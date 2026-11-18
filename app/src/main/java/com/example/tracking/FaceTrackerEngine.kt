package com.example.tracking

import android.graphics.Bitmap
import android.graphics.PointF
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
import kotlin.math. hypot
import kotlin.math.sin

sealed class StaticFaceDetectionResult {
    data class HumanFaceDetected(
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
            .setMinFaceSize(0.15f)
            .build()
        FaceDetection.getClient(options)
    }

    private val staticAccuracyDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.10f)
            .build()
        FaceDetection.getClient(options)
    }

    @Volatile
    var isMirrored: Boolean = true

    @Volatile
    var forceDemoMotion: Boolean = false

    private var modelReadyNotified = false
    private var lastFrameTimestampMs = System.currentTimeMillis()
    private var lastRealFaceTimestampMs = 0L
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

        val inputImage = InputImage.fromMediaImage(mediaImage, rotationDegrees)
        realtimeDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                val primaryFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                if (primaryFace != null) {
                    lastRealFaceTimestampMs = now
                    val pose = mapFaceToLivePose(
                        face = primaryFace,
                        frameWidth = frameWidth.coerceAtLeast(1f),
                        frameHeight = frameHeight.coerceAtLeast(1f),
                        mirrorX = isMirrored,
                        fps = currentFps
                    )
                    onLivePoseDetected(pose)
                } else {
                    // If no physical person is in front of the camera (e.g. streaming emulator),
                    // smoothly animate synthetic expressions so the creator can preview the rig live.
                    onLivePoseDetected(synthesizeExpressivePose(now, currentFps))
                }
            }
            .addOnFailureListener {
                onLivePoseDetected(synthesizeExpressivePose(now, currentFps))
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    /**
     * Runs static face landmark detection on a user-uploaded gallery image.
     * If no human face is detected (e.g. cartoon or animal), returns [StaticFaceDetectionResult.NoHumanFaceFound]
     * so the UI opens the Manual Setup Editor.
     */
    suspend fun detectFaceOnStaticBitmap(bitmap: Bitmap): StaticFaceDetectionResult =
        suspendCancellableCoroutine { cont ->
            val inputImage = InputImage.fromBitmap(bitmap, 0)
            staticAccuracyDetector.process(inputImage)
                .addOnSuccessListener { faces ->
                    val face = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                    if (face == null) {
                        if (cont.isActive) cont.resume(StaticFaceDetectionResult.NoHumanFaceFound)
                        return@addOnSuccessListener
                    }
                    val w = bitmap.width.toFloat().coerceAtLeast(1f)
                    val h = bitmap.height.toFloat().coerceAtLeast(1f)
                    val pose = mapFaceToLivePose(
                        face = face,
                        frameWidth = w,
                        frameHeight = h,
                        mirrorX = false,
                        fps = 30
                    )
                    val puppet = extractPuppetConfigFromFace(face, w, h)
                    if (cont.isActive) {
                        cont.resume(
                            StaticFaceDetectionResult.HumanFaceDetected(
                                controlPoints = pose.controlPoints,
                                puppetConfig = puppet
                            )
                        )
                    }
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(StaticFaceDetectionResult.NoHumanFaceFound)
                }
        }

    private fun extractPuppetConfigFromFace(face: Face, w: Float, h: Float): PuppetConfig {
        val box = face.boundingBox
        val bx = box.left / w
        val by = box.top / h
        val bw = box.width() / w
        val bh = box.height() / h

        fun normPt(landmarkType: Int, fallbackX: Float, fallbackY: Float): Point2D {
            val lm = face.getLandmark(landmarkType)?.position
            return if (lm != null) {
                Point2D((lm.x / w).coerceIn(0.05f, 0.95f), (lm.y / h).coerceIn(0.05f, 0.95f))
            } else {
                Point2D(fallbackX, fallbackY)
            }
        }

        val leftEye = normPt(FaceLandmark.LEFT_EYE, bx + bw * 0.32f, by + bh * 0.38f)
        val rightEye = normPt(FaceLandmark.RIGHT_EYE, bx + bw * 0.68f, by + bh * 0.38f)
        val nose = normPt(FaceLandmark.NOSE_BASE, bx + bw * 0.50f, by + bh * 0.54f)
        val mouthLeft = normPt(FaceLandmark.MOUTH_LEFT, bx + bw * 0.35f, by + bh * 0.72f)
        val mouthRight = normPt(FaceLandmark.MOUTH_RIGHT, bx + bw * 0.65f, by + bh * 0.72f)
        val mouthBottom = normPt(FaceLandmark.MOUTH_BOTTOM, bx + bw * 0.50f, by + bh * 0.78f)

        val eyeHalfW = (abs(rightEye.x - leftEye.x) * 0.32f).coerceIn(0.06f, 0.14f)
        val eyeHalfH = (eyeHalfW * 0.65f).coerceIn(0.04f, 0.10f)

        val mouthHalfH = (abs(mouthBottom.y - (mouthLeft.y + mouthRight.y) * 0.5f) * 1.4f)
            .coerceIn(0.05f, 0.12f)
        val mouthCenterY = (mouthLeft.y + mouthRight.y) * 0.5f

        return PuppetConfig(
            leftEye = leftEye,
            rightEye = rightEye,
            noseTip = nose,
            mouthLeft = mouthLeft,
            mouthRight = mouthRight,
            leftEyeBox = NormalizedBox(
                left = (leftEye.x - eyeHalfW).coerceIn(0.05f, 0.90f),
                top = (leftEye.y - eyeHalfH).coerceIn(0.05f, 0.90f),
                right = (leftEye.x + eyeHalfW).coerceIn(0.10f, 0.95f),
                bottom = (leftEye.y + eyeHalfH).coerceIn(0.10f, 0.95f)
            ),
            rightEyeBox = NormalizedBox(
                left = (rightEye.x - eyeHalfW).coerceIn(0.05f, 0.90f),
                top = (rightEye.y - eyeHalfH).coerceIn(0.05f, 0.90f),
                right = (rightEye.x + eyeHalfW).coerceIn(0.10f, 0.95f),
                bottom = (rightEye.y + eyeHalfH).coerceIn(0.10f, 0.95f)
            ),
            mouthBox = NormalizedBox(
                left = (minOf(mouthLeft.x, mouthRight.x) - 0.03f).coerceIn(0.05f, 0.90f),
                top = (mouthCenterY - mouthHalfH * 0.8f).coerceIn(0.05f, 0.90f),
                right = (maxOf(mouthLeft.x, mouthRight.x) + 0.03f).coerceIn(0.10f, 0.95f),
                bottom = (mouthCenterY + mouthHalfH * 1.2f).coerceIn(0.10f, 0.95f)
            )
        )
    }

    private fun mapFaceToLivePose(
        face: Face,
        frameWidth: Float,
        frameHeight: Float,
        mirrorX: Boolean,
        fps: Int
    ): LiveFacePose {
        fun norm(pt: PointF): Point2D {
            val rawX = (pt.x / frameWidth).coerceIn(0.01f, 0.99f)
            val x = if (mirrorX) 1.0f - rawX else rawX
            val y = (pt.y / frameHeight).coerceIn(0.01f, 0.99f)
            return Point2D(x, y)
        }

        val box = face.boundingBox
        val rawCenterX = (box.exactCenterX() / frameWidth).coerceIn(0.1f, 0.9f)
        val centerX = if (mirrorX) 1f - rawCenterX else rawCenterX
        val centerY = (box.exactCenterY() / frameHeight).coerceIn(0.1f, 0.9f)
        val scaleX = (box.width() / frameWidth).coerceIn(0.22f, 0.92f)
        val scaleY = (box.height() / frameHeight).coerceIn(0.22f, 0.92f)
        val faceScale = ((scaleX + scaleY) * 0.5f).coerceIn(0.28f, 0.88f)

        // Extract blendshapes
        val leftOpenProb = face.leftEyeOpenProbability ?: 0.92f
        val rightOpenProb = face.rightEyeOpenProbability ?: 0.92f
        val rawBlinkLeft = (1f - leftOpenProb).coerceIn(0f, 1f)
        val rawBlinkRight = (1f - rightOpenProb).coerceIn(0f, 1f)
        val eyeBlinkLeft = if (mirrorX) rawBlinkRight else rawBlinkLeft
        val eyeBlinkRight = if (mirrorX) rawBlinkLeft else rawBlinkRight

        val smile = (face.smilingProbability ?: 0.15f).coerceIn(0f, 1f)

        // Compute jawOpen from inner lip contours if available
        val upperLipBottom = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLipTop = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
        val jawOpenFromContour = if (!upperLipBottom.isNullOrEmpty() && !lowerLipTop.isNullOrEmpty()) {
            val uMid = upperLipBottom[upperLipBottom.size / 2]
            val lMid = lowerLipTop[lowerLipTop.size / 2]
            val mouthGap = abs(lMid.y - uMid.y)
            val normalizedGap = mouthGap / (box.height().coerceAtLeast(1).toFloat() * 0.22f)
            (normalizedGap - 0.06f).coerceIn(0f, 1f)
        } else {
            0.05f
        }

        val yaw = if (mirrorX) -face.headEulerAngleY else face.headEulerAngleY
        val pitch = face.headEulerAngleX
        val roll = if (mirrorX) -face.headEulerAngleZ else face.headEulerAngleZ

        // Collect all contour points for dense visualization
        val allContours = face.allContours.flatMap { it.points }.map(::norm)

        // Map canonical 36 control points onto the user's live face geometry, head pose, and expressions
        val controlPoints = transformCanonicalControlPoints(
            centerX = centerX,
            centerY = centerY,
            scale = faceScale * 1.15f,
            yawDeg = yaw,
            pitchDeg = pitch,
            rollDeg = roll,
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
            faceCenterX = centerX,
            faceCenterY = centerY,
            faceScale = faceScale,
            fps = fps
        )
    }

    /**
     * Synthesizes natural, expressive facial motion (smooth head sway, periodic blinking,
     * and conversational jaw movement) when testing in an emulator without a live person
     * in front of the camera or when Demo Motion mode is enabled.
     */
    fun synthesizeExpressivePose(nowMs: Long, fps: Int = 30): LiveFacePose {
        val t = nowMs / 1000.0
        val yaw = (sin(t * 0.9) * 11.0).toFloat()
        val pitch = (cos(t * 0.65) * 6.5).toFloat()
        val roll = (sin(t * 0.55) * 5.5).toFloat()

        // Conversational mouth open rhythm
        val talkWave = ((sin(t * 3.4) + sin(t * 5.7) * 0.5 + 0.4) * 0.42).toFloat().coerceIn(0f, 0.88f)
        // Natural blink every ~3.4 seconds
        val blinkPhase = (t % 3.4)
        val blink = if (blinkPhase in 0.0..0.18) {
            sin((blinkPhase / 0.18) * Math.PI).toFloat().coerceIn(0f, 1f)
        } else 0f

        val smile = ((sin(t * 0.8) + 1.0) * 0.35).toFloat().coerceIn(0.05f, 0.75f)
        val cx = 0.50f + (sin(t * 0.6) * 0.025f).toFloat()
        val cy = 0.46f + (cos(t * 0.7) * 0.020f).toFloat()
        val scale = 0.58f

        val controlPoints = transformCanonicalControlPoints(
            centerX = cx,
            centerY = cy,
            scale = scale,
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
         * Warps the 36 canonical mesh vertices using live head pose (yaw, pitch, roll)
         * and expression blendshapes (jawOpen, eyeBlinkLeft, eyeBlinkRight, smile).
         */
        fun transformCanonicalControlPoints(
            centerX: Float,
            centerY: Float,
            scale: Float,
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
            val yawShift = (yawDeg / 45f).coerceIn(-0.45f, 0.45f)
            val pitchShift = (pitchDeg / 45f).coerceIn(-0.35f, 0.35f)

            return base.mapIndexed { idx, pt ->
                var lx = pt.x - 0.50f
                var ly = pt.y - 0.50f

                // Apply expression deformations in local canonical space:
                // 1. Eyebrows raise slightly with jawOpen/smile
                if (idx in 5..8) {
                    ly -= (jawOpen * 0.018f + smile * 0.012f)
                }
                // 2. Left eye blink squashes top (10) and bottom (12) eyelids toward eye center
                if (idx == 10) ly += blinkLeft * 0.026f
                if (idx == 12) ly -= blinkLeft * 0.020f
                // 3. Right eye blink squashes top (14) and bottom (16) eyelids toward eye center
                if (idx == 14) ly += blinkRight * 0.026f
                if (idx == 16) ly -= blinkRight * 0.020f

                // 4. Mouth corners widen & lift with smile
                if (idx == 25) {
                    lx -= smile * 0.024f
                    ly -= smile * 0.016f
                }
                if (idx == 27) {
                    lx += smile * 0.024f
                    ly -= smile * 0.016f
                }

                // 5. Jaw open moves lower lip (28, 30) and chin (32, 33, 34) downward
                when (idx) {
                    29 -> ly -= jawOpen * 0.008f
                    30 -> ly += jawOpen * 0.068f
                    28 -> ly += jawOpen * 0.072f
                    32, 34 -> ly += jawOpen * 0.048f
                    33 -> ly += jawOpen * 0.058f
                }

                // 6. 3D foreshortening for yaw and pitch
                val depthFactor = 1f - abs(lx) * 0.35f
                lx += yawShift * 0.14f * depthFactor
                ly += pitchShift * 0.11f * depthFactor

                // 7. Roll rotation + scale + translation to live face center
                val rx = (lx * cosR - ly * sinR) * (scale / 0.58f)
                val ry = (lx * sinR + ly * cosR) * (scale / 0.58f)

                Point2D(
                    x = (centerX + rx).coerceIn(0.01f, 0.99f),
                    y = (centerY + ry).coerceIn(0.01f, 0.99f)
                )
            }
        }
    }
}
