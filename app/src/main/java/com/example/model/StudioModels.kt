package com.example.model

import androidx.annotation.DrawableRes

enum class CharacterCategory(val labelEn: String, val labelBn: String) {
    ALL("All", "সব"),
    REALISTIC("Realistic", "বাস্তব"),
    CARTOON("Cartoon", "কার্টুন"),
    ANIMAL("Animal", "প্রাণী"),
    CUSTOM("Custom", "নিজস্ব")
}

enum class RigType(val badgeEn: String, val badgeBn: String) {
    AUTO_MESH("478 Mesh", "মেস ওয়ার্প"),
    MANUAL_PUPPET("2D Puppet", "২ডি পাপেট")
}

enum class VideoResolution(
    val width: Int,
    val height: Int,
    val labelEn: String,
    val labelBn: String
) {
    HD_720P(720, 1280, "720p HD", "৭২০পি HD"),
    FHD_1080P(1080, 1920, "1080p FHD", "১০৮০পি FHD")
}

enum class LanguageMode(val label: String) {
    BILINGUAL("বাংলা + English"),
    BENGALI("শুধু বাংলা"),
    ENGLISH("English Only")
}

data class Point2D(
    val x: Float,
    val y: Float
)

data class NormalizedBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = (right - left).coerceAtLeast(0.04f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.04f)
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f
}

/**
 * Stores the 5 manual anchor points and 3 draggable region boxes for
 * Cartoon, Animal, and Custom manual characters.
 * Coordinates are normalized in [0f..1f] relative to the character image.
 */
data class PuppetConfig(
    val leftEye: Point2D = Point2D(0.36f, 0.40f),
    val rightEye: Point2D = Point2D(0.64f, 0.40f),
    val noseTip: Point2D = Point2D(0.50f, 0.53f),
    val mouthLeft: Point2D = Point2D(0.39f, 0.67f),
    val mouthRight: Point2D = Point2D(0.61f, 0.67f),
    val leftEyeBox: NormalizedBox = NormalizedBox(0.27f, 0.33f, 0.45f, 0.46f),
    val rightEyeBox: NormalizedBox = NormalizedBox(0.55f, 0.33f, 0.73f, 0.46f),
    val mouthBox: NormalizedBox = NormalizedBox(0.35f, 0.60f, 0.65f, 0.75f)
) {
    fun serialize(): String {
        return listOf(
            leftEye.x, leftEye.y,
            rightEye.x, rightEye.y,
            noseTip.x, noseTip.y,
            mouthLeft.x, mouthLeft.y,
            mouthRight.x, mouthRight.y,
            leftEyeBox.left, leftEyeBox.top, leftEyeBox.right, leftEyeBox.bottom,
            rightEyeBox.left, rightEyeBox.top, rightEyeBox.right, rightEyeBox.bottom,
            mouthBox.left, mouthBox.top, mouthBox.right, mouthBox.bottom
        ).joinToString(",") { "%.4f".format(java.util.Locale.US, it) }
    }

    companion object {
        fun deserialize(raw: String?): PuppetConfig {
            if (raw.isNullOrBlank()) return PuppetConfig()
            val parts = raw.split(",").mapNotNull { it.trim().toFloatOrNull() }
            if (parts.size < 22) return PuppetConfig()
            return PuppetConfig(
                leftEye = Point2D(parts[0], parts[1]),
                rightEye = Point2D(parts[2], parts[3]),
                noseTip = Point2D(parts[4], parts[5]),
                mouthLeft = Point2D(parts[6], parts[7]),
                mouthRight = Point2D(parts[8], parts[9]),
                leftEyeBox = NormalizedBox(parts[10], parts[11], parts[12], parts[13]),
                rightEyeBox = NormalizedBox(parts[14], parts[15], parts[16], parts[17]),
                mouthBox = NormalizedBox(parts[18], parts[19], parts[20], parts[21])
            )
        }
    }
}

data class CharacterItem(
    val id: String,
    val nameEn: String,
    val nameBn: String,
    val subtitleEn: String,
    val subtitleBn: String,
    val category: CharacterCategory,
    val rigType: RigType,
    @DrawableRes val drawableRes: Int? = null,
    val customImagePath: String? = null,
    val proceduralPresetId: String? = null,
    val hasTransparency: Boolean = true,
    val puppetConfig: PuppetConfig = PuppetConfig(),
    val meshControlPoints: List<Point2D> = CanonicalMeshTopology.defaultControlPoints,
    val accentHex: Long = 0xFF00E5FF,
    val isCustom: Boolean = false
) {
    fun displayName(mode: LanguageMode): String = when (mode) {
        LanguageMode.BILINGUAL -> "$nameBn • $nameEn"
        LanguageMode.BENGALI -> nameBn
        LanguageMode.ENGLISH -> nameEn
    }

    fun shortLabel(mode: LanguageMode): String = when (mode) {
        LanguageMode.BILINGUAL -> "$nameBn\n$nameEn"
        LanguageMode.BENGALI -> nameBn
        LanguageMode.ENGLISH -> nameEn
    }
}

data class LiveFacePose(
    val isTracking: Boolean = true,
    val landmarkCount: Int = 478,
    val controlPoints: List<Point2D> = CanonicalMeshTopology.defaultControlPoints,
    val denseLandmarks: List<Point2D> = emptyList(),
    val jawOpen: Float = 0.08f,
    val eyeBlinkLeft: Float = 0.0f,
    val eyeBlinkRight: Float = 0.0f,
    val smile: Float = 0.2f,
    val browRaise: Float = 0.1f,
    val yawDegrees: Float = 0f,
    val pitchDegrees: Float = 0f,
    val rollDegrees: Float = 0f,
    val faceCenterX: Float = 0.5f,
    val faceCenterY: Float = 0.47f,
    val faceScale: Float = 0.56f,
    val fps: Int = 30
)

data class StudioSettings(
    val isFrontCamera: Boolean = true,
    val smoothingStrength: Float = 0.65f,
    val blendOpacity: Float = 0.92f,
    val resolution: VideoResolution = VideoResolution.HD_720P,
    val isMirrored: Boolean = true,
    val detailedMouthInterior: Boolean = true,
    val languageMode: LanguageMode = LanguageMode.BILINGUAL,
    val showMeshWireframe: Boolean = false,
    val useDemoMotionFeed: Boolean = false
)

data class StudioCaptureItem(
    val id: Long = 0,
    val filePath: String,
    val mediaStoreUri: String?,
    val isVideo: Boolean,
    val characterName: String,
    val durationMs: Long,
    val createdAt: Long
)

/**
 * Canonical 36-vertex facial mesh control topology with Delaunay triangulation
 * indices covering forehead, eyebrows, eyes, nose bridge, cheeks, upper/lower lips,
 * inner mouth cavity, and chin/jawline.
 */
object CanonicalMeshTopology {
    // 36 canonical control points in normalized [0..1] portrait coordinates
    val defaultControlPoints: List<Point2D> = listOf(
        // 0..4: Forehead arc
        Point2D(0.24f, 0.22f), // 0: Forehead left outer
        Point2D(0.36f, 0.17f), // 1: Forehead left inner
        Point2D(0.50f, 0.15f), // 2: Forehead center
        Point2D(0.64f, 0.17f), // 3: Forehead right inner
        Point2D(0.76f, 0.22f), // 4: Forehead right outer

        // 5..8: Eyebrows
        Point2D(0.27f, 0.32f), // 5: Left brow outer
        Point2D(0.42f, 0.31f), // 6: Left brow inner
        Point2D(0.58f, 0.31f), // 7: Right brow inner
        Point2D(0.73f, 0.32f), // 8: Right brow outer

        // 9..12: Left Eye diamond
        Point2D(0.29f, 0.39f), // 9: Left eye outer
        Point2D(0.36f, 0.36f), // 10: Left eye top
        Point2D(0.43f, 0.39f), // 11: Left eye inner
        Point2D(0.36f, 0.42f), // 12: Left eye bottom

        // 13..16: Right Eye diamond
        Point2D(0.57f, 0.39f), // 13: Right eye inner
        Point2D(0.64f, 0.36f), // 14: Right eye top
        Point2D(0.71f, 0.39f), // 15: Right eye outer
        Point2D(0.64f, 0.42f), // 16: Right eye bottom

        // 17..20: Nose bridge & tip
        Point2D(0.50f, 0.38f), // 17: Nose bridge top
        Point2D(0.50f, 0.51f), // 18: Nose tip
        Point2D(0.42f, 0.54f), // 19: Left nostril
        Point2D(0.58f, 0.54f), // 20: Right nostril

        // 21..24: Cheeks & Temples
        Point2D(0.18f, 0.39f), // 21: Left temple
        Point2D(0.82f, 0.39f), // 22: Right temple
        Point2D(0.21f, 0.56f), // 23: Left cheek outer
        Point2D(0.79f, 0.56f), // 24: Right cheek outer

        // 25..30: Mouth outer & inner lips
        Point2D(0.37f, 0.65f), // 25: Mouth left corner
        Point2D(0.50f, 0.62f), // 26: Upper lip outer top
        Point2D(0.63f, 0.65f), // 27: Mouth right corner
        Point2D(0.50f, 0.69f), // 28: Lower lip outer bottom
        Point2D(0.50f, 0.64f), // 29: Upper lip inner
        Point2D(0.50f, 0.66f), // 30: Lower lip inner

        // 31..35: Jawline & Chin
        Point2D(0.26f, 0.72f), // 31: Jaw left
        Point2D(0.37f, 0.81f), // 32: Chin left
        Point2D(0.50f, 0.85f), // 33: Chin bottom center
        Point2D(0.63f, 0.81f), // 34: Chin right
        Point2D(0.74f, 0.72f)  // 35: Jaw right
    )

    // Non-overlapping Delaunay triangles indexing into the 36 control points
    val triangleIndices: IntArray = intArrayOf(
        // Forehead to brows
        0, 1, 5,
        1, 6, 5,
        1, 2, 6,
        2, 7, 6,
        2, 3, 7,
        3, 8, 7,
        3, 4, 8,
        // Temples
        0, 5, 21,
        4, 22, 8,
        // Brows to eyes & nose bridge
        5, 10, 9,
        5, 6, 10,
        6, 11, 10,
        6, 17, 11,
        6, 7, 17,
        7, 13, 17,
        7, 14, 13,
        7, 8, 14,
        8, 15, 14,
        // Left & right eye lids
        9, 10, 12,
        10, 11, 12,
        13, 14, 16,
        14, 15, 16,
        // Temples to cheeks
        21, 5, 9,
        21, 9, 23,
        8, 22, 15,
        15, 22, 24,
        // Under eyes to nostrils & cheeks
        9, 12, 23,
        12, 19, 23,
        12, 11, 19,
        11, 17, 18,
        11, 18, 19,
        17, 13, 18,
        13, 20, 18,
        13, 16, 20,
        16, 24, 20,
        16, 15, 24,
        // Nose to upper lip
        19, 18, 26,
        18, 20, 26,
        19, 26, 25,
        20, 27, 26,
        // Upper & lower lips
        25, 26, 29,
        26, 27, 29,
        25, 30, 28,
        30, 27, 28,
        // Cheeks to mouth & jaw
        23, 19, 25,
        23, 25, 31,
        20, 24, 27,
        27, 24, 35,
        // Mouth to chin & jaw
        31, 25, 32,
        25, 28, 32,
        28, 33, 32,
        28, 34, 33,
        28, 27, 34,
        27, 35, 34
    )
}
