package com.example.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DemoCharacterCatalog
import com.example.data.StudioDatabase
import com.example.data.StudioRepository
import com.example.model.CanonicalMeshTopology
import com.example.model.CharacterCategory
import com.example.model.CharacterItem
import com.example.model.LanguageMode
import com.example.model.LiveFacePose
import com.example.model.PuppetConfig
import com.example.model.RigType
import com.example.model.StudioCaptureItem
import com.example.model.StudioSettings
import com.example.model.VideoResolution
import com.example.recording.StudioMediaRecorder
import com.example.tracking.FaceTrackerEngine
import com.example.tracking.OneEuroFilterBank
import com.example.tracking.StaticFaceDetectionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ActiveModalSheet {
    NONE,
    SETTINGS,
    GUIDE,
    STUDIO_GALLERY
}

data class ManualSetupDraft(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val bitmap: Bitmap,
    val puppetConfig: PuppetConfig,
    val bannerMessageBnEn: String,
    val isExistingCharacter: Boolean = false
)

class FaceSwapViewModel(application: Application) : AndroidViewModel(application) {

    private val database = StudioDatabase.getInstance(application)
    private val repository = StudioRepository(application, database.studioDao())
    private val filterBank = OneEuroFilterBank()
    val mediaRecorder = StudioMediaRecorder(application)

    val settings: StateFlow<StudioSettings> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudioSettings()
    )

    val studioCaptures: StateFlow<List<StudioCaptureItem>> = repository.studioCapturesFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val builtInRigOverrides = MutableStateFlow<Map<String, PuppetConfig>>(emptyMap())

    val allCharacters: StateFlow<List<CharacterItem>> = combine(
        repository.customCharactersFlow,
        builtInRigOverrides
    ) { customList, overrides ->
        val builtIns = DemoCharacterCatalog.builtInCharacters.map { item ->
            val overrideCfg = overrides[item.id]
            if (overrideCfg != null) item.copy(puppetConfig = overrideCfg) else item
        }
        customList + builtIns
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DemoCharacterCatalog.builtInCharacters
    )

    private val _selectedCategory = MutableStateFlow(CharacterCategory.ALL)
    val selectedCategory: StateFlow<CharacterCategory> = _selectedCategory.asStateFlow()

    private val _selectedCharacterId = MutableStateFlow(DemoCharacterCatalog.builtInCharacters.first().id)
    val selectedCharacterId: StateFlow<String> = _selectedCharacterId.asStateFlow()

    private val _isModelLoading = MutableStateFlow(true)
    val isModelLoading: StateFlow<Boolean> = _isModelLoading.asStateFlow()

    private val _livePose = MutableStateFlow(LiveFacePose())
    val livePose: StateFlow<LiveFacePose> = _livePose.asStateFlow()

    private val _activeSheet = MutableStateFlow(ActiveModalSheet.NONE)
    val activeSheet: StateFlow<ActiveModalSheet> = _activeSheet.asStateFlow()

    private val _manualSetupDraft = MutableStateFlow<ManualSetupDraft?>(null)
    val manualSetupDraft: StateFlow<ManualSetupDraft?> = _manualSetupDraft.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingElapsedSec = MutableStateFlow(0L)
    val recordingElapsedSec: StateFlow<Long> = _recordingElapsedSec.asStateFlow()

    private val _photoFlashActive = MutableStateFlow(false)
    val photoFlashActive: StateFlow<Boolean> = _photoFlashActive.asStateFlow()

    private val _toastBanner = MutableStateFlow<String?>(null)
    val toastBanner: StateFlow<String?> = _toastBanner.asStateFlow()

    private var recordingTimerJob: Job? = null

    val faceTrackerEngine = FaceTrackerEngine(
        onLivePoseDetected = { rawPose ->
            val strength = settings.value.smoothingStrength
            val smoothed = filterBank.smoothPose(rawPose, strength)
            _livePose.value = smoothed
        },
        onModelLoaded = {
            _isModelLoading.value = false
        }
    )

    init {
        // Continuous 30fps expression ticker when camera is not streaming or in emulator demo mode
        viewModelScope.launch {
            delay(900)
            _isModelLoading.value = false
            while (isActive) {
                val now = System.currentTimeMillis()
                faceTrackerEngine.isMirrored = settings.value.isMirrored
                faceTrackerEngine.forceDemoMotion = settings.value.useDemoMotionFeed
                if (settings.value.useDemoMotionFeed) {
                    val synthetic = faceTrackerEngine.synthesizeExpressivePose(now)
                    _livePose.value = filterBank.smoothPose(synthetic, settings.value.smoothingStrength, now)
                }
                delay(33)
            }
        }
    }

    fun selectCategory(category: CharacterCategory) {
        _selectedCategory.value = category
    }

    fun selectCharacter(character: CharacterItem) {
        _selectedCharacterId.value = character.id
    }

    fun openSheet(sheet: ActiveModalSheet) {
        _activeSheet.value = sheet
    }

    fun closeSheet() {
        _activeSheet.value = ActiveModalSheet.NONE
    }

    fun showToast(message: String) {
        viewModelScope.launch {
            _toastBanner.value = message
            delay(3400)
            if (_toastBanner.value == message) {
                _toastBanner.value = null
            }
        }
    }

    fun dismissToast() {
        _toastBanner.value = null
    }

    // ==================== SETTINGS ACTIONS ====================

    fun toggleCameraLens() {
        viewModelScope.launch {
            val current = settings.value
            val nextFront = !current.isFrontCamera
            filterBank.reset()
            repository.updateSettings(
                transform = { it.copy(isFrontCamera = nextFront, isMirrored = nextFront) },
                current = current
            )
        }
    }

    fun setSmoothingStrength(value: Float) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                transform = { it.copy(smoothingStrength = value.coerceIn(0f, 1f)) },
                current = current
            )
        }
    }

    fun setBlendOpacity(value: Float) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                transform = { it.copy(blendOpacity = value.coerceIn(0.1f, 1f)) },
                current = current
            )
        }
    }

    fun setResolution(resolution: VideoResolution) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                transform = { it.copy(resolution = resolution) },
                current = current
            )
        }
    }

    fun setMirrored(mirrored: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                transform = { it.copy(isMirrored = mirrored) },
                current = current
            )
        }
    }

    fun setDetailedMouthInterior(detailed: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                transform = { it.copy(detailedMouthInterior = detailed) },
                current = current
            )
        }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                transform = { it.copy(languageMode = mode) },
                current = current
            )
        }
    }

    fun setShowMeshWireframe(show: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                transform = { it.copy(showMeshWireframe = show) },
                current = current
            )
        }
    }

    fun setUseDemoMotionFeed(enabled: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.updateSettings(
                transform = { it.copy(useDemoMotionFeed = enabled) },
                current = current
            )
        }
    }

    // ==================== GALLERY CHARACTER UPLOAD & MANUAL SETUP ====================

    fun handleUploadedGalleryUri(uri: Uri) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val decoded = withContext(Dispatchers.IO) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                }.getOrNull()
            }

            if (decoded == null) {
                showToast("ছবি লোড করা যায়নি / Failed to decode image")
                return@launch
            }

            val scaled = withContext(Dispatchers.Default) {
                val maxDim = 640
                val ratio = minOf(
                    maxDim.toFloat() / decoded.width.coerceAtLeast(1),
                    maxDim.toFloat() / decoded.height.coerceAtLeast(1),
                    1.0f
                )
                val w = (decoded.width * ratio).toInt().coerceAtLeast(64)
                val h = (decoded.height * ratio).toInt().coerceAtLeast(64)
                Bitmap.createScaledBitmap(decoded, w, h, true)
            }

            showToast("ফেস ল্যান্ডমার্ক খোঁজা হচ্ছে... / Detecting face landmarks...")
            val detection = faceTrackerEngine.detectFaceOnStaticBitmap(scaled)
            val customIndex = (allCharacters.value.count { it.isCustom } + 1)
            val newId = "custom_${System.currentTimeMillis()}"

            when (detection) {
                is StaticFaceDetectionResult.HumanFaceDetected -> {
                    val saved = repository.saveCustomCharacter(
                        id = newId,
                        nameEn = "Custom Face #$customIndex",
                        nameBn = "নিজস্ব ফেস #$customIndex",
                        rigType = RigType.AUTO_MESH,
                        bitmap = scaled,
                        puppetConfig = detection.puppetConfig,
                        meshControlPoints = detection.controlPoints
                    )
                    _selectedCharacterId.value = saved.id
                    showToast("মানুষের মুখ শনাক্ত হয়েছে! ৪৭৮-মেস রেডি / Face landmarks detected & saved!")
                }
                is StaticFaceDetectionResult.NoHumanFaceFound -> {
                    // Automatically open Manual Setup Editor for Cartoon / Animal / Stylized character
                    _manualSetupDraft.value = ManualSetupDraft(
                        id = newId,
                        nameEn = "Puppet Face #$customIndex",
                        nameBn = "কার্টুন/প্রাণী #$customIndex",
                        bitmap = scaled,
                        puppetConfig = PuppetConfig(),
                        bannerMessageBnEn = "মানুষের মুখ পাওয়া যায়নি — কার্টুন বা প্রাণীর জন্য ৫টি পয়েন্ট ও বক্স সেট করুন / No human face detected — place 5 anchor points & eye/mouth boxes for 2D Puppet warp",
                        isExistingCharacter = false
                    )
                }
            }
        }
    }

    fun openManualSetupForCharacter(character: CharacterItem) {
        val context = getApplication<Application>()
        val bmp = DemoCharacterCatalog.getCharacterBitmap(context, character)
        _manualSetupDraft.value = ManualSetupDraft(
            id = character.id,
            nameEn = character.nameEn,
            nameBn = character.nameBn,
            bitmap = bmp,
            puppetConfig = character.puppetConfig,
            bannerMessageBnEn = "৫টি অ্যাঙ্কর পয়েন্ট এবং চোখ ও মুখের রিজিয়ন বক্স ড্র্যাগ করে অ্যাডজাস্ট করুন / Drag the 5 anchor points and eye/mouth boxes to customize the 2D puppet rig",
            isExistingCharacter = true
        )
    }

    fun cancelManualSetup() {
        _manualSetupDraft.value = null
    }

    fun confirmManualSetup(
        draft: ManualSetupDraft,
        updatedNameEn: String,
        updatedNameBn: String,
        updatedConfig: PuppetConfig
    ) {
        viewModelScope.launch {
            val existing = allCharacters.value.find { it.id == draft.id }
            if (existing != null && !existing.isCustom) {
                // Built-in character override
                builtInRigOverrides.value = builtInRigOverrides.value + (existing.id to updatedConfig)
                _selectedCharacterId.value = existing.id
                _manualSetupDraft.value = null
                showToast("পাপেট রিগ আপডেট হয়েছে / 2D Puppet rig updated!")
            } else {
                val saved = repository.saveCustomCharacter(
                    id = draft.id,
                    nameEn = updatedNameEn.ifBlank { draft.nameEn },
                    nameBn = updatedNameBn.ifBlank { draft.nameBn },
                    rigType = RigType.MANUAL_PUPPET,
                    bitmap = draft.bitmap,
                    puppetConfig = updatedConfig,
                    meshControlPoints = CanonicalMeshTopology.defaultControlPoints
                )
                _selectedCharacterId.value = saved.id
                _manualSetupDraft.value = null
                showToast("কার্টুন/প্রাণী পাপেট সেভ হয়েছে! / Custom 2D Puppet saved!")
            }
        }
    }

    fun deleteCustomCharacter(character: CharacterItem) {
        if (!character.isCustom) return
        viewModelScope.launch {
            repository.deleteCustomCharacter(character.id)
            if (_selectedCharacterId.value == character.id) {
                _selectedCharacterId.value = DemoCharacterCatalog.builtInCharacters.first().id
            }
            showToast("ক্যারেক্টার মুছে ফেলা হয়েছে / Character deleted")
        }
    }

    // ==================== PHOTO CAPTURE & VIDEO RECORDING ====================

    fun capturePhoto(
        cameraPreviewBitmap: Bitmap?,
        activeCharacter: CharacterItem
    ) {
        viewModelScope.launch {
            _photoFlashActive.value = true
            val context = getApplication<Application>()
            val currentSettings = settings.value
            val currentPose = livePose.value

            val savedItem = withContext(Dispatchers.Default) {
                val charBmp = DemoCharacterCatalog.getCharacterBitmap(context, activeCharacter)
                val snapshot = mediaRecorder.renderCompositeSnapshot(
                    resolution = currentSettings.resolution,
                    cameraPreviewBitmap = cameraPreviewBitmap,
                    character = activeCharacter,
                    characterBitmap = charBmp,
                    pose = currentPose,
                    blendOpacity = currentSettings.blendOpacity,
                    detailedMouthInterior = currentSettings.detailedMouthInterior,
                    showMeshWireframe = currentSettings.showMeshWireframe,
                    isMirrored = currentSettings.isMirrored
                )
                repository.saveCapturedPhoto(
                    bitmap = snapshot,
                    characterName = activeCharacter.nameEn
                )
            }
            delay(140)
            _photoFlashActive.value = false
            showToast("ছবি গ্যালারি ও স্টুডিওতে সেভ হয়েছে! / Photo saved to Gallery & Studio (${savedItem.characterName})")
        }
    }

    fun toggleVideoRecording(
        hasMicPermission: Boolean,
        activeCharacter: CharacterItem
    ) {
        if (_isRecording.value) {
            stopVideoRecording(activeCharacter)
        } else {
            startVideoRecording(hasMicPermission)
        }
    }

    private fun startVideoRecording(hasMicPermission: Boolean) {
        val started = mediaRecorder.startRecording(
            resolution = settings.value.resolution,
            hasMicPermission = hasMicPermission
        )
        if (!started) {
            showToast("রেকর্ডিং শুরু করা যায়নি / Could not start video recorder")
            return
        }
        _isRecording.value = true
        _recordingElapsedSec.value = 0L
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (isActive && _isRecording.value) {
                delay(1000)
                _recordingElapsedSec.value += 1L
            }
        }
        showToast(
            if (hasMicPermission) {
                "মাইক্রোফোন অডিওসহ ভিডিও রেকর্ড শুরু / Recording video + mic audio..."
            } else {
                "ভিডিও রেকর্ড শুরু হয়েছে / Recording video..."
            }
        )
    }

    private fun stopVideoRecording(activeCharacter: CharacterItem) {
        recordingTimerJob?.cancel()
        _isRecording.value = false
        viewModelScope.launch {
            val completed = withContext(Dispatchers.IO) {
                mediaRecorder.stopRecording()
            }
            if (completed != null) {
                repository.saveRecordedVideo(
                    videoFile = completed.file,
                    characterName = activeCharacter.nameEn,
                    durationMs = completed.durationMs
                )
                showToast("ভিডিও গ্যালারি ও স্টুডিওতে সেভ হয়েছে! / Video saved to Gallery & Studio!")
            } else {
                showToast("রেকর্ডিং শেষ হয়েছে / Recording stopped")
            }
        }
    }

    fun deleteCapture(item: StudioCaptureItem) {
        viewModelScope.launch {
            repository.deleteStudioCapture(item)
            showToast("ফাইল মুছে ফেলা হয়েছে / Capture deleted")
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (mediaRecorder.isRecording) {
            mediaRecorder.stopRecording()
        }
    }
}
