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
    val defaultScale: Float = 0.34f,
    val defaultYaw: Float = 0f,
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
    val detectedSlots: List<DetectedVideoFaceSlot>,
    // Pre-computed per-frame multi-face trajectories (supports full side-profile head turns!)
    val frameTrajectories: List<List<MatchedFrameFace>> = emptyList()
)

class VideoMultiFaceProcessor(private val context: Context) {

    private val multiFaceDetector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setMinFaceSize(0.05f)
            .enableTracking()
            .build()
        FaceDetection.getClient(options)
    }

    /**
     * Scans an uploaded pre-recorded video, extracts sample frames across its timeline,
     * detects all faces present in the video, clusters them into distinct [DetectedVideoFaceSlot]s,
     * and computes a continuous 3D pose trajectory across every frame (interpolating and tracking
     * skin/head motion during side-profile head turns where frontal detection drops out).
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

            // Sample up to 16 frames across the video timeline for smooth playback & profile interpolation
            val sampleCount = 16
            val frames = mutableListOf<Bitmap>()
            for (i in 0 until sampleCount) {
                val timeUs = ((durationMs * 1000L * i) / sampleCount).coerceAtLeast(0L)
                val rawFrame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                    ?: retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                if (rawFrame != null) {
                    val scaled = scaleFrameForStudio(rawFrame, maxDim = 640)
                    frames.add(scaled)
                }
            }

            if (frames.isEmpty()) {
                retriever.getFrameAtTime(0L)?.let { frames.add(scaleFrameForStudio(it, 640)) }
            }
            if (frames.isEmpty()) return@withContext null

            // Detect faces on all sampled frames (with contrast enhancement fallback for backlit outdoor shots)
            val rawDetectionsPerFrame = frames.map { frame ->
                val direct = detectFacesInBitmap(frame)
                if (direct.isNotEmpty()) direct else detectFacesInBitmap(enhanceOutdoorFrame(frame))
            }

            // Cluster all faces detected across the sampled frames
            val slots = mutableListOf<DetectedVideoFaceSlot>()

            for (frameIdx in frames.indices) {
                val frame = frames[frameIdx]
                val faces = rawDetectionsPerFrame[frameIdx]
                val fw = frame.width.toFloat().coerceAtLeast(1f)
                val fh = frame.height.toFloat().coerceAtLeast(1f)

                val sortedFaces = faces.sortedBy { it.boundingBox.exactCenterX() }
                for (face in sortedFaces) {
                    val cx = (face.boundingBox.exactCenterX() / fw).coerceIn(0.05f, 0.95f)
                    val cy = (face.boundingBox.exactCenterY() / fh).coerceIn(0.05f, 0.95f)
                    val bw = (face.boundingBox.width() / fw).coerceIn(0.12f, 0.85f)
                    val bh = (face.boundingBox.height() / fh).coerceIn(0.12f, 0.85f)
                    val trackId = face.trackingId

                    val existingIdx = slots.indexOfFirst { slot ->
                        (trackId != null && slot.trackingId == trackId) ||
                            hypot(slot.centerX - cx, slot.centerY - cy) < 0.22f
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
                                defaultScale = (bw + bh) * 0.5f,
                                defaultYaw = face.headEulerAngleY,
                                assignedCharacterId = if (slots.isEmpty()) defaultCharacterId else null
                            )
                        )
                    }
                }
            }

            // If a video consists entirely of a side-profile head turn where ML Kit missed every frame,
            // locate the person's head & skin region automatically so the face slot is still detected and tracked!
            if (slots.isEmpty()) {
                val first = frames.first()
                val profileEst = estimateProfileHeadInBitmap(first, prevX = 0.52f, prevY = 0.42f, prevScale = 0.34f)
                slots.add(
                    DetectedVideoFaceSlot(
                        slotIndex = 0,
                        trackingId = null,
                        labelBn = "মুখ #১",
                        labelEn = "Face #1",
                        thumbnail = cropRegionThumbnail(first, profileEst.centerX, profileEst.centerY, profileEst.scale),
                        centerX = profileEst.centerX,
                        centerY = profileEst.centerY,
                        defaultScale = profileEst.scale,
                        defaultYaw = profileEst.estimatedYaw,
                        assignedCharacterId = defaultCharacterId
                    )
                )
            }

            // Build complete per-frame trajectories with side-profile tracking & temporal interpolation
            val trajectories = buildContinuousFrameTrajectories(
                frames = frames,
                rawDetectionsPerFrame = rawDetectionsPerFrame,
                slots = slots
            )

            ScannedVideoSession(
                videoUri = videoUri,
                durationMs = durationMs,
                videoWidth = videoW,
                videoHeight = videoH,
                sampleFrames = frames,
                detectedSlots = slots,
                frameTrajectories = trajectories
            )
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    /**
     * Builds a smooth, uninterrupted 3D face pose trajectory for every detected person across all
     * frames of the video. When a person turns their head sideways (>45°..80° profile) and frontal
     * detection misses that frame, we combine skin/head region tracking with bi-directional
     * trajectory interpolation so the swapped face turns naturally with their head and never vanishes!
     */
    private fun buildContinuousFrameTrajectories(
        frames: List<Bitmap>,
        rawDetectionsPerFrame: List<List<Face>>,
        slots: List<DetectedVideoFaceSlot>
    ): List<List<MatchedFrameFace>> {
        val frameCount = frames.size
        // slotPoses[slotIdx][frameIdx]
        val slotPoses = Array(slots.size) { arrayOfNulls<LiveFacePose>(frameCount) }

        // Step 1: Assign directly detected ML Kit faces to their corresponding slot
        for (fIdx in 0 until frameCount) {
            val frame = frames[fIdx]
            val fw = frame.width.toFloat().coerceAtLeast(1f)
            val fh = frame.height.toFloat().coerceAtLeast(1f)
            val faces = rawDetectionsPerFrame[fIdx]
            val usedSlots = mutableSetOf<Int>()

            for (face in faces) {
                val cx = (face.boundingBox.exactCenterX() / fw).coerceIn(0.05f, 0.95f)
                val cy = (face.boundingBox.exactCenterY() / fh).coerceIn(0.05f, 0.95f)
                val trackId = face.trackingId

                val matchedSlot = slots
                    .filter { it.slotIndex !in usedSlots }
                    .minByOrNull { slot ->
                        if (trackId != null && slot.trackingId == trackId) 0f
                        else hypot(slot.centerX - cx, slot.centerY - cy)
                    } ?: continue

                usedSlots.add(matchedSlot.slotIndex)
                slotPoses[matchedSlot.slotIndex][fIdx] = mapVideoFaceToPose(face, fw, fh)
            }
        }

        // Step 2: Fill side-profile gaps for each slot using skin/head centroid tracking + yaw interpolation
        for (sIdx in slots.indices) {
            val slot = slots[sIdx]
            val poses = slotPoses[sIdx]

            for (fIdx in 0 until frameCount) {
                if (poses[fIdx] != null) continue

                // Find nearest previous and next detected frames for this person
                var prevIdx = fIdx - 1
                while (prevIdx >= 0 && poses[prevIdx] == null) prevIdx--
                var nextIdx = fIdx + 1
                while (nextIdx < frameCount && poses[nextIdx] == null) nextIdx++

                val prevPose = if (prevIdx >= 0) poses[prevIdx] else null
                val nextPose = if (nextIdx < frameCount) poses[nextIdx] else null

                val anchorX: Float
                val anchorY: Float
                val anchorScale: Float
                val baseYaw: Float
                val basePitch: Float
                val baseRoll: Float
                val baseJaw: Float

                when {
                    prevPose != null && nextPose != null -> {
                        val t = (fIdx - prevIdx).toFloat() / (nextIdx - prevIdx).coerceAtLeast(1)
                        anchorX = prevPose.faceCenterX + (nextPose.faceCenterX - prevPose.faceCenterX) * t
                        anchorY = prevPose.faceCenterY + (nextPose.faceCenterY - prevPose.faceCenterY) * t
                        anchorScale = prevPose.faceScale + (nextPose.faceScale - prevPose.faceScale) * t
                        baseYaw = prevPose.yawDegrees + (nextPose.yawDegrees - prevPose.yawDegrees) * t
                        basePitch = prevPose.pitchDegrees + (nextPose.pitchDegrees - prevPose.pitchDegrees) * t
                        baseRoll = prevPose.rollDegrees + (nextPose.rollDegrees - prevPose.rollDegrees) * t
                        baseJaw = prevPose.jawOpen + (nextPose.jawOpen - prevPose.jawOpen) * t
                    }
                    prevPose != null -> {
                        anchorX = prevPose.faceCenterX
                        anchorY = prevPose.faceCenterY
                        anchorScale = prevPose.faceScale
                        baseYaw = prevPose.yawDegrees
                        basePitch = prevPose.pitchDegrees
                        baseRoll = prevPose.rollDegrees
                        baseJaw = prevPose.jawOpen
                    }
                    nextPose != null -> {
                        anchorX = nextPose.faceCenterX
                        anchorY = nextPose.faceCenterY
                        anchorScale = nextPose.faceScale
                        baseYaw = nextPose.yawDegrees
                        basePitch = nextPose.pitchDegrees
                        baseRoll = nextPose.rollDegrees
                        baseJaw = nextPose.jawOpen
                    }
                    else -> {
                        anchorX = slot.centerX
                        anchorY = slot.centerY
                        anchorScale = slot.defaultScale
                        baseYaw = slot.defaultYaw
                        basePitch = 0f
                        baseRoll = 0f
                        baseJaw = 0.06f
                    }
                }

                // Refine position & side-profile yaw angle directly from the current video frame's skin/hair distribution!
                val profileEst = estimateProfileHeadInBitmap(
                    frame = frames[fIdx],
                    prevX = anchorX,
                    prevY = anchorY,
                    prevScale = anchorScale
                )

                // If frontal detection missed this frame, the head is almost certainly turned in side profile (±42°..65°)
                val finalYaw = if (abs(profileEst.estimatedYaw) > abs(baseYaw)) {
                    profileEst.estimatedYaw
                } else if (abs(baseYaw) < 20f) {
                    // Push toward detected profile orientation
                    profileEst.estimatedYaw
                } else {
                    baseYaw.coerceIn(-68f, 68f)
                }

                val controlPts = FaceTrackerEngine.transformCanonicalToBox(
                    centerX = profileEst.centerX,
                    centerY = profileEst.centerY,
                    boxWidth = profileEst.scale * 0.94f,
                    boxHeight = profileEst.scale * 0.94f,
                    yawDeg = finalYaw,
                    pitchDeg = basePitch,
                    rollDeg = baseRoll,
                    jawOpen = baseJaw,
                    blinkLeft = 0f,
                    blinkRight = 0f,
                    smile = 0.15f
                )

                poses[fIdx] = LiveFacePose(
                    isTracking = true,
                    controlPoints = controlPts,
                    jawOpen = baseJaw,
                    yawDegrees = finalYaw,
                    pitchDegrees = basePitch,
                    rollDegrees = baseRoll,
                    faceCenterX = profileEst.centerX,
                    faceCenterY = profileEst.centerY,
                    faceScale = profileEst.scale
                )
            }
        }

        // Convert to per-frame list of MatchedFrameFace
        return List(frameCount) { fIdx ->
            slots.mapNotNull { slot ->
                val p = slotPoses[slot.slotIndex][fIdx]
                if (p != null) MatchedFrameFace(slotIndex = slot.slotIndex, pose = p) else null
            }
        }
    }

    /**
     * Returns the matched face poses for a given frame, using the precomputed continuous trajectory
     * if available or running live detection + profile estimation on the fly.
     */
    suspend fun matchFacesInFrame(
        frame: Bitmap,
        slots: List<DetectedVideoFaceSlot>,
        frameIndex: Int = -1,
        session: ScannedVideoSession? = null
    ): List<MatchedFrameFace> {
        if (session != null && frameIndex in session.frameTrajectories.indices) {
            return session.frameTrajectories[frameIndex]
        }

        val directFaces = detectFacesInBitmap(frame)
        val faces = if (directFaces.isNotEmpty()) directFaces else detectFacesInBitmap(enhanceOutdoorFrame(frame))
        val fw = frame.width.toFloat().coerceAtLeast(1f)
        val fh = frame.height.toFloat().coerceAtLeast(1f)

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

        // For any slot whose face is turned into a side profile on this frame, estimate its side-profile pose
        for (slot in slots) {
            if (slot.slotIndex !in usedSlots) {
                val est = estimateProfileHeadInBitmap(frame, slot.centerX, slot.centerY, slot.defaultScale)
                val controlPts = FaceTrackerEngine.transformCanonicalToBox(
                    centerX = est.centerX,
                    centerY = est.centerY,
                    boxWidth = est.scale * 0.94f,
                    boxHeight = est.scale * 0.94f,
                    yawDeg = est.estimatedYaw,
                    pitchDeg = 0f,
                    rollDeg = 0f,
                    jawOpen = 0.06f,
                    blinkLeft = 0f,
                    blinkRight = 0f,
                    smile = 0.15f
                )
                results.add(
                    MatchedFrameFace(
                        slotIndex = slot.slotIndex,
                        pose = LiveFacePose(
                            isTracking = true,
                            controlPoints = controlPts,
                            yawDegrees = est.estimatedYaw,
                            faceCenterX = est.centerX,
                            faceCenterY = est.centerY,
                            faceScale = est.scale
                        )
                    )
                )
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
                val frameIdx = ((step * frames.size) / totalSteps).coerceIn(0, frames.lastIndex)
                val rawFrame = frames[frameIdx]
                val matched = matchFacesInFrame(rawFrame, slots, frameIndex = frameIdx, session = session)
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

    private data class ProfileHeadEstimate(
        val centerX: Float,
        val centerY: Float,
        val scale: Float,
        val estimatedYaw: Float
    )

    /**
     * Detects the position, scale, and left/right profile yaw direction of a person's head
     * when they turn sideways (45°..85° side profile) by analyzing the spatial distribution of
     * skin-tone pixels (facial profile) vs dark hair pixels (back of the head) around the search window.
     */
    private fun estimateProfileHeadInBitmap(
        frame: Bitmap,
        prevX: Float,
        prevY: Float,
        prevScale: Float
    ): ProfileHeadEstimate {
        val w = frame.width
        val h = frame.height
        if (w < 16 || h < 16) {
            return ProfileHeadEstimate(prevX, prevY, prevScale, 0f)
        }

        var skinSumX = 0f
        var skinSumY = 0f
        var skinWeight = 0f

        var hairSumX = 0f
        var hairWeight = 0f

        val step = 6
        val searchRadius = (prevScale * 1.15f).coerceIn(0.22f, 0.55f)

        for (py in step until (h - step) step step) {
            val ny = py.toFloat() / h
            if (abs(ny - prevY) > searchRadius) continue

            for (px in step until (w - step) step step) {
                val nx = px.toFloat() / w
                if (abs(nx - prevX) > searchRadius) continue

                val pixel = frame.getPixel(px, py)
                val r = Color.red(pixel)
                val g = Color.green(pixel)
                val b = Color.blue(pixel)

                // Warm skin-tone chrominance rule (works across South Asian and diverse skin tones in outdoor/indoor light)
                val isSkin = r > 55 && g > 35 && b > 20 &&
                    r > g && g >= (b * 0.85f).toInt() &&
                    (r - b) > 14 && r < 245

                val dist = hypot(nx - prevX, ny - prevY)
                val spatialWeight = (1f - (dist / searchRadius)).coerceAtLeast(0.1f)

                if (isSkin) {
                    skinSumX += nx * spatialWeight
                    skinSumY += ny * spatialWeight
                    skinWeight += spatialWeight
                } else if (r < 65 && g < 65 && b < 68 && ny < prevY + prevScale * 0.35f) {
                    // Dark hair region on top/back of the head
                    hairSumX += nx * spatialWeight
                    hairWeight += spatialWeight
                }
            }
        }

        if (skinWeight < 1.5f) {
            return ProfileHeadEstimate(prevX, prevY, prevScale, 0f)
        }

        val skinCx = (skinSumX / skinWeight).coerceIn(0.12f, 0.88f)
        val skinCy = (skinSumY / skinWeight).coerceIn(0.12f, 0.88f)

        // Compare skin centroid (front of face) vs hair centroid (back of head) to infer left/right profile turn!
        val estimatedYaw = if (hairWeight > 1.0f) {
            val hairCx = hairSumX / hairWeight
            // If skin is to the LEFT of hair (skinCx < hairCx), the person is looking LEFT (negative yaw),
            // just like in the user's outdoor side-profile video screenshot!
            val deltaX = (skinCx - hairCx) / prevScale.coerceAtLeast(0.15f)
            (deltaX * 95f).coerceIn(-62f, 62f)
        } else {
            0f
        }

        // Blend smoothly with previous anchor so the face glides naturally with the head
        val finalCx = (prevX * 0.35f + skinCx * 0.65f).coerceIn(0.12f, 0.88f)
        val finalCy = (prevY * 0.35f + skinCy * 0.65f).coerceIn(0.12f, 0.88f)

        return ProfileHeadEstimate(
            centerX = finalCx,
            centerY = finalCy,
            scale = prevScale.coerceIn(0.20f, 0.65f),
            estimatedYaw = estimatedYaw
        )
    }

    private fun enhanceOutdoorFrame(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val cm = android.graphics.ColorMatrix(
            floatArrayOf(
                1.22f, 0f, 0f, 0f, 18f,
                0f, 1.22f, 0f, 0f, 18f,
                0f, 0f, 1.22f, 0f, 18f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = android.graphics.ColorMatrixColorFilter(cm)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    private fun cropRegionThumbnail(frame: Bitmap, cxNorm: Float, cyNorm: Float, scaleNorm: Float): Bitmap {
        val w = frame.width
        val h = frame.height
        val halfSize = (minOf(w, h) * scaleNorm * 0.7f).toInt().coerceAtLeast(24)
        val cx = (cxNorm * w).toInt()
        val cy = (cyNorm * h).toInt()
        val left = (cx - halfSize).coerceIn(0, (w - 16).coerceAtLeast(0))
        val top = (cy - halfSize).coerceIn(0, (h - 16).coerceAtLeast(0))
        val right = (cx + halfSize).coerceIn(left + 16, w)
        val bottom = (cy + halfSize).coerceIn(top + 16, h)
        val cropped = Bitmap.createBitmap(frame, left, top, right - left, bottom - top)
        return Bitmap.createScaledBitmap(cropped, 160, 160, true)
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
