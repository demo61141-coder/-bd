package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.CharacterCategory
import com.example.ui.components.BottomCreatorDock
import com.example.ui.components.CreatorGuideSheet
import com.example.ui.components.FaceSwapCameraViewport
import com.example.ui.components.ManualSetupSheet
import com.example.ui.components.SettingsSheet
import com.example.ui.components.StudioGallerySheet
import com.example.ui.components.TopStudioHud
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceGlass
import com.example.ui.theme.TextAlabaster
import com.example.viewmodel.ActiveModalSheet
import com.example.viewmodel.FaceSwapViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                FaceSwapStudioScreen()
            }
        }
    }
}

@Composable
fun FaceSwapStudioScreen(
    viewModel: FaceSwapViewModel = viewModel()
) {
    val context = LocalContext.current

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val allCharacters by viewModel.allCharacters.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val selectedCharacterId by viewModel.selectedCharacterId.collectAsStateWithLifecycle()
    val livePose by viewModel.livePose.collectAsStateWithLifecycle()
    val isModelLoading by viewModel.isModelLoading.collectAsStateWithLifecycle()
    val activeSheet by viewModel.activeSheet.collectAsStateWithLifecycle()
    val manualSetupDraft by viewModel.manualSetupDraft.collectAsStateWithLifecycle()
    val isRecording by viewModel.isRecording.collectAsStateWithLifecycle()
    val recordingElapsedSec by viewModel.recordingElapsedSec.collectAsStateWithLifecycle()
    val photoFlashActive by viewModel.photoFlashActive.collectAsStateWithLifecycle()
    val studioCaptures by viewModel.studioCaptures.collectAsStateWithLifecycle()
    val toastBanner by viewModel.toastBanner.collectAsStateWithLifecycle()

    val filteredCharacters = remember(allCharacters, selectedCategory) {
        if (selectedCategory == CharacterCategory.ALL) {
            allCharacters
        } else {
            allCharacters.filter { it.category == selectedCategory }
        }
    }

    val activeCharacter = remember(allCharacters, selectedCharacterId) {
        allCharacters.find { it.id == selectedCharacterId } ?: allCharacters.first()
    }

    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }

    // Camera, Microphone, and Legacy Storage runtime permission state
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var hasMicPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        hasMicPermission = result[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
    }

    fun requestStudioPermissions() {
        val perms = buildList {
            add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
        permissionLauncher.launch(perms)
    }

    // Request Camera & Microphone permissions on launch
    LaunchedEffect(Unit) {
        if (!hasCameraPermission || !hasMicPermission) {
            requestStudioPermissions()
        }
    }

    // Zero-permission Android Photo Picker for custom character gallery uploads
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.handleUploadedGalleryUri(uri)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
    ) {
        // 1. Full-Screen Camera + Real-Time Warped Face Mesh / 2D Puppet Viewport
        FaceSwapCameraViewport(
            hasCameraPermission = hasCameraPermission,
            isModelLoading = isModelLoading,
            activeCharacter = activeCharacter,
            livePose = livePose,
            settings = settings,
            faceTrackerEngine = viewModel.faceTrackerEngine,
            mediaRecorder = viewModel.mediaRecorder,
            isRecording = isRecording,
            onPreviewViewReady = { previewViewRef = it },
            onRequestPermissions = ::requestStudioPermissions,
            onEnableDemoPreview = { viewModel.setUseDemoMotionFeed(true) }
        )

        // 2. Top Studio HUD
        TopStudioHud(
            activeCharacter = activeCharacter,
            livePose = livePose,
            languageMode = settings.languageMode,
            capturesCount = studioCaptures.size,
            isRecording = isRecording,
            recordingElapsedSec = recordingElapsedSec,
            onFlipCamera = viewModel::toggleCameraLens,
            onOpenStudioGallery = { viewModel.openSheet(ActiveModalSheet.STUDIO_GALLERY) },
            onOpenGuide = { viewModel.openSheet(ActiveModalSheet.GUIDE) },
            onOpenSettings = { viewModel.openSheet(ActiveModalSheet.SETTINGS) },
            modifier = Modifier.align(Alignment.TopCenter)
        )

        // 3. Bottom Creator Dock (Category Chips + 12 Demo & Custom Carousel + Photo/Video Shutter)
        BottomCreatorDock(
            categories = CharacterCategory.entries,
            selectedCategory = selectedCategory,
            characters = filteredCharacters,
            selectedCharacter = activeCharacter,
            languageMode = settings.languageMode,
            isRecording = isRecording,
            onSelectCategory = viewModel::selectCategory,
            onSelectCharacter = viewModel::selectCharacter,
            onUploadFromGallery = {
                photoPickerLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                )
            },
            onOpenManualRigEditor = viewModel::openManualSetupForCharacter,
            onCapturePhoto = {
                val previewBmp = runCatching { previewViewRef?.bitmap }.getOrNull()
                viewModel.capturePhoto(previewBmp, activeCharacter)
            },
            onToggleRecordVideo = {
                viewModel.toggleVideoRecording(
                    hasMicPermission = hasMicPermission,
                    activeCharacter = activeCharacter
                )
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // 4. White Flash Effect on Photo Capture
        AnimatedVisibility(
            visible = photoFlashActive,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.72f))
            )
        }

        // 5. Floating Bilingual Status Toast Banner
        AnimatedVisibility(
            visible = toastBanner != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 98.dp, start = 16.dp, end = 16.dp)
        ) {
            toastBanner?.let { msg ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(StudioSurfaceGlass)
                        .border(1.dp, ElectricCyan, RoundedCornerShape(16.dp))
                        .clickable { viewModel.dismissToast() }
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.labelMedium,
                        color = TextAlabaster,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // 6. Modal Bottom Sheets (Settings, Creator Guide, Studio Gallery)
        if (activeSheet != ActiveModalSheet.NONE) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f))
                    .clickable { viewModel.closeSheet() },
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = false) {}
                ) {
                    when (activeSheet) {
                        ActiveModalSheet.SETTINGS -> {
                            SettingsSheet(
                                settings = settings,
                                onFlipCamera = viewModel::toggleCameraLens,
                                onSmoothingChanged = viewModel::setSmoothingStrength,
                                onBlendOpacityChanged = viewModel::setBlendOpacity,
                                onResolutionChanged = viewModel::setResolution,
                                onMirrorChanged = viewModel::setMirrored,
                                onDetailedMouthChanged = viewModel::setDetailedMouthInterior,
                                onShowWireframeChanged = viewModel::setShowMeshWireframe,
                                onUseDemoMotionChanged = viewModel::setUseDemoMotionFeed,
                                onLanguageModeChanged = viewModel::setLanguageMode,
                                onClose = viewModel::closeSheet
                            )
                        }
                        ActiveModalSheet.GUIDE -> {
                            CreatorGuideSheet(
                                onClose = viewModel::closeSheet
                            )
                        }
                        ActiveModalSheet.STUDIO_GALLERY -> {
                            StudioGallerySheet(
                                captures = studioCaptures,
                                customCharacters = allCharacters.filter { it.isCustom },
                                onDeleteCapture = viewModel::deleteCapture,
                                onDeleteCustomCharacter = viewModel::deleteCustomCharacter,
                                onEditCustomCharacter = viewModel::openManualSetupForCharacter,
                                onClose = viewModel::closeSheet
                            )
                        }
                        ActiveModalSheet.NONE -> Unit
                    }
                }
            }
        }

        // 7. Full-Screen Manual Setup Editor for Cartoon, Animal & Custom Characters
        manualSetupDraft?.let { draft ->
            ManualSetupSheet(
                draft = draft,
                detailedMouthInterior = settings.detailedMouthInterior,
                onCancel = viewModel::cancelManualSetup,
                onSave = { nameEn, nameBn, updatedConfig ->
                    viewModel.confirmManualSetup(draft, nameEn, nameBn, updatedConfig)
                }
            )
        }
    }
}
