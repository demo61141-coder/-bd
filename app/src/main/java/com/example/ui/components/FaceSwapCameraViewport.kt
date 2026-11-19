package com.example.ui.components

import android.content.Context
import android.graphics.Bitmap
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraFront
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.DemoCharacterCatalog
import com.example.model.CharacterItem
import com.example.model.LiveFacePose
import com.example.model.StudioSettings
import com.example.recording.StudioMediaRecorder
import com.example.rendering.FaceSwapRenderer
import com.example.tracking.FaceTrackerEngine
import com.example.ui.theme.CineRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceGlass
import com.example.ui.theme.TextAlabaster
import com.example.ui.theme.TextSlate
import java.util.concurrent.Executors

@Composable
fun FaceSwapCameraViewport(
    hasCameraPermission: Boolean,
    isModelLoading: Boolean,
    activeCharacter: CharacterItem,
    livePose: LiveFacePose,
    settings: StudioSettings,
    faceTrackerEngine: FaceTrackerEngine,
    mediaRecorder: StudioMediaRecorder,
    isRecording: Boolean,
    onPreviewViewReady: (PreviewView) -> Unit,
    onRequestPermissions: () -> Unit,
    onEnableDemoPreview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    val characterBitmap: Bitmap = remember(activeCharacter.id, activeCharacter.customImagePath) {
        DemoCharacterCatalog.getCharacterBitmap(context, activeCharacter)
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    // Bind CameraX Preview + ImageAnalysis when camera permission is granted and not in demo-only mode
    LaunchedEffect(hasCameraPermission, settings.isFrontCamera) {
        val pv = previewViewRef ?: return@LaunchedEffect
        if (!hasCameraPermission) return@LaunchedEffect

        bindCameraUseCases(
            context = context,
            lifecycleOwner = lifecycleOwner,
            previewView = pv,
            isFrontCamera = settings.isFrontCamera,
            analyzer = faceTrackerEngine,
            executor = analysisExecutor
        )
    }

    // Feed composite frames into MediaRecorder at a steady 30fps when video recording is active
    val currentPoseState = androidx.compose.runtime.rememberUpdatedState(livePose)
    val currentCharState = androidx.compose.runtime.rememberUpdatedState(activeCharacter)
    val currentCharBmpState = androidx.compose.runtime.rememberUpdatedState(characterBitmap)
    val currentSettingsState = androidx.compose.runtime.rememberUpdatedState(settings)

    LaunchedEffect(isRecording) {
        while (isRecording) {
            val previewBmp = runCatching { previewViewRef?.bitmap }.getOrNull()
            val s = currentSettingsState.value
            mediaRecorder.recordCompositeFrame(
                cameraPreviewBitmap = previewBmp,
                character = currentCharState.value,
                characterBitmap = currentCharBmpState.value,
                pose = currentPoseState.value,
                blendOpacity = s.blendOpacity,
                detailedMouthInterior = s.detailedMouthInterior,
                showMeshWireframe = s.showMeshWireframe,
                isMirrored = s.isMirrored
            )
            kotlinx.coroutines.delay(33L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .testTag("camera_viewport")
    ) {
        if (hasCameraPermission) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        previewViewRef = this
                        onPreviewViewReady(this)
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        // Mirror rear camera if user explicitly toggled mirror on rear camera
                        scaleX = if (!settings.isFrontCamera && settings.isMirrored) -1f else 1f
                    }
            )
        } else {
            // Ambient Studio Grid Backdrop when in Demo Preview or awaiting permission
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFF17223B), StudioObsidian),
                        center = Offset(size.width * 0.5f, size.height * 0.45f),
                        radius = size.maxDimension * 0.7f
                    )
                )
            }
        }

        // Real-time Hardware-Accelerated Warped Character Face Mesh / 2D Puppet Overlay Canvas
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .testTag("warped_face_canvas")
        ) {
            if (size.height > 1f) {
                faceTrackerEngine.viewportAspectRatio = size.width / size.height
            }
            FaceSwapRenderer.renderWarpedCharacter(
                canvas = drawContext.canvas.nativeCanvas,
                viewportWidth = size.width,
                viewportHeight = size.height,
                character = activeCharacter,
                characterBitmap = characterBitmap,
                pose = livePose,
                blendOpacity = settings.blendOpacity,
                detailedMouthInterior = settings.detailedMouthInterior,
                showMeshWireframe = settings.showMeshWireframe
            )
        }

        // Low-light / No-face-yet Guidance Pill when Camera is active but no face is currently locked
        if (hasCameraPermission && !isModelLoading && !livePose.isTracking && !settings.useDemoMotionFeed) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 104.dp, start = 20.dp, end = 20.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(StudioSurfaceGlass)
                    .border(1.dp, com.example.ui.theme.AmberWarn, RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "মুখ শনাক্ত হচ্ছে না — ক্যামেরার দিকে সোজা তাকান ও মুখে পর্যাপ্ত আলো রাখুন",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextAlabaster,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Face not detected yet — face the camera in clear front lighting",
                        style = MaterialTheme.typography.labelSmall,
                        color = com.example.ui.theme.AmberWarn,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // Loading HUD Card while Face Landmarker initializes
        if (isModelLoading) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(20.dp))
                    .background(StudioSurfaceGlass)
                    .border(1.dp, ElectricCyan, RoundedCornerShape(20.dp))
                    .padding(horizontal = 24.dp, vertical = 20.dp)
                    .testTag("model_loading_indicator"),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = ElectricCyan,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "৪৭৮ ফেস ল্যান্ডমার্ক মডেল লোড হচ্ছে...",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextAlabaster,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Loading 478 Face Landmarker + Blendshapes (GPU)...",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }
        }

        // Permission Denied / Request Card if Camera permission is not granted and user hasn't dismissed into Demo Mode
        if (!hasCameraPermission && !settings.useDemoMotionFeed) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 24.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioCard.copy(alpha = 0.95f))
                    .border(1.5.dp, ElectricCyan.copy(alpha = 0.7f), RoundedCornerShape(24.dp))
                    .padding(22.dp)
                    .testTag("permission_denied_card")
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Icon(
                            imageVector = Icons.Default.CameraFront,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(32.dp)
                        )
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = CineRed,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "ক্যামেরা, মাইক্রোফোন ও স্টোরেজ অনুমতি প্রয়োজন\nCamera, Mic & Storage Access",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextAlabaster,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "রিয়েল-টাইম ফেস সোয়াপ, মাইক্রোফোন অডিওসহ ভিডিও রেকর্ড এবং ফোনের গ্যালারিতে সেভ করার জন্য অনুমতি প্রদান করুন।\nGrant Camera & Microphone permissions to swap faces live on camera and record video with audio.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSlate,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ElectricCyan,
                            contentColor = StudioObsidian
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .width(240.dp)
                            .testTag("grant_permissions_button")
                    ) {
                        Text(
                            text = "অনুমতি দিন / Grant Access",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onEnableDemoPreview,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .width(240.dp)
                            .testTag("try_demo_mode_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = ElectricCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ডেমো প্রিভিউ / Live Demo Mode",
                            color = TextAlabaster
                        )
                    }
                }
            }
        }
    }
}

private fun bindCameraUseCases(
    context: Context,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    previewView: PreviewView,
    isFrontCamera: Boolean,
    analyzer: FaceTrackerEngine,
    executor: java.util.concurrent.Executor
) {
    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
    cameraProviderFuture.addListener({
        runCatching {
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(executor, analyzer)
                }

            val selector = if (isFrontCamera && cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }

            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageAnalysis
            )
            // Enforce 0% digital zoom (widest natural camera FOV like the phone's default camera app)
            camera.cameraControl.setLinearZoom(0f)
        }
    }, ContextCompat.getMainExecutor(context))
}
