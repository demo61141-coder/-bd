package com.example.recording

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.media.MediaRecorder
import android.os.Build
import android.view.Surface
import com.example.model.CharacterItem
import com.example.model.LiveFacePose
import com.example.model.VideoResolution
import com.example.rendering.FaceSwapRenderer
import java.io.File

/**
 * Handles high-resolution composite photo snapshots and real-time MP4 video + microphone audio
 * recording of the live camera + warped character mesh canvas.
 */
class StudioMediaRecorder(private val context: Context) {

    private var mediaRecorder: MediaRecorder? = null
    private var recordingSurface: Surface? = null
    private var activeOutputFile: File? = null
    private var recordingStartTimeMs: Long = 0L

    @Volatile
    var isRecording: Boolean = false
        private set

    /**
     * Starts recording video from an input Surface (plus microphone audio if [hasMicPermission] is true)
     * into an MP4 file in `filesDir/captures/`.
     */
    fun startRecording(
        resolution: VideoResolution,
        hasMicPermission: Boolean
    ): Boolean {
        if (isRecording) return true
        val capturesDir = File(context.filesDir, "captures").apply { mkdirs() }
        val outputFile = File(capturesDir, "FACESWAP_VID_${System.currentTimeMillis()}.mp4")

        val started = startRecorderInternal(outputFile, resolution, withAudio = hasMicPermission)
            || (hasMicPermission && startRecorderInternal(outputFile, resolution, withAudio = false))

        if (started) {
            activeOutputFile = outputFile
            recordingStartTimeMs = System.currentTimeMillis()
            isRecording = true
        }
        return started
    }

    private fun startRecorderInternal(
        outputFile: File,
        resolution: VideoResolution,
        withAudio: Boolean
    ): Boolean {
        return runCatching {
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            if (withAudio) {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            }
            recorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            recorder.setOutputFile(outputFile.absolutePath)
            recorder.setVideoEncodingBitRate(6_000_000)
            recorder.setVideoFrameRate(30)
            recorder.setVideoSize(resolution.width, resolution.height)
            recorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            if (withAudio) {
                recorder.setAudioEncodingBitRate(128_000)
                recorder.setAudioSamplingRate(44_100)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            }
            recorder.prepare()
            val surface = recorder.surface
            recorder.start()

            mediaRecorder = recorder
            recordingSurface = surface
            true
        }.onFailure {
            runCatching { mediaRecorder?.release() }
            mediaRecorder = null
            recordingSurface = null
        }.getOrDefault(false)
    }

    /**
     * Draws a composite frame (live camera frame + warped character face + studio watermark badge)
     * onto the active recording Surface if currently recording.
     */
    fun recordCompositeFrame(
        cameraPreviewBitmap: Bitmap?,
        character: CharacterItem,
        characterBitmap: Bitmap,
        pose: LiveFacePose,
        blendOpacity: Float,
        detailedMouthInterior: Boolean,
        showMeshWireframe: Boolean,
        isMirrored: Boolean
    ) {
        if (!isRecording) return
        val surface = recordingSurface ?: return
        if (!surface.isValid) return

        runCatching {
            val hwCanvas = surface.lockCanvas(null) ?: return
            try {
                drawCompositeScene(
                    canvas = hwCanvas,
                    width = hwCanvas.width.toFloat(),
                    height = hwCanvas.height.toFloat(),
                    cameraPreviewBitmap = cameraPreviewBitmap,
                    character = character,
                    characterBitmap = characterBitmap,
                    pose = pose,
                    blendOpacity = blendOpacity,
                    detailedMouthInterior = detailedMouthInterior,
                    showMeshWireframe = showMeshWireframe,
                    isMirrored = isMirrored
                )
            } finally {
                surface.unlockCanvasAndPost(hwCanvas)
            }
        }
    }

    data class CompletedRecording(
        val file: File,
        val durationMs: Long
    )

    fun stopRecording(): CompletedRecording? {
        if (!isRecording) return null
        isRecording = false
        val duration = (System.currentTimeMillis() - recordingStartTimeMs).coerceAtLeast(500L)
        val outFile = activeOutputFile

        runCatching {
            mediaRecorder?.stop()
        }
        runCatching {
            recordingSurface?.release()
        }
        runCatching {
            mediaRecorder?.reset()
            mediaRecorder?.release()
        }
        mediaRecorder = null
        recordingSurface = null
        activeOutputFile = null

        return if (outFile != null && outFile.exists() && outFile.length() > 0L) {
            CompletedRecording(outFile, duration)
        } else {
            null
        }
    }

    /**
     * Renders a high-resolution composite snapshot bitmap for Photo Capture.
     */
    fun renderCompositeSnapshot(
        resolution: VideoResolution,
        cameraPreviewBitmap: Bitmap?,
        character: CharacterItem,
        characterBitmap: Bitmap,
        pose: LiveFacePose,
        blendOpacity: Float,
        detailedMouthInterior: Boolean,
        showMeshWireframe: Boolean,
        isMirrored: Boolean
    ): Bitmap {
        val out = Bitmap.createBitmap(resolution.width, resolution.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        drawCompositeScene(
            canvas = canvas,
            width = resolution.width.toFloat(),
            height = resolution.height.toFloat(),
            cameraPreviewBitmap = cameraPreviewBitmap,
            character = character,
            characterBitmap = characterBitmap,
            pose = pose,
            blendOpacity = blendOpacity,
            detailedMouthInterior = detailedMouthInterior,
            showMeshWireframe = showMeshWireframe,
            isMirrored = isMirrored
        )
        return out
    }

    private fun drawCompositeScene(
        canvas: Canvas,
        width: Float,
        height: Float,
        cameraPreviewBitmap: Bitmap?,
        character: CharacterItem,
        characterBitmap: Bitmap,
        pose: LiveFacePose,
        blendOpacity: Float,
        detailedMouthInterior: Boolean,
        showMeshWireframe: Boolean,
        isMirrored: Boolean
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        if (cameraPreviewBitmap != null && !cameraPreviewBitmap.isRecycled) {
            val scale = maxOf(
                width / cameraPreviewBitmap.width.coerceAtLeast(1),
                height / cameraPreviewBitmap.height.coerceAtLeast(1)
            )
            val scaledW = cameraPreviewBitmap.width * scale
            val scaledH = cameraPreviewBitmap.height * scale
            val dx = (width - scaledW) * 0.5f
            val dy = (height - scaledH) * 0.5f

            canvas.save()
            if (isMirrored) {
                // PreviewView bitmap already matches screen orientation, keep as-is
            }
            canvas.drawBitmap(
                cameraPreviewBitmap,
                null,
                RectF(dx, dy, dx + scaledW, dy + scaledH),
                paint
            )
            canvas.restore()
        } else {
            // Sleek dark creator studio ambient backdrop when camera feed is in simulation mode
            paint.shader = LinearGradient(
                0f, 0f, width, height,
                Color.parseColor("#090A0F"),
                Color.parseColor("#161D2F"),
                Shader.TileMode.CLAMP
            )
            canvas.drawRect(0f, 0f, width, height, paint)
            paint.shader = null
        }

        // Render the live warped character face onto the composite frame
        FaceSwapRenderer.renderWarpedCharacter(
            canvas = canvas,
            viewportWidth = width,
            viewportHeight = height,
            character = character,
            characterBitmap = characterBitmap,
            pose = pose,
            blendOpacity = blendOpacity,
            detailedMouthInterior = detailedMouthInterior,
            showMeshWireframe = showMeshWireframe
        )
    }
}
