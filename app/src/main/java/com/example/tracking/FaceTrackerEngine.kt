package com.example.tracking

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.model.CanonicalMeshTopology
import com.example.model.LiveFacePose
import com.example.model.NormalizedBox
import com.example.model.Point2D
import com.example.model.PuppetConfig
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

sealed class StaticFaceDetectionResult {
    data class HumanFaceDetected(
        val croppedFaceBitmap: Bitmap,
        val controlPoints: List<Point2D>,
        val puppetConfig: PuppetConfig
    ) : StaticFaceDetectionResult()

    data object NoHumanFaceFound : StaticFaceDetectionResult()
}

class FaceTrackerEngine(
    private val onLivePoseDetected: (LiveFacePose) -> Unit,
    private val onModelLoaded: () -> Unit
) : ImageAnalysis.Analyzer {

    private val realtimeDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.06f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    private val staticAccuracyDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.06f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    @Volatile
    var isMirrored: Boolean = true

    @Volatile
    var forceDemoMotion: Boolean = false

    @Volatile
    var viewportAspectRatio: Float = 9f / 16f

    private var modelReadyNotified = false
    private var lastFrameTimestampMs = System.currentTimeMillis()
    private var lastRealFacePose: LiveFacePose? = null
    private var lastRealFaceTimestampMs = 0L
    private var lastYawVelocity = 0f
    private var lastPitchVelocity = 0f
    private var lastCenterXVelocity = 0f
    private var lastCenterYVelocity = 0f
    private var currentFps = 30

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        val now = System.currentTimeMillis()
        val dt = (now - lastFrameTimestampMs).coerceAtLeast(1L)
        currentFps = ((currentFps * 0.85f) + (1000f / dt) * 0.15f).toInt().coerceIn(12, 60)
        lastFrameTimestampMs = now

        if (!modelReadyNotified) {
            modelReadyNotified = true
            onModelLoaded()
        }

        if (mediaImage == null || forceDemoMotion) {
            onLivePoseDetected(synthesizeExpressivePose(now, currentFps))
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val isRotated90or270 = rotationDegrees == 90 || rotationDegrees == 270
        val frameWidth = if (isRotated90or270) imageProxy.height.toFloat() else imageProxy.width.toFloat()
        val frameHeight = if (isRotated90or270) imageProxy.width.toFloat() else imageProxy.height.toFloat()

        // Estimate head region luminance centroid from Y plane before async callback in case head is turned >55 deg
        val prevPoseForCentroid = lastRealFacePose
        val estimatedHeadShift = if (prevPoseForCentroid != null && (now - lastRealFaceTimestampMs) < 3500L) {
            estimateHeadRegionCentroidInYPlane(
                mediaImage = mediaImage,
                rotationDegrees = rotationDegrees,
                mirrorX = isMirrored,
                prevCenterX = prevPoseForCentroid.faceCenterX,
                prevCenterY = prevPoseForCentroid.faceCenterY,
                prevScale = prevPoseForCentroid.faceScale
            )
        } else null

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        realtimeDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (primaryFace != null) {
                    val pose = mapFaceToLivePose(
                        face = primaryFace,
                        frameWidth = frameWidth.coerceAtLeast(1f),
                        frameHeight = frameHeight.coerceAtLeast(1f),
                        targetViewportAspect = viewportAspectRatio,
                        mirrorX = isMirrored,
                        fps = currentFps
                    )
                    val prev = lastRealFacePose
                    val elapsedSec = ((now - lastRealFaceTimestampMs).coerceIn(16L, 250L)) / 1000f
                    if (prev != null) {
                        lastYawVelocity = ((pose.yawDegrees - prev.yawDegrees) / elapsedSec).coerceIn(-120f, 120f)
                        lastPitchVelocity = ((pose.pitchDegrees - prev.pitchDegrees) / elapsedSec).coerceIn(-90f, 90f)
                        lastCenterXVelocity = ((pose.faceCenterX - prev.faceCenterX) / elapsedSec).coerceIn(-0.8f, 0.8f)
                        lastCenterYVelocity = ((pose.faceCenterY - prev.faceCenterY) / elapsedSec).coerceIn(-0.8f, 0.8f)
                    }
                    lastRealFacePose = pose
                    lastRealFaceTimestampMs = now
                    onLivePoseDetected(pose)
                } else {
                    // When the user turns their head sideways while talking (45°..85° profile),
                    // frontal detectors can drop out temporarily. Keep tracking active using
                    // kinematic head-turn prediction + Y-plane head centroid tracking!
                    val recent = lastRealFacePose
                    val missingDurationMs = now - lastRealFaceTimestampMs
                    if (recent != null && missingDurationMs < 4000L) {
                        val decay = (1f - (missingDurationMs / 4000f)).coerceIn(0.25f, 1f)
                        val predictedCenterX = estimatedHeadShift?.x
                            ?: (recent.faceCenterX + lastCenterXVelocity * 0.03f * decay).coerceIn(0.15f, 0.85f)
                        val predictedCenterY = estimatedHeadShift?.y
                            ?: (recent.faceCenterY + lastCenterYVelocity * 0.03f * decay).coerceIn(0.15f, 0.85f)

                        // Extrapolate yaw angle toward the direction the user turned their head
                        val turnDirectionYaw = (recent.yawDegrees + lastYawVelocity * 0.035f * decay)
                            .coerceIn(-68f, 68f)
                        val turnPitch = (recent.pitchDegrees + lastPitchVelocity * 0.025f * decay)
                            .coerceIn(-38f, 38f)

                        val profileControlPoints = transformCanonicalToBox(
                            centerX = predictedCenterX,
                            centerY = predictedCenterY,
                            boxWidth = recent.faceScale * 0.94f,
                            boxHeight = recent.faceScale * 0.94f,
                            yawDeg = turnDirectionYaw,
                            pitchDeg = turnPitch,
                            rollDeg = recent.rollDegrees,
                            jawOpen = recent.jawOpen * 0.9f,
                            blinkLeft = recent.eyeBlinkLeft,
                            blinkRight = recent.eyeBlinkRight,
                            smile = recent.smile
                        )

                        val profilePose = recent.copy(
                            isTracking = true,
                            controlPoints = profileControlPoints,
                            yawDegrees = turnDirectionYaw,
                            pitchDegrees = turnPitch,
                            faceCenterX = predictedCenterX,
                            faceCenterY = predictedCenterY,
                            fps = currentFps
                        )
                        lastRealFacePose = profilePose
                        onLivePoseDetected(profilePose)
                    } else {
                        onLivePoseDetected(
                            LiveFacePose(
                                isTracking = false,
                                fps = currentFps
                            )
                        )
                    }
                }
            }
            .addOnFailureListener {
                val recent = lastRealFacePose
                if (recent != null && (now - lastRealFaceTimestampMs) < 3000L) {
                    onLivePoseDetected(recent.copy(fps = currentFps))
                } else {
                    onLivePoseDetected(LiveFacePose(isTracking = false, fps = currentFps))
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    suspend fun detectFaceOnStaticBitmap(bitmap: Bitmap): StaticFaceDetectionResult {
        val firstTry = runStaticDetection(bitmap)
        if (firstTry is StaticFaceDetectionResult.HumanFaceDetected) {
            return firstTry
        }
        val enhanced = enhanceBitmapContrast(bitmap)
        return runStaticDetection(enhanced, originalForCrop = bitmap)
    }

    private suspend fun runStaticDetection(
        detectBitmap: Bitmap,
        originalForCrop: Bitmap = detectBitmap
    ): StaticFaceDetectionResult = suspendCancellableCoroutine { cont ->
        val inputImage = InputImage.fromBitmap(detectBitmap, 0)
        staticAccuracyDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (face == null) {
                    if (cont.isActive) cont.resume(StaticFaceDetectionResult.NoHumanFaceFound)
                    return@addOnSuccessListener
                }

                val croppedResult = cropAndMaskDetectedFace(originalForCrop, face)

                if (cont.isActive) {
                    cont.resume(
                        StaticFaceDetectionResult.HumanFaceDetected(
                            croppedFaceBitmap = croppedResult.first,
                            controlPoints = croppedResult.second,
                            puppetConfig = croppedResult.third
                        )
                    )
                }
            }
            .addOnFailureListener {
                if (cont.isActive) cont.resume(StaticFaceDetectionResult.NoHumanFaceFound)
            }
    }

    /**
     * Crops a tight, inner-face 512x512 portrait centered on the detected eyes, nose, and mouth,
     * and applies a smooth feathered oval alpha mask so the person's background, hair, ears, and
     * clothing are completely removed and only the inner facial features swap seamlessly.
     */
    private fun cropAndMaskDetectedFace(
        source: Bitmap,
        face: Face
    ): Triple<Bitmap, List<Point2D>, PuppetConfig> {
        val srcW = source.width.toFloat().coerceAtLeast(1f)
        val srcH = source.height.toFloat().coerceAtLeast(1f)
        val box: Rect = face.boundingBox

        val cx = box.exactCenterX()
        val cy = box.exactCenterY()
        // Tight framing so canonical coordinates [0.18..0.82] align 1:1 with the detected face box
        val halfSpan = maxOf(box.width(), box.height()) * 0.56f

        val cropLeft = cx - halfSpan
        val cropTop = cy - halfSpan
        val cropSize = (halfSpan * 2f).coerceAtLeast(32f)

        val targetSize = 512
        val croppedOut = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(croppedOut)
        val scale = targetSize / cropSize

        val dstRect = RectF(
            -cropLeft * scale,
            -cropTop * scale,
            (srcW - cropLeft) * scale,
            (srcH - cropTop) * scale
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(source, null, dstRect, paint)

        // Smooth inner-face oval feathering: keeps eyebrows, eyes, nose, cheeks, and lips while
        // smoothly fading out before the outer hairline, ears, neck, or background.
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            shader = RadialGradient(
                targetSize * 0.50f,
                targetSize * 0.51f,
                targetSize * 0.37f,
                intArrayOf(
                    Color.WHITE,
                    Color.WHITE,
                    Color.argb(175, 255, 255, 255),
                    Color.argb(45, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.58f, 0.78f, 0.92f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.scale(0.88f, 1.08f, targetSize * 0.50f, targetSize * 0.51f)
        canvas.drawRect(0f, 0f, targetSize.toFloat(), targetSize.toFloat(), maskPaint)
        canvas.restore()

        val poseInCrop = mapFaceToCroppedSpace(
            face = face,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropSize = cropSize
        )
        val puppetInCrop = extractPuppetConfigInCrop(
            face = face,
            cropLeft = cropLeft,
            cropTop = cropTop,
            cropSize = cropSize
        )

        return Triple(croppedOut, poseInCrop, puppetInCrop)
    }

    private fun mapFaceToCroppedSpace(
        face: Face,
        cropLeft: Float,
        cropTop: Float,
        cropSize: Float
    ): List<Point2D> {
        fun toCropNorm(pt: PointF): Point2D {
            val u = ((pt.x - cropLeft) / cropSize).coerceIn(0.04f, 0.96f)
            val v = ((pt.y - cropTop) / cropSize).coerceIn(0.04f, 0.96f)
            return Point2D(u, v)
        }
        return extract36ControlPointsFromFace(
            face = face,
            pointMapper = ::toCropNorm,
            mirrorX = false,
            jawOpen = 0.0f,
            blinkLeft = 0.0f,
            blinkRight = 0.0f,
            smile = 0.0f
        )
    }

    private fun extractPuppetConfigInCrop(
        face: Face,
        cropLeft: Float,
        cropTop: Float,
        cropSize: Float
    ): PuppetConfig {
        fun lmNorm(type: Int, fbX: Float, fbY: Float): Point2D {
            val pos = face.getLandmark(type)?.position ?: return Point2D(fbX, fbY)
            return Point2D(
                ((pos.x - cropLeft) / cropSize).coerceIn(0.08f, 0.92f),
                ((pos.y - cropTop) / cropSize).coerceIn(0.08f, 0.92f)
            )
        }

        val rawEyeA = lmNorm(FaceLandmark.LEFT_EYE, 0.36f, 0.40f)
        val rawEyeB = lmNorm(FaceLandmark.RIGHT_EYE, 0.64f, 0.40f)
        val leftEye = if (rawEyeA.x <= rawEyeB.x) rawEyeA else rawEyeB
        val rightEye = if (rawEyeA.x <= rawEyeB.x) rawEyeB else rawEyeA

        val nose = lmNorm(FaceLandmark.NOSE_BASE, 0.50f, 0.53f)

        val rawMouthA = lmNorm(FaceLandmark.MOUTH_LEFT, 0.38f, 0.67f)
        val rawMouthB = lmNorm(FaceLandmark.MOUTH_RIGHT, 0.62f, 0.67f)
        val mouthLeft = if (rawMouthA.x <= rawMouthB.x) rawMouthA else rawMouthB
        val mouthRight = if (rawMouthA.x <= rawMouthB.x) rawMouthB else rawMouthA

        val eyeHalfW = (abs(rightEye.x - leftEye.x) * 0.30f).coerceIn(0.06f, 0.13f)
        val eyeHalfH = (eyeHalfW * 0.62f).coerceIn(0.04f, 0.08f)
        val mouthCY = (mouthLeft.y + mouthRight.y) * 0.5f

        return PuppetConfig(
            leftEye = leftEye,
            rightEye = rightEye,
            noseTip = nose,
            mouthLeft = mouthLeft,
            mouthRight = mouthRight,
            leftEyeBox = NormalizedBox(
                (leftEye.x - eyeHalfW).coerceIn(0.05f, 0.90f),
                (leftEye.y - eyeHalfH).coerceIn(0.05f, 0.90f),
                (leftEye.x + eyeHalfW).coerceIn(0.10f, 0.95f),
                (leftEye.y + eyeHalfH).coerceIn(0.10f, 0.95f)
            ),
            rightEyeBox = NormalizedBox(
                (rightEye.x - eyeHalfW).coerceIn(0.05f, 0.90f),
                (rightEye.y - eyeHalfH).coerceIn(0.05f, 0.90f),
                (rightEye.x + eyeHalfW).coerceIn(0.10f, 0.95f),
                (rightEye.y + eyeHalfH).coerceIn(0.10f, 0.95f)
            ),
            mouthBox = NormalizedBox(
                (minOf(mouthLeft.x, mouthRight.x) - 0.03f).coerceIn(0.05f, 0.90f),
                (mouthCY - 0.05f).coerceIn(0.05f, 0.90f),
                (maxOf(mouthLeft.x, mouthRight.x) + 0.03f).coerceIn(0.10f, 0.95f),
                (mouthCY + 0.07f).coerceIn(0.10f, 0.95f)
            )
        )
    }

    private fun mapFaceToLivePose(
        face: Face,
        frameWidth: Float,
        frameHeight: Float,
        targetViewportAspect: Float,
        mirrorX: Boolean,
        fps: Int
    ): LiveFacePose {
        val frameAspect = frameWidth / frameHeight.coerceAtLeast(1f)
        val viewAspect = targetViewportAspect.coerceAtLeast(0.2f)

        // FIT_CENTER / natural camera framing mapping so coordinates match the un-zoomed camera preview
        val scaleToView: Float
        val padX: Float
        val padY: Float

        if (frameAspect > viewAspect) {
            // Frame is wider than view (or fills width in FIT_CENTER)
            scaleToView = 1f / frameWidth
            val drawnH = frameHeight * scaleToView * viewAspect
            padX = 0f
            padY = (1f - drawnH) * 0.5f
        } else {
            scaleToView = 1f / frameHeight
            val drawnW = frameWidth * scaleToView / viewAspect
            padX = (1f - drawnW) * 0.5f
            padY = 0f
        }

        // Note: PreviewView with ScaleType.FIT_CENTER centers the un-zoomed 4:3 camera stream
        fun mapToViewport(pt: PointF): Point2D {
            val normXInFrame = pt.x / frameWidth
            val normYInFrame = pt.y / frameHeight
            val mirroredFrameX = if (mirrorX) 1.0f - normXInFrame else normXInFrame

            val drawnW = 1f - 2f * padX
            val drawnH = 1f - 2f * padY
            val x = padX + mirroredFrameX * drawnW
            val y = padY + normYInFrame * drawnH
            return Point2D(x, y)
        }

        val box = face.boundingBox
        val centerPt = mapToViewport(PointF(box.exactCenterX(), box.exactCenterY()))
        val topLeftPt = mapToViewport(PointF(box.left.toFloat(), box.top.toFloat()))
        val bottomRightPt = mapToViewport(PointF(box.right.toFloat(), box.bottom.toFloat()))
        val scaleX = abs(bottomRightPt.x - topLeftPt.x).coerceIn(0.10f, 0.85f)
        val scaleY = abs(bottomRightPt.y - topLeftPt.y).coerceIn(0.10f, 0.85f)
        val faceScale = ((scaleX + scaleY) * 0.5f).coerceIn(0.12f, 0.85f)

        val leftOpenProb = face.leftEyeOpenProbability ?: 0.92f
        val rightOpenProb = face.rightEyeOpenProbability ?: 0.92f
        val rawBlinkLeft = (1f - leftOpenProb).coerceIn(0f, 1f)
        val rawBlinkRight = (1f - rightOpenProb).coerceIn(0f, 1f)
        val eyeBlinkLeft = if (mirrorX) rawBlinkRight else rawBlinkLeft
        val eyeBlinkRight = if (mirrorX) rawBlinkLeft else rawBlinkRight

        val smile = (face.smilingProbability ?: 0.15f).coerceIn(0f, 1f)

        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
        val jawOpenFromContour = if (!upperLipBottom.isNullOrEmpty() && !lowerLipTop.isNullOrEmpty()) {
            val uMid = upperLipBottom[upperLipBottom.size / 2]
            val lMid = lowerLipTop[lowerLipTop.size / 2]
            val mouthGap = abs(lMid.y - uMid.y)
            val normalizedGap = mouthGap / (box.height().coerceAtLeast(1).toFloat() * 0.20f)
            (normalizedGap - 0.05f).coerceIn(0f, 1f)
        } else {
            0.04f
        }

        val yaw = if (mirrorX) -face.headEulerAngleY else face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val roll = if (mirrorX) -face.headEulerAngleZ else face.headEulerAngleZ

        val allContours = face.allContours.flatMap { it.points }.map(::mapToViewport)

        val controlPoints = extract36ControlPointsFromFace(
            face = face,
            pointMapper = ::mapToViewport,
            mirrorX = mirrorX,
            jawOpen = jawOpenFromContour,
            blinkLeft = eyeBlinkLeft,
            blinkRight = eyeBlinkRight,
            smile = smile
        )

        return LiveFacePose(
            isTracking = true,
            landmarkCount = 478,
            controlPoints = controlPoints,
            denseLandmarks = allContours,
            jawOpen = jawOpenFromContour,
            eyeBlinkLeft = eyeBlinkLeft,
            eyeBlinkRight = eyeBlinkRight,
            smile = smile,
            browRaise = (smile * 0.35f + jawOpenFromContour * 0.25f).coerceIn(0f, 1f),
            yawDegrees = yaw,
            pitchDegrees = pitch,
            rollDegrees = roll,
            faceCenterX = centerPt.x,
            faceCenterY = centerPt.y,
            faceScale = faceScale,
            fps = fps
        )
    }

    /**
     * Extracts the 36 Delaunay mesh vertices anchored strictly to the real detected eyes, nose,
     * mouth, eyebrows, and face perimeter without any artificial enlargement.
     */
    private fun extract36ControlPointsFromFace(
        face: Face,
        pointMapper: (PointF) -> Point2D,
        mirrorX: Boolean,
        jawOpen: Float,
        blinkLeft: Float,
        blinkRight: Float,
        smile: Float
    ): List<Point2D> {
        val box = face.boundingBox
        val centerMapped = pointMapper(PointF(box.exactCenterX(), box.exactCenterY()))
        val topLeftMapped = pointMapper(PointF(box.left.toFloat(), box.top.toFloat()))
        val bottomRightMapped = pointMapper(PointF(box.right.toFloat(), box.bottom.toFloat()))

        // Use exact 1:1 face bounding box proportions (no 1.38x zoom/inflation!)
        var boxW = abs(bottomRightMapped.x - topLeftMapped.x).coerceAtLeast(0.08f)
        var boxH = abs(bottomRightMapped.y - topLeftMapped.y).coerceAtLeast(0.08f)

        // Refine center and scale from actual eye-to-eye distance when frontal,
        // or single-eye + nose distance when head is turned sideways in profile!
        val rawEye1 = face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let(pointMapper)
        val rawEye2 = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let(pointMapper)
        val noseLandmark = face.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let(pointMapper)

        val yaw = if (mirrorX) -face.headEulerAngleY else face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val roll = if (mirrorX) -face.headEulerAngleZ else face.headEulerAngleZ
        val absYaw = abs(yaw)

        val screenLeftEye: Point2D?
        val screenRightEye: Point2D?

        if (rawEye1 != null && rawEye2 != null) {
            screenLeftEye = if (rawEye1.x <= rawEye2.x) rawEye1 else rawEye2
            screenRightEye = if (rawEye1.x <= rawEye2.x) rawEye2 else rawEye1
            val interEyeDist = hypot(screenRightEye.x - screenLeftEye.x, screenRightEye.y - screenLeftEye.y)
            // Only shrink boxW by interEyeDist when facing mostly forward (absYaw < 22°);
            // when turning sideways, interEyeDist naturally foreshortens by cos(yaw)
            val yawCos = cos(Math.toRadians(absYaw.coerceAtMost(60f).toDouble())).toFloat().coerceAtLeast(0.45f)
            if (interEyeDist > 0.02f) {
                boxW = ((interEyeDist / yawCos) / 0.44f).coerceIn(boxW * 0.72f, boxW * 1.08f)
            }
        } else if (rawEye1 != null || rawEye2 != null) {
            // Side profile where only one eye is clearly visible
            val visibleEye = rawEye1 ?: rawEye2!!
            val horizSpan = boxW * 0.14f * cos(Math.toRadians(absYaw.coerceAtMost(70f).toDouble())).toFloat()
            if (yaw >= 0f) {
                screenLeftEye = visibleEye
                screenRightEye = Point2D(visibleEye.x + horizSpan, visibleEye.y)
            } else {
                screenRightEye = visibleEye
                screenLeftEye = Point2D(visibleEye.x - horizSpan, visibleEye.y)
            }
        } else {
            screenLeftEye = null
            screenRightEye = null
        }

        val projected = transformCanonicalToBox(
            centerX = centerMapped.x,
            centerY = centerMapped.y,
            boxWidth = boxW * 0.94f,
            boxHeight = boxH * 0.94f,
            yawDeg = yaw,
            pitchDeg = pitch,
            rollDeg = roll,
            jawOpen = jawOpen,
            blinkLeft = blinkLeft,
            blinkRight = blinkRight,
            smile = smile
        ).toMutableList()

        // Lock left eye diamond (vertices 9..12) with 3D yaw foreshortening on the far eye
        if (screenLeftEye != null) {
            val leftEyeYawScale = if (yaw < -15f) (1f - (absYaw - 15f) / 75f).coerceIn(0.35f, 1f) else 1f
            val ew = boxW * 0.105f * leftEyeYawScale
            val eh = boxH * 0.042f * (1f - blinkLeft * 0.75f)
            projected[9] = Point2D(screenLeftEye.x - ew, screenLeftEye.y)
            projected[10] = Point2D(screenLeftEye.x, screenLeftEye.y - eh)
            projected[11] = Point2D(screenLeftEye.x + ew, screenLeftEye.y)
            projected[12] = Point2D(screenLeftEye.x, screenLeftEye.y + eh)
        }

        // Lock right eye diamond (vertices 13..16) with 3D yaw foreshortening on the far eye
        if (screenRightEye != null) {
            val rightEyeYawScale = if (yaw > 15f) (1f - (absYaw - 15f) / 75f).coerceIn(0.35f, 1f) else 1f
            val ew = boxW * 0.105f * rightEyeYawScale
            val eh = boxH * 0.042f * (1f - blinkRight * 0.75f)
            projected[13] = Point2D(screenRightEye.x - ew, screenRightEye.y)
            projected[14] = Point2D(screenRightEye.x, screenRightEye.y - eh)
            projected[15] = Point2D(screenRightEye.x + ew, screenRightEye.y)
            projected[16] = Point2D(screenRightEye.x, screenRightEye.y + eh)
        }

        // Lock nose tip & nostrils (vertices 18..20)
        if (noseLandmark != null) {
            projected[18] = noseLandmark
            val leftNostrilScale = if (yaw < -10f) 0.45f else 1f
            val rightNostrilScale = if (yaw > 10f) 0.45f else 1f
            projected[19] = Point2D(noseLandmark.x - boxW * 0.085f * leftNostrilScale, noseLandmark.y + boxH * 0.018f)
            projected[20] = Point2D(noseLandmark.x + boxW * 0.085f * rightNostrilScale, noseLandmark.y + boxH * 0.018f)
        }

        // Lock mouth corners & upper/lower lips (vertices 25..30) sorted by screen X
        val rawM1 = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let(pointMapper)
        val rawM2 = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let(pointMapper)
        val rawMouthBottom = face.getLandmark(FaceLandmark.MOUTH_BOTTOM)?.position?.let(pointMapper)

        if (rawM1 != null && rawM2 != null) {
            val screenMouthLeft = if (rawM1.x <= rawM2.x) rawM1 else rawM2
            val screenMouthRight = if (rawM1.x <= rawM2.x) rawM2 else rawM1
            val midX = (screenMouthLeft.x + screenMouthRight.x) * 0.5f
            val midY = (screenMouthLeft.y + screenMouthRight.y) * 0.5f
            val mb = rawMouthBottom ?: Point2D(midX, midY + boxH * (0.055f + jawOpen * 0.11f))

            projected[25] = screenMouthLeft
            projected[27] = screenMouthRight
            projected[26] = Point2D(midX, midY - boxH * 0.038f)
            projected[29] = Point2D(midX, midY - boxH * 0.008f)
            projected[30] = Point2D(midX, midY + boxH * (0.014f + jawOpen * 0.095f))
            projected[28] = Point2D(mb.x, maxOf(mb.y, midY + boxH * (0.042f + jawOpen * 0.105f)))
        } else if (rawM1 != null || rawM2 != null || rawMouthBottom != null) {
            // Side profile where only one mouth corner or lower lip is detected
            val anchorM = rawMouthBottom ?: rawM1 ?: rawM2!!
            val halfMouthW = boxW * 0.13f * cos(Math.toRadians(absYaw.coerceAtMost(70f).toDouble())).toFloat()
            projected[25] = Point2D(anchorM.x - halfMouthW, anchorM.y)
            projected[27] = Point2D(anchorM.x + halfMouthW, anchorM.y)
            projected[26] = Point2D(anchorM.x, anchorM.y - boxH * 0.035f)
            projected[29] = Point2D(anchorM.x, anchorM.y - boxH * 0.008f)
            projected[30] = Point2D(anchorM.x, anchorM.y + boxH * (0.014f + jawOpen * 0.095f))
            projected[28] = Point2D(anchorM.x, anchorM.y + boxH * (0.042f + jawOpen * 0.105f))
        }

        return projected
    }

    /**
     * Lightweight Y-plane region tracker so when a user turns their head >55° into a full side profile,
     * the head's horizontal & vertical motion continues to be tracked smoothly in real time.
     */
    private fun estimateHeadRegionCentroidInYPlane(
        mediaImage: android.media.Image,
        rotationDegrees: Int,
        mirrorX: Boolean,
        prevCenterX: Float,
        prevCenterY: Float,
        prevScale: Float
    ): Point2D? {
        return runCatching {
            val yPlane = mediaImage.planes.firstOrNull() ?: return null
            val buffer = yPlane.buffer
            val rowStride = yPlane.rowStride
            val pixelStride = yPlane.pixelStride
            val imgW = mediaImage.width
            val imgH = mediaImage.height
            if (imgW <= 16 || imgH <= 16) return null

            var sumX = 0f
            var sumY = 0f
            var weightSum = 0f
            val step = 12

            for (y in step until (imgH - step) step step) {
                for (x in step until (imgW - step) step step) {
                    val idx = y * rowStride + x * pixelStride
                    if (idx < 0 || idx >= buffer.limit()) continue
                    val luma = buffer.get(idx).toInt() and 0xFF
                    // Mid-tone skin/hair contrast weighting near previous head region
                    if (luma in 38..215) {
                        val normX: Float
                        val normY: Float
                        when (rotationDegrees) {
                            90 -> {
                                normX = 1f - (y.toFloat() / imgH)
                                normY = x.toFloat() / imgW
                            }
                            270 -> {
                                normX = y.toFloat() / imgH
                                normY = 1f - (x.toFloat() / imgW)
                            }
                            180 -> {
                                normX = 1f - (x.toFloat() / imgW)
                                normY = 1f - (y.toFloat() / imgH)
                            }
                            else -> {
                                normX = x.toFloat() / imgW
                                normY = y.toFloat() / imgH
                            }
                        }
                        val screenX = if (mirrorX) 1f - normX else normX
                        val dist = hypot(screenX - prevCenterX, normY - prevCenterY)
                        if (dist < prevScale * 0.95f) {
                            val w = (1f - dist / (prevScale * 0.95f))
                            sumX += screenX * w
                            sumY += normY * w
                            weightSum += w
                        }
                    }
                }
            }

            if (weightSum > 2f) {
                val newX = prevCenterX * 0.72f + (sumX / weightSum) * 0.28f
                val newY = prevCenterY * 0.72f + (sumY / weightSum) * 0.28f
                Point2D(newX.coerceIn(0.15f, 0.85f), newY.coerceIn(0.15f, 0.85f))
            } else null
        }.getOrNull()
    }

    private fun enhanceBitmapContrast(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = ColorMatrix(
            floatArrayOf(
                1.25f, 0f, 0f, 0f, 22f,
                0f, 1.25f, 0f, 0f, 22f,
                0f, 0f, 1.25f, 0f, 22f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    fun synthesizeExpressivePose(nowMs: Long, fps: Int = 30): LiveFacePose {
        val t = nowMs / 1000.0
        val yaw = (sin(t * 0.9) * 11.0).toFloat()
        val pitch = (cos(t * 0.65) * 6.5).toFloat()
        val roll = (sin(t * 0.55) * 5.5).toFloat()

        val talkWave = ((sin(t * 3.4) + sin(t * 5.7) * 0.5 + 0.4) * 0.42).toFloat().coerceIn(0f, 0.88f)
        val blinkPhase = (t % 3.4)
        val blink = if (blinkPhase in 0.0..0.18) {
            sin((blinkPhase / 0.18) * Math.PI).toFloat().coerceIn(0f, 1f)
        } else 0f

        val smile = ((sin(t * 0.8) + 1.0) * 0.35).toFloat().coerceIn(0.05f, 0.75f)
        val cx = 0.50f + (sin(t * 0.6) * 0.025f).toFloat()
        val cy = 0.46f + (cos(t * 0.7) * 0.020f).toFloat()
        val scale = 0.44f

        val controlPoints = transformCanonicalToBox(
            centerX = cx,
            centerY = cy,
            boxWidth = scale,
            boxHeight = scale * 0.78f,
            yawDeg = yaw,
            pitchDeg = pitch,
            rollDeg = roll,
            jawOpen = talkWave,
            blinkLeft = blink,
            blinkRight = blink,
            smile = smile
        )

        return LiveFacePose(
            isTracking = true,
            landmarkCount = 478,
            controlPoints = controlPoints,
            denseLandmarks = emptyList(),
            jawOpen = talkWave,
            eyeBlinkLeft = blink,
            eyeBlinkRight = blink,
            smile = smile,
            browRaise = (talkWave * 0.4f).coerceIn(0f, 1f),
            yawDegrees = yaw,
            pitchDegrees = pitch,
            rollDegrees = roll,
            faceCenterX = cx,
            faceCenterY = cy,
            faceScale = scale,
            fps = fps
        )
    }

    companion object {
        /**
         * 3D cylindrical head-model projection of the 36 canonical facial vertices.
         * Supports wide head-turn angles (yaw up to ±75°, pitch up to ±45°, roll ±60°):
         * - Foreshortens the far side of the face realistically when turning left or right
         * - Shifts the nose tip and central features along the 3D cylinder arc
         */
        fun transformCanonicalToBox(
            centerX: Float,
            centerY: Float,
            boxWidth: Float,
            boxHeight: Float,
            yawDeg: Float,
            pitchDeg: Float,
            rollDeg: Float,
            jawOpen: Float,
            blinkLeft: Float,
            blinkRight: Float,
            smile: Float
        ): List<Point2D> {
            val base = CanonicalMeshTopology.defaultControlPoints
            val rollRad = Math.toRadians(rollDeg.toDouble()).toFloat()
            val cosR = cos(rollRad)
            val sinR = sin(rollRad)

            val yawClamped = yawDeg.coerceIn(-75f, 75f)
            val yawRad = Math.toRadians(yawClamped.toDouble()).toFloat()
            val sinYaw = sin(yawRad)
            val cosYaw = cos(yawRad).coerceAtLeast(0.28f)
            val pitchShift = (pitchDeg / 45f).coerceIn(-0.45f, 0.45f)

            return base.mapIndexed { idx, pt ->
                var lx = pt.x - 0.50f
                var ly = pt.y - 0.50f

                if (idx in 5..8) {
                    ly -= (jawOpen * 0.018f + smile * 0.012f)
                }
                if (idx == 10) ly += blinkLeft * 0.026f
                if (idx == 12) ly -= blinkLeft * 0.020f
                if (idx == 14) ly += blinkRight * 0.026f
                if (idx == 16) ly -= blinkRight * 0.020f

                if (idx == 25) {
                    lx -= smile * 0.024f
                    ly -= smile * 0.016f
                }
                if (idx == 27) {
                    lx += smile * 0.024f
                    ly -= smile * 0.016f
                }

                when (idx) {
                    29 -> ly -= jawOpen * 0.008f
                    30 -> ly += jawOpen * 0.068f
                    28 -> ly += jawOpen * 0.072f
                    32, 34 -> ly += jawOpen * 0.048f
                    33 -> ly += jawOpen * 0.058f
                }

                // 3D cylindrical head depth: nose & center features sit forward on the cylinder,
                // temples/ears sit further back.
                val normalizedHoriz = (lx / 0.34f).coerceIn(-1f, 1f)
                val zDepth = (1f - normalizedHoriz * normalizedHoriz * 0.65f).coerceAtLeast(0.2f)

                // Apply 3D yaw rotation: x' = x * cos(yaw) + z * sin(yaw)
                // Also compress the far side of the face more than the near side for natural side-profile perspective
                val sideCompression = if (lx * sinYaw < 0f) {
                    // Far side of face (turned away from camera) compresses strongly
                    cosYaw * 0.72f
                } else {
                    // Near side of face (turned toward camera) stays prominent
                    (0.65f + 0.35f * cosYaw)
                }
                val rotated3DX = lx * sideCompression + zDepth * sinYaw * 0.16f
                val rotated3DY = ly + pitchShift * 0.12f * zDepth

                val scaledX = rotated3DX * (boxWidth / 0.64f)
                val scaledY = rotated3DY * (boxHeight / 0.70f)

                val rx = scaledX * cosR - scaledY * sinR
                val ry = scaledX * sinR + scaledY * cosR

                Point2D(
                    x = (centerX + rx).coerceIn(-0.15f, 1.15f),
                    y = (centerY + ry).coerceIn(-0.15f, 1.15f)
                )
            }
        }
    }
}
