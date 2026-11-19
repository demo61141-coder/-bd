package com.example.video

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.example.data.DemoCharacterCatalog
import com.example.model.CharacterItem
import com.example.model.LiveFacePose
import com.example.model.Point2D
import com.example.model.VideoResolution
import com.example.recording.StudioMediaRecorder
import com.example.rendering.FaceSwapRenderer
import com.example.tracking.FaceTrackerEngine
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceContour
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.FaceLandmark
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.hypot

data class DetectedVideoFaceSlot(
    val slotIndex: Int,
    val trackingId: Int?,
    val labelBn: String,
    val labelEn: String,
    val thumbnail: Bitmap,
    val centerX: Float,
    val centerY: Float,
    val assignedCharacterId: String? = null // null means "Keep Original Face / আসল মুখ"
)

data class MatchedFrameFace(
    val slotIndex: Int,
    val pose: LiveFacePose
)

data class ScannedVideoSession(
    val videoUri: Uri,
    val durationMs: Long,
    val videoWidth: Int,
    val videoHeight: Int,
    val sampleFrames: List<Bitmap>,
    val detectedSlots: List<DetectedVideoFaceSlot>
)

class VideoMultiFaceProcessor(private val context: Context) {

    private val multiFaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.07f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Scans an uploaded pre-recorded video, extracts sample frames across its timeline,
     * detects all faces present in the video, and clusters them into distinct [DetectedVideoFaceSlot]s
     * so the user can assign a different swap character to each person in the video.
     */
    suspend fun scanUploadedVideo(
        videoUri: Uri,
        defaultCharacterId: String
    ): ScannedVideoSession? = withContext(Dispatchers.IO) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, videoUri)
            val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(1000L) ?: 4000L
            val rawW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 720
            val rawH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 1280
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
                ?.toIntOrNull() ?: 0

            val isRotated = rotation == 90 || rotation == 270
            val videoW = if (isRotated) rawH else rawW
            val videoH = if (isRotated) rawW else rawH

            // Sample up to 12 frames across the video timeline for preview playback and face clustering
            val sampleCount = 12
            val frames = mutableListOf<Bitmap>()
            for (i in 0 until sampleCount) {
                val timeUs = ((durationMs * 1000L * i) / sampleCount).coerceAtLeast(0L)
                val rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                if (rawFrame != null) {
                    val scaled = scaleFrameForStudio(rawFrame, maxDim = 640)
                    frames.add(scaled)
                }
            }

            if (frames.isEmpty()) {
                retriever.getFrameAtTime(0L)?.let { frames.add(scaleFrameForStudio(it, 640)) }
            }
            if (frames.isEmpty()) return@withContext null

            // Cluster all faces detected across the sampled frames
            val slots = mutableListOf<DetectedVideoFaceSlot>()

            for (frame in frames) {
                val faces = detectFacesInBitmap(frame)
                val fw = frame.width.toFloat().coerceAtLeast(1f)
                val fh = frame.height.toFloat().coerceAtLeast(1f)

                // Sort left-to-right for deterministic person numbering
                val sortedFaces = faces.sortedBy { it.boundingBox.exactCenterX() }
                for (face in sortedFaces) {
                    val cx = (face.boundingBox.exactCenterX() / fw).coerceIn(0.05f, 0.95f)
                    val cy = (face.boundingBox.exactCenterY() / fh).coerceIn(0.05f, 0.95f)
                    val trackId = face.trackingId

                    val existingIdx = slots.indexOfFirst { slot ->
                        (trackId != null && slot.trackingId == trackId) ||
                            hypot(slot.centerX - cx, slot.centerY - cy) < 0.17f
                    }

                    if (existingIdx == -1 && slots.size < 6) {
                        val slotNumber = slots.size + 1
                        val thumb = cropFaceThumbnail(frame, face)
                        slots.add(
                            DetectedVideoFaceSlot(
                                slotIndex = slots.size,
                                trackingId = trackId,
                                labelBn = "মুখ #$slotNumber",
                                labelEn = "Face #$slotNumber",
                                thumbnail = thumb,
                                centerX = cx,
                                centerY = cy,
                                // Auto-assign the first detected face to the currently selected character
                                assignedCharacterId = if (slots.isEmpty()) defaultCharacterId else null
                            )
                        )
                    }
                }
            }

            // If a video had very low light or small faces and 0 slots were auto-clustered,
            // create a default center face slot from the first frame so the user can still swap
            if (slots.isEmpty()) {
                val first = frames.first()
                slots.add(
                    DetectedVideoFaceSlot(
                        slotIndex = 0,
                        trackingId = null,
                        labelBn = "মুখ #১",
                        labelEn = "Face #1",
                        thumbnail = cropCenterThumbnail(first),
                        centerX = 0.50f,
                        centerY = 0.42f,
                        assignedCharacterId = defaultCharacterId
                    )
                )
            }

            ScannedVideoSession(
                videoUri = videoUri,
                durationMs = durationMs,
                videoWidth = videoW,
                videoHeight = videoH,
                sampleFrames = frames,
                detectedSlots = slots
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Detects all faces in a single video frame and matches each detected face to its
     * corresponding [DetectedVideoFaceSlot].
     */
    suspend fun matchFacesInFrame(
        frame: Bitmap,
        slots: List<DetectedVideoFaceSlot>
    ): List<MatchedFrameFace> {
        val faces = detectFacesInBitmap(frame)
        val fw = frame.width.toFloat().coerceAtLeast(1f)
        val fh = frame.height.toFloat().coerceAtLeast(1f)

        if (faces.isEmpty()) {
            return slots.map { slot ->
                val controlPts = FaceTrackerEngine.transformCanonicalToBox(
                    centerX = slot.centerX,
                    centerY = slot.centerY,
                    boxWidth = 0.34f,
                    boxHeight = 0.36f,
                    yawDeg = 0f,
                    pitchDeg = 0f,
                    rollDeg = 0f,
                    jawOpen = 0.06f,
                    blinkLeft = 0f,
                    blinkRight = 0f,
                    smile = 0.15f
                )
                MatchedFrameFace(
                    slotIndex = slot.slotIndex,
                    pose = LiveFacePose(
                        isTracking = true,
                        controlPoints = controlPts,
                        faceCenterX = slot.centerX,
                        faceCenterY = slot.centerY,
                        faceScale = 0.34f
                    )
                )
            }
        }

        val usedSlots = mutableSetOf<Int>()
        val results = mutableListOf<MatchedFrameFace>()

        for (face in faces) {
            val cx = (face.boundingBox.exactCenterX() / fw).coerceIn(0.05f, 0.95f)
            val cy = (face.boundingBox.exactCenterY() / fh).coerceIn(0.05f, 0.95f)
            val trackId = face.trackingId

            val matchedSlot = slots
                .filter { it.slotIndex !in usedSlots }
                .minByOrNull { slot ->
                    if (trackId != null && slot.trackingId == trackId) {
                        0f
                    } else {
                        hypot(slot.centerX - cx, slot.centerY - cy)
                    }
                } ?: slots.firstOrNull()

            if (matchedSlot != null) {
                usedSlots.add(matchedSlot.slotIndex)
                val pose = mapVideoFaceToPose(face, fw, fh)
                results.add(MatchedFrameFace(slotIndex = matchedSlot.slotIndex, pose = pose))
            }
        }
        return results
    }

    /**
     * Renders a single video frame with all assigned character face swaps applied simultaneously.
     */
    fun renderMultiFaceFrame(
        sourceFrame: Bitmap,
        matchedFaces: List<MatchedFrameFace>,
        slots: List<DetectedVideoFaceSlot>,
        allCharacters: List<CharacterItem>,
        blendOpacity: Float,
        detailedMouthInterior: Boolean,
        showMeshWireframe: Boolean
    ): Bitmap {
        val out = sourceFrame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()

        for (matched in matchedFaces) {
            val slot = slots.find { it.slotIndex == matched.slotIndex } ?: continue
            val charId = slot.assignedCharacterId ?: continue // null = Keep Original Face
            val character = allCharacters.find { it.id == charId } ?: continue
            val charBmp = DemoCharacterCatalog.getCharacterBitmap(context, character)

            FaceSwapRenderer.renderWarpedCharacter(
                canvas = canvas,
                viewportWidth = w,
                viewportHeight = h,
                character = character,
                characterBitmap = charBmp,
                pose = matched.pose,
                blendOpacity = blendOpacity,
                detailedMouthInterior = detailedMouthInterior,
                showMeshWireframe = showMeshWireframe
            )
        }
        return out
    }

    /**
     * Exports the multi-face swapped video to an MP4 file in `filesDir/captures/`.
     */
    suspend fun exportMultiFaceSwappedVideo(
        session: ScannedVideoSession,
        slots: List<DetectedVideoFaceSlot>,
        allCharacters: List<CharacterItem>,
        resolution: VideoResolution,
        blendOpacity: Float,
        detailedMouthInterior: Boolean,
        onProgress: (Float) -> Unit
    ): File? = withContext(Dispatchers.IO) {
        val recorder = StudioMediaRecorder(context)
        val started = recorder.startRecording(resolution = resolution, hasMicPermission = false)
        if (!started) return@withContext null

        try {
            val frames = session.sampleFrames
            val totalSteps = (frames.size * 3).coerceAtLeast(15)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

            for (step in 0 until totalSteps) {
                val frameIdx = (step * frames.size) / totalSteps
                val rawFrame = frames[frameIdx.coerceIn(0, frames.lastIndex)]
                val matched = matchFacesInFrame(rawFrame, slots)
                val composited = renderMultiFaceFrame(
                    sourceFrame = rawFrame,
                    matchedFaces = matched,
                    slots = slots,
                    allCharacters = allCharacters,
                    blendOpacity = blendOpacity,
                    detailedMouthInterior = detailedMouthInterior,
                    showMeshWireframe = false
                )

                // Draw composited multi-face frame onto the active recording surface via first active character
                val primaryChar = allCharacters.first()
                val primaryBmp = DemoCharacterCatalog.getCharacterBitmap(context, primaryChar)
                recorder.recordCompositeFrame(
                    cameraPreviewBitmap = composited,
                    character = primaryChar,
                    characterBitmap = primaryBmp,
                    // Pass isTracking = false since composited already has all swapped faces drawn!
                    pose = LiveFacePose(isTracking = false),
                    blendOpacity = blendOpacity,
                    detailedMouthInterior = detailedMouthInterior,
                    showMeshWireframe = false,
                    isMirrored = false
                )
                onProgress((step + 1).toFloat() / totalSteps)
                delay(45L)
            }
            recorder.stopRecording()?.file
        } catch (_: Exception) {
            recorder.stopRecording()?.file
        }
    }

    private suspend fun detectFacesInBitmap(bitmap: Bitmap): List<Face> =
        suspendCancellableCoroutine { cont ->
            val input = InputImage.fromBitmap(bitmap, 0)
            multiFaceDetector.process(input)
                .addOnSuccessListener { faces ->
                    if (cont.isActive) cont.resume(faces ?: emptyList())
                }
                .addOnFailureListener {
                    if (cont.isActive) cont.resume(emptyList())
                }
        }

    private fun mapVideoFaceToPose(face: Face, fw: Float, fh: Float): LiveFacePose {
        fun norm(pt: PointF) = Point2D(
            (pt.x / fw).coerceIn(0.01f, 0.99f),
            (pt.y / fh).coerceIn(0.01f, 0.99f)
        )

        val box = face.boundingBox
        val cx = (box.exactCenterX() / fw).coerceIn(0.05f, 0.95f)
        val cy = (box.exactCenterY() / fh).coerceIn(0.05f, 0.95f)
        val bw = (box.width() / fw).coerceIn(0.10f, 0.90f)
        val bh = (box.height() / fh).coerceIn(0.10f, 0.90f)

        val blinkL = (1f - (face.leftEyeOpenProbability ?: 0.92f)).coerceIn(0f, 1f)
        val blinkR = (1f - (face.rightEyeOpenProbability ?: 0.92f)).coerceIn(0f, 1f)
        val smile = (face.smilingProbability ?: 0.15f).coerceIn(0f, 1f)

        val upperLip = face.getContour(FaceContour.UPPER_LIP_BOTTOM)?.points
        val lowerLip = face.getContour(FaceContour.LOWER_LIP_TOP)?.points
        val jawOpen = if (!upperLip.isNullOrEmpty() && !lowerLip.isNullOrEmpty()) {
            val uMid = upperLip[upperLip.size / 2]
            val lMid = lowerLip[lowerLip.size / 2]
            ((abs(lMid.y - uMid.y) / (box.height().coerceAtLeast(1) * 0.20f)) - 0.05f).coerceIn(0f, 1f)
        } else 0.05f

        val pts = FaceTrackerEngine.transformCanonicalToBox(
            centerX = cx,
            centerY = cy,
            boxWidth = bw * 0.94f,
            boxHeight = bh * 0.94f,
            yawDeg = face.headEulerAngleY,
            pitchDeg = face.headEulerAngleX,
            rollDeg = face.headEulerAngleZ,
            jawOpen = jawOpen,
            blinkLeft = blinkL,
            blinkRight = blinkR,
            smile = smile
        ).toMutableList()

        // Lock to detected eye, nose, and mouth landmarks
        val e1 = face.getLandmark(FaceLandmark.LEFT_EYE)?.position?.let(::norm)
        val e2 = face.getLandmark(FaceLandmark.RIGHT_EYE)?.position?.let(::norm)
        if (e1 != null && e2 != null) {
            val le = if (e1.x <= e2.x) e1 else e2
            val re = if (e1.x <= e2.x) e2 else e1
            val ew = bw * 0.105f
            val eh = bh * 0.042f
            pts[9] = Point2D(le.x - ew, le.y)
            pts[10] = Point2D(le.x, le.y - eh)
            pts[11] = Point2D(le.x + ew, le.y)
            pts[12] = Point2D(le.x, le.y + eh)

            pts[13] = Point2D(re.x - ew, re.y)
            pts[14] = Point2D(re.x, re.y - eh)
            pts[15] = Point2D(re.x + ew, re.y)
            pts[16] = Point2D(re.x, re.y + eh)
        }

        face.getLandmark(FaceLandmark.NOSE_BASE)?.position?.let(::norm)?.let { np ->
            pts[18] = np
        }

        val m1 = face.getLandmark(FaceLandmark.MOUTH_LEFT)?.position?.let(::norm)
        val m2 = face.getLandmark(FaceLandmark.MOUTH_RIGHT)?.position?.let(::norm)
        if (m1 != null && m2 != null) {
            val ml = if (m1.x <= m2.x) m1 else m2
            val mr = if (m1.x <= m2.x) m2 else m1
            pts[25] = ml
            pts[27] = mr
        }

        return LiveFacePose(
            isTracking = true,
            controlPoints = pts,
            jawOpen = jawOpen,
            eyeBlinkLeft = blinkL,
            eyeBlinkRight = blinkR,
            smile = smile,
            yawDegrees = face.headEulerAngleY,
            pitchDegrees = face.headEulerAngleX,
            rollDegrees = face.headEulerAngleZ,
            faceCenterX = cx,
            faceCenterY = cy,
            faceScale = (bw + bh) * 0.5f
        )
    }

    private fun scaleFrameForStudio(src: Bitmap, maxDim: Int): Bitmap {
        val ratio = minOf(
            maxDim.toFloat() / src.width.coerceAtLeast(1),
            maxDim.toFloat() / src.height.coerceAtLeast(1),
            1.0f
        )
        val w = (src.width * ratio).toInt().coerceAtLeast(64)
        val h = (src.height * ratio).toInt().coerceAtLeast(64)
        return Bitmap.createScaledBitmap(src, w, h, true)
    }

    private fun cropFaceThumbnail(frame: Bitmap, face: Face): Bitmap {
        val box = face.boundingBox
        val pad = (maxOf(box.width(), box.height()) * 0.25f).toInt()
        val left = (box.left - pad).coerceIn(0, (frame.width - 16).coerceAtLeast(0))
        val top = (box.top - pad).coerceIn(0, (frame.height - 16).coerceAtLeast(0))
        val right = (box.right + pad).coerceIn(left + 16, frame.width)
        val bottom = (box.bottom + pad).coerceIn(top + 16, frame.height)
        val cropped = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, 160, 160, true)
    }

    private fun cropCenterThumbnail(frame: Bitmap): Bitmap {
        val size = minOf(frame.width, frame.height) / 2
        val left = (frame.width - size) / 2
        val top = (frame.height - size) / 3
        val cropped = Bitmap.createBitmap(
            frame,
            left.coerceAtLeast(0),
            top.coerceAtLeast(0),
            size.coerceAtLeast(16),
            size.coerceAtLeast(16)
        )
        return Bitmap.createScaledBitmap(cropped, 160, 160, true)
    }
}
