package com.example.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.model.CanonicalMeshTopology
import com.example.model.CharacterCategory
import com.example.model.CharacterItem
import com.example.model.LanguageMode
import com.example.model.Point2D
import com.example.model.PuppetConfig
import com.example.model.RigType
import com.example.model.StudioCaptureItem
import com.example.model.StudioSettings
import com.example.model.VideoResolution
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private val Context.studioDataStore by preferencesDataStore(name = "faceswap_studio_prefs")

class StudioRepository(
    private val context: Context,
    private val dao: StudioDao
) {
    private object Keys {
        val IS_FRONT_CAMERA = booleanPreferencesKey("is_front_camera")
        val SMOOTHING_STRENGTH = floatPreferencesKey("smoothing_strength")
        val BLEND_OPACITY = floatPreferencesKey("blend_opacity")
        val RESOLUTION = stringPreferencesKey("video_resolution")
        val IS_MIRRORED = booleanPreferencesKey("is_mirrored")
        val DETAILED_MOUTH = booleanPreferencesKey("detailed_mouth_interior")
        val LANGUAGE_MODE = stringPreferencesKey("language_mode")
        val SHOW_MESH_WIREFRAME = booleanPreferencesKey("show_mesh_wireframe")
        val USE_DEMO_MOTION = booleanPreferencesKey("use_demo_motion")
    }

    val settingsFlow: Flow<StudioSettings> = context.studioDataStore.data.map { prefs ->
        StudioSettings(
            isFrontCamera = prefs[Keys.IS_FRONT_CAMERA] ?: true,
            smoothingStrength = prefs[Keys.SMOOTHING_STRENGTH] ?: 0.65f,
            blendOpacity = prefs[Keys.BLEND_OPACITY] ?: 0.92f,
            resolution = prefs[Keys.RESOLUTION]?.let {
                runCatching { VideoResolution.valueOf(it) }.getOrNull()
            } ?: VideoResolution.HD_720P,
            isMirrored = prefs[Keys.IS_MIRRORED] ?: true,
            detailedMouthInterior = prefs[Keys.DETAILED_MOUTH] ?: true,
            languageMode = prefs[Keys.LANGUAGE_MODE]?.let {
                runCatching { LanguageMode.valueOf(it) }.getOrNull()
            } ?: LanguageMode.BILINGUAL,
            showMeshWireframe = prefs[Keys.SHOW_MESH_WIREFRAME] ?: false,
            useDemoMotionFeed = prefs[Keys.USE_DEMO_MOTION] ?: false
        )
    }

    val customCharactersFlow: Flow<List<CharacterItem>> = dao.observeCustomCharacters().map { list ->
        list.map { entity ->
            val rigType = runCatching { RigType.valueOf(entity.rigTypeName) }.getOrDefault(RigType.MANUAL_PUPPET)
            val category = runCatching { CharacterCategory.valueOf(entity.categoryName) }.getOrDefault(CharacterCategory.CUSTOM)
            val meshPoints = deserializePoints(entity.meshPointsSerialized)
            CharacterItem(
                id = entity.id,
                nameEn = entity.nameEn,
                nameBn = entity.nameBn,
                subtitleEn = if (rigType == RigType.AUTO_MESH) "Custom 478-Mesh" else "Custom 2D Puppet",
                subtitleBn = if (rigType == RigType.AUTO_MESH) "নিজস্ব মেস ফেস" else "নিজস্ব ২ডি পাপেট",
                category = category,
                rigType = rigType,
                customImagePath = entity.imagePath,
                hasTransparency = entity.hasTransparency,
                puppetConfig = PuppetConfig.deserialize(entity.puppetConfigSerialized),
                meshControlPoints = meshPoints,
                accentHex = if (rigType == RigType.AUTO_MESH) 0xFF00E5FF else 0xFFA855F7,
                isCustom = true
            )
        }
    }

    val studioCapturesFlow: Flow<List<StudioCaptureItem>> = dao.observeStudioCaptures().map { list ->
        list.map {
            StudioCaptureItem(
                id = it.id,
                filePath = it.filePath,
                mediaStoreUri = it.mediaStoreUri,
                isVideo = it.isVideo,
                characterName = it.characterName,
                durationMs = it.durationMs,
                createdAt = it.createdAt
            )
        }
    }

    suspend fun updateSettings(transform: (StudioSettings) -> StudioSettings, current: StudioSettings) {
        val next = transform(current)
        context.studioDataStore.edit { prefs ->
            prefs[Keys.IS_FRONT_CAMERA] = next.isFrontCamera
            prefs[Keys.SMOOTHING_STRENGTH] = next.smoothingStrength
            prefs[Keys.BLEND_OPACITY] = next.blendOpacity
            prefs[Keys.RESOLUTION] = next.resolution.name
            prefs[Keys.IS_MIRRORED] = next.isMirrored
            prefs[Keys.DETAILED_MOUTH] = next.detailedMouthInterior
            prefs[Keys.LANGUAGE_MODE] = next.languageMode.name
            prefs[Keys.SHOW_MESH_WIREFRAME] = next.showMeshWireframe
            prefs[Keys.USE_DEMO_MOTION] = next.useDemoMotionFeed
        }
    }

    suspend fun saveCustomCharacter(
        id: String,
        nameEn: String,
        nameBn: String,
        rigType: RigType,
        bitmap: Bitmap,
        puppetConfig: PuppetConfig,
        meshControlPoints: List<Point2D>
    ): CharacterItem = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "characters").apply { mkdirs() }
        val imgFile = File(dir, "$id.png")
        FileOutputStream(imgFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val entity = CustomCharacterEntity(
            id = id,
            nameEn = nameEn,
            nameBn = nameBn,
            categoryName = CharacterCategory.CUSTOM.name,
            rigTypeName = rigType.name,
            imagePath = imgFile.absolutePath,
            hasTransparency = bitmap.hasAlpha(),
            puppetConfigSerialized = puppetConfig.serialize(),
            meshPointsSerialized = serializePoints(meshControlPoints)
        )
        dao.upsertCustomCharacter(entity)
        DemoCharacterCatalog.invalidateCache(id)
        CharacterItem(
            id = id,
            nameEn = nameEn,
            nameBn = nameBn,
            subtitleEn = if (rigType == RigType.AUTO_MESH) "Custom 478-Mesh" else "Custom 2D Puppet",
            subtitleBn = if (rigType == RigType.AUTO_MESH) "নিজস্ব মেস ফেস" else "নিজস্ব ২ডি পাপেট",
            category = CharacterCategory.CUSTOM,
            rigType = rigType,
            customImagePath = imgFile.absolutePath,
            hasTransparency = bitmap.hasAlpha(),
            puppetConfig = puppetConfig,
            meshControlPoints = meshControlPoints,
            accentHex = if (rigType == RigType.AUTO_MESH) 0xFF00E5FF else 0xFFA855F7,
            isCustom = true
        )
    }

    suspend fun updateCharacterPuppetRig(
        item: CharacterItem,
        newConfig: PuppetConfig
    ): CharacterItem = withContext(Dispatchers.IO) {
        if (item.isCustom && !item.customImagePath.isNullOrBlank()) {
            val entity = CustomCharacterEntity(
                id = item.id,
                nameEn = item.nameEn,
                nameBn = item.nameBn,
                categoryName = item.category.name,
                rigTypeName = RigType.MANUAL_PUPPET.name,
                imagePath = item.customImagePath,
                hasTransparency = item.hasTransparency,
                puppetConfigSerialized = newConfig.serialize(),
                meshPointsSerialized = serializePoints(item.meshControlPoints)
            )
            dao.upsertCustomCharacter(entity)
        }
        item.copy(
            rigType = RigType.MANUAL_PUPPET,
            puppetConfig = newConfig
        )
    }

    suspend fun deleteCustomCharacter(id: String) = withContext(Dispatchers.IO) {
        dao.deleteCustomCharacter(id)
        File(File(context.filesDir, "characters"), "$id.png").delete()
        DemoCharacterCatalog.invalidateCache(id)
    }

    suspend fun saveCapturedPhoto(
        bitmap: Bitmap,
        characterName: String
    ): StudioCaptureItem = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val capturesDir = File(context.filesDir, "captures").apply { mkdirs() }
        val localFile = File(capturesDir, "FACESWAP_IMG_$timestamp.jpg")
        FileOutputStream(localFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
        }

        // Also save directly to phone Gallery (MediaStore.Images)
        val mediaUri = exportImageToGallery(localFile, "FaceSwap_$timestamp.jpg")
        val id = dao.insertStudioCapture(
            StudioCaptureEntity(
                filePath = localFile.absolutePath,
                mediaStoreUri = mediaUri?.toString(),
                isVideo = false,
                characterName = characterName,
                durationMs = 0L,
                createdAt = timestamp
            )
        )
        StudioCaptureItem(
            id = id,
            filePath = localFile.absolutePath,
            mediaStoreUri = mediaUri?.toString(),
            isVideo = false,
            characterName = characterName,
            durationMs = 0L,
            createdAt = timestamp
        )
    }

    suspend fun saveRecordedVideo(
        videoFile: File,
        characterName: String,
        durationMs: Long
    ): StudioCaptureItem = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val mediaUri = exportVideoToGallery(videoFile, "FaceSwap_VID_$timestamp.mp4")
        val id = dao.insertStudioCapture(
            StudioCaptureEntity(
                filePath = videoFile.absolutePath,
                mediaStoreUri = mediaUri?.toString(),
                isVideo = true,
                characterName = characterName,
                durationMs = durationMs,
                createdAt = timestamp
            )
        )
        StudioCaptureItem(
            id = id,
            filePath = videoFile.absolutePath,
            mediaStoreUri = mediaUri?.toString(),
            isVideo = true,
            characterName = characterName,
            durationMs = durationMs,
            createdAt = timestamp
        )
    }

    suspend fun deleteStudioCapture(item: StudioCaptureItem) = withContext(Dispatchers.IO) {
        dao.deleteStudioCapture(item.id)
        runCatching { File(item.filePath).delete() }
    }

    private fun exportImageToGallery(sourceFile: File, displayName: String): Uri? {
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/FaceSwapCam")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
    }

    private fun exportVideoToGallery(sourceFile: File, displayName: String): Uri? {
        return runCatching {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_DCIM + "/FaceSwapCam")
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                FileInputStream(sourceFile).use { input -> input.copyTo(out) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        }.getOrNull()
    }

    private fun serializePoints(points: List<Point2D>): String {
        return points.joinToString(";") { "%.4f,%.4f".format(java.util.Locale.US, it.x, it.y) }
    }

    private fun deserializePoints(raw: String?): List<Point2D> {
        if (raw.isNullOrBlank()) return CanonicalMeshTopology.defaultControlPoints
        val parsed = raw.split(";").mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size == 2) {
                val x = parts[0].toFloatOrNull()
                val y = parts[1].toFloatOrNull()
                if (x != null && y != null) Point2D(x, y) else null
            } else null
        }
        return if (parsed.size == CanonicalMeshTopology.defaultControlPoints.size) {
            parsed
        } else {
            CanonicalMeshTopology.defaultControlPoints
        }
    }
}
