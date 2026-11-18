package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import com.example.R
import com.example.model.CanonicalMeshTopology
import com.example.model.CharacterCategory
import com.example.model.CharacterItem
import com.example.model.NormalizedBox
import com.example.model.Point2D
import com.example.model.PuppetConfig
import com.example.model.RigType
import java.io.File

object DemoCharacterCatalog {

    val builtInCharacters: List<CharacterItem> = listOf(
        // ==================== 6 REALISTIC CHARACTERS ====================
        CharacterItem(
            id = "real_cyber",
            nameEn = "Cyber Creator",
            nameBn = "সাইবার ক্রিয়েটর",
            subtitleEn = "478-Mesh • Neon Circuit",
            subtitleBn = "৪৭৮-মেস • নিয়ন লুক",
            category = CharacterCategory.REALISTIC,
            rigType = RigType.AUTO_MESH,
            drawableRes = R.drawable.img_char_cyber_creator_1795018121537,
            accentHex = 0xFF00E5FF
        ),
        CharacterItem(
            id = "real_maharaja",
            nameEn = "Royal Maharaja",
            nameBn = "রাজকীয় মহারাজা",
            subtitleEn = "478-Mesh • Heritage Gold",
            subtitleBn = "৪৭৮-মেস • রাজকীয় পাগড়ি",
            category = CharacterCategory.REALISTIC,
            rigType = RigType.AUTO_MESH,
            drawableRes = R.drawable.img_char_maharaja_1795018120945,
            accentHex = 0xFFF59E0B
        ),
        CharacterItem(
            id = "real_neon_pop",
            nameEn = "Neon Pop Star",
            nameBn = "নিয়ন পপ স্টার",
            subtitleEn = "478-Mesh • Stage Glam",
            subtitleBn = "৪৭৮-মেস • স্টেজ গ্ল্যাম",
            category = CharacterCategory.REALISTIC,
            rigType = RigType.AUTO_MESH,
            proceduralPresetId = "preset_neon_pop",
            accentHex = 0xFFEC4899
        ),
        CharacterItem(
            id = "real_filmmaker",
            nameEn = "Vintage Director",
            nameBn = "ভিন্টেজ ডিরেক্টর",
            subtitleEn = "478-Mesh • Warm Cinema",
            subtitleBn = "৪৭৮-মেস • সিনেমা লুক",
            category = CharacterCategory.REALISTIC,
            rigType = RigType.AUTO_MESH,
            proceduralPresetId = "preset_filmmaker",
            accentHex = 0xFFD97706
        ),
        CharacterItem(
            id = "real_detective",
            nameEn = "Classic Detective",
            nameBn = "ক্লাসিক গোয়েন্দা",
            subtitleEn = "478-Mesh • Noir Studio",
            subtitleBn = "৪৭৮-মেস • রহস্যময় লুক",
            category = CharacterCategory.REALISTIC,
            rigType = RigType.AUTO_MESH,
            proceduralPresetId = "preset_detective",
            accentHex = 0xFF38BDF8
        ),
        CharacterItem(
            id = "real_warrior",
            nameEn = "Mystic Warrior",
            nameBn = "মিস্টিক যোদ্ধা",
            subtitleEn = "478-Mesh • Tribal Gold",
            subtitleBn = "৪৭৮-মেস • যোদ্ধা ফেস",
            category = CharacterCategory.REALISTIC,
            rigType = RigType.AUTO_MESH,
            proceduralPresetId = "preset_warrior",
            accentHex = 0xFF10B981
        ),

        // ==================== 3 CARTOON CHARACTERS ====================
        CharacterItem(
            id = "toon_anime_hero",
            nameEn = "Anime Hero",
            nameBn = "অ্যানিমে হিরো",
            subtitleEn = "2D Puppet • Cel Shaded",
            subtitleBn = "২ডি পাপেট • অ্যানিমে",
            category = CharacterCategory.CARTOON,
            rigType = RigType.MANUAL_PUPPET,
            drawableRes = R.drawable.img_char_anime_hero_1795018125445,
            puppetConfig = PuppetConfig(
                leftEye = Point2D(0.38f, 0.48f),
                rightEye = Point2D(0.62f, 0.48f),
                noseTip = Point2D(0.50f, 0.59f),
                mouthLeft = Point2D(0.42f, 0.68f),
                mouthRight = Point2D(0.58f, 0.68f),
                leftEyeBox = NormalizedBox(0.30f, 0.42f, 0.46f, 0.53f),
                rightEyeBox = NormalizedBox(0.54f, 0.42f, 0.70f, 0.53f),
                mouthBox = NormalizedBox(0.39f, 0.64f, 0.61f, 0.74f)
            ),
            accentHex = 0xFF3B82F6
        ),
        CharacterItem(
            id = "toon_bot",
            nameEn = "Toon CyberBot",
            nameBn = "টুন রোবট",
            subtitleEn = "2D Puppet • Transparent PNG",
            subtitleBn = "২ডি পাপেট • ট্রান্সপারেন্ট PNG",
            category = CharacterCategory.CARTOON,
            rigType = RigType.MANUAL_PUPPET,
            proceduralPresetId = "preset_toon_bot",
            puppetConfig = PuppetConfig(
                leftEye = Point2D(0.35f, 0.41f),
                rightEye = Point2D(0.65f, 0.41f),
                noseTip = Point2D(0.50f, 0.52f),
                mouthLeft = Point2D(0.36f, 0.67f),
                mouthRight = Point2D(0.64f, 0.67f),
                leftEyeBox = NormalizedBox(0.26f, 0.34f, 0.45f, 0.48f),
                rightEyeBox = NormalizedBox(0.55f, 0.34f, 0.74f, 0.48f),
                mouthBox = NormalizedBox(0.33f, 0.60f, 0.67f, 0.75f)
            ),
            accentHex = 0xFF00E5FF
        ),
        CharacterItem(
            id = "toon_villain",
            nameEn = "Comic Villain",
            nameBn = "কমিক ভিলেন",
            subtitleEn = "2D Puppet • Expressive Rig",
            subtitleBn = "২ডি পাপেট • কমিক স্টাইল",
            category = CharacterCategory.CARTOON,
            rigType = RigType.MANUAL_PUPPET,
            proceduralPresetId = "preset_toon_villain",
            puppetConfig = PuppetConfig(
                leftEye = Point2D(0.36f, 0.40f),
                rightEye = Point2D(0.64f, 0.40f),
                noseTip = Point2D(0.50f, 0.53f),
                mouthLeft = Point2D(0.37f, 0.67f),
                mouthRight = Point2D(0.63f, 0.67f),
                leftEyeBox = NormalizedBox(0.27f, 0.33f, 0.45f, 0.46f),
                rightEyeBox = NormalizedBox(0.55f, 0.33f, 0.73f, 0.46f),
                mouthBox = NormalizedBox(0.34f, 0.60f, 0.66f, 0.75f)
            ),
            accentHex = 0xFFA855F7
        ),

        // ==================== 3 ANIMAL CHARACTERS ====================
        CharacterItem(
            id = "animal_tiger",
            nameEn = "Bengal Tiger",
            nameBn = "রয়েল বেঙ্গল টাইগার",
            subtitleEn = "2D Puppet • Pride of Bengal",
            subtitleBn = "২ডি পাপেট • সুন্দরবনের বাঘ",
            category = CharacterCategory.ANIMAL,
            rigType = RigType.MANUAL_PUPPET,
            drawableRes = R.drawable.img_char_bengal_tiger_1795018121422,
            puppetConfig = PuppetConfig(
                leftEye = Point2D(0.36f, 0.40f),
                rightEye = Point2D(0.64f, 0.40f),
                noseTip = Point2D(0.50f, 0.62f),
                mouthLeft = Point2D(0.40f, 0.74f),
                mouthRight = Point2D(0.60f, 0.74f),
                leftEyeBox = NormalizedBox(0.28f, 0.34f, 0.44f, 0.46f),
                rightEyeBox = NormalizedBox(0.56f, 0.34f, 0.72f, 0.46f),
                mouthBox = NormalizedBox(0.36f, 0.68f, 0.64f, 0.81f)
            ),
            accentHex = 0xFFF97316
        ),
        CharacterItem(
            id = "animal_cyber_cat",
            nameEn = "Cyber Cat",
            nameBn = "সাইবার বিড়াল",
            subtitleEn = "2D Puppet • Neon Whiskers",
            subtitleBn = "২ডি পাপেট • নিয়ন বিড়াল",
            category = CharacterCategory.ANIMAL,
            rigType = RigType.MANUAL_PUPPET,
            proceduralPresetId = "preset_cyber_cat",
            puppetConfig = PuppetConfig(
                leftEye = Point2D(0.35f, 0.43f),
                rightEye = Point2D(0.65f, 0.43f),
                noseTip = Point2D(0.50f, 0.56f),
                mouthLeft = Point2D(0.40f, 0.67f),
                mouthRight = Point2D(0.60f, 0.67f),
                leftEyeBox = NormalizedBox(0.26f, 0.35f, 0.44f, 0.50f),
                rightEyeBox = NormalizedBox(0.56f, 0.35f, 0.74f, 0.50f),
                mouthBox = NormalizedBox(0.36f, 0.61f, 0.64f, 0.74f)
            ),
            accentHex = 0xFF06B6D4
        ),
        CharacterItem(
            id = "animal_panda",
            nameEn = "Golden Panda",
            nameBn = "গোল্ডেন পান্ডা",
            subtitleEn = "2D Puppet • Bamboo Mascot",
            subtitleBn = "২ডি পাপেট • পান্ডা মাসকট",
            category = CharacterCategory.ANIMAL,
            rigType = RigType.MANUAL_PUPPET,
            proceduralPresetId = "preset_panda",
            puppetConfig = PuppetConfig(
                leftEye = Point2D(0.35f, 0.43f),
                rightEye = Point2D(0.65f, 0.43f),
                noseTip = Point2D(0.50f, 0.56f),
                mouthLeft = Point2D(0.40f, 0.67f),
                mouthRight = Point2D(0.60f, 0.67f),
                leftEyeBox = NormalizedBox(0.25f, 0.34f, 0.45f, 0.51f),
                rightEyeBox = NormalizedBox(0.55f, 0.34f, 0.75f, 0.51f),
                mouthBox = NormalizedBox(0.36f, 0.61f, 0.64f, 0.74f)
            ),
            accentHex = 0xFF22C55E
        )
    )

    private val bitmapCache = mutableMapOf<String, Bitmap>()

    /**
     * Loads or generates an ARGB_8888 Bitmap with transparent cutout edges
     * for any built-in or custom CharacterItem.
     */
    @Synchronized
    fun getCharacterBitmap(context: Context, item: CharacterItem): Bitmap {
        val cacheKey = "${item.id}_${item.customImagePath.orEmpty()}"
        bitmapCache[cacheKey]?.let { if (!it.isRecycled) return it }

        val bmp: Bitmap = when {
            !item.customImagePath.isNullOrBlank() -> {
                val file = File(item.customImagePath)
                if (file.exists()) {
                    BitmapFactory.decodeFile(file.absolutePath)?.let { decoded ->
                        scaleToSquare(decoded, 512)
                    } ?: createProceduralPortrait("preset_toon_bot", 512)
                } else {
                    createProceduralPortrait("preset_toon_bot", 512)
                }
            }
            item.drawableRes != null -> {
                val raw = BitmapFactory.decodeResource(context.resources, item.drawableRes)
                if (raw != null) {
                    applySoftFaceAlphaMask(scaleToSquare(raw, 512), item.category)
                } else {
                    createProceduralPortrait("preset_neon_pop", 512)
                }
            }
            item.proceduralPresetId != null -> {
                createProceduralPortrait(item.proceduralPresetId, 512)
            }
            else -> createProceduralPortrait("preset_neon_pop", 512)
        }

        bitmapCache[cacheKey] = bmp
        return bmp
    }

    fun invalidateCache(characterId: String) {
        val keys = bitmapCache.keys.filter { it.startsWith(characterId) }
        keys.forEach { bitmapCache.remove(it) }
    }

    private fun scaleToSquare(src: Bitmap, targetSize: Int): Bitmap {
        val out = Bitmap.createBitmap(targetSize, targetSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val scale = maxOf(
            targetSize.toFloat() / src.width.coerceAtLeast(1),
            targetSize.toFloat() / src.height.coerceAtLeast(1)
        )
        val w = src.width * scale
        val h = src.height * scale
        val left = (targetSize - w) * 0.5f
        val top = (targetSize - h) * 0.5f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, null, RectF(left, top, left + w, top + h), paint)
        return out
    }

    /**
     * Applies a feathered elliptical face/head mask to raster portraits so the character
     * blends seamlessly over the live camera feed without hard rectangular corners.
     */
    private fun applySoftFaceAlphaMask(src: Bitmap, category: CharacterCategory): Bitmap {
        val size = src.width
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        canvas.drawBitmap(src, 0f, 0f, paint)

        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            val rx = if (category == CharacterCategory.REALISTIC) size * 0.42f else size * 0.47f
            shader = RadialGradient(
                size * 0.5f,
                size * 0.49f,
                rx,
                intArrayOf(
                    Color.WHITE,
                    Color.WHITE,
                    Color.argb(215, 255, 255, 255),
                    Color.TRANSPARENT
                ),
                floatArrayOf(0f, 0.72f, 0.89f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.save()
        canvas.scale(1.0f, 1.12f, size * 0.5f, size * 0.49f)
        canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), maskPaint)
        canvas.restore()
        return out
    }

    /**
     * Generates a crisp, high-resolution transparent-background (ARGB_8888) portrait
     * aligned with the canonical mesh/puppet coordinates.
     */
    private fun createProceduralPortrait(presetId: String, size: Int): Bitmap {
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val s = size.toFloat()

        when (presetId) {
            "preset_toon_bot" -> drawToonBot(canvas, s)
            "preset_toon_villain" -> drawComicVillain(canvas, s)
            "preset_cyber_cat" -> drawCyberCat(canvas, s)
            "preset_panda" -> drawGoldenPanda(canvas, s)
            "preset_neon_pop" -> drawStylizedRealisticFace(
                canvas, s,
                skinTop = Color.parseColor("#F5C6AA"),
                skinBottom = Color.parseColor("#C9886B"),
                hairColor = Color.parseColor("#EC4899"),
                irisColor = Color.parseColor("#00E5FF"),
                lipColor = Color.parseColor("#E11D48"),
                markingColor = Color.parseColor("#F43F5E"),
                styleTag = "NEON_POP"
            )
            "preset_filmmaker" -> drawStylizedRealisticFace(
                canvas, s,
                skinTop = Color.parseColor("#E0A982"),
                skinBottom = Color.parseColor("#9C6343"),
                hairColor = Color.parseColor("#1E1B18"),
                irisColor = Color.parseColor("#78350F"),
                lipColor = Color.parseColor("#9A5B4A"),
                markingColor = Color.parseColor("#F59E0B"),
                styleTag = "DIRECTOR"
            )
            "preset_detective" -> drawStylizedRealisticFace(
                canvas, s,
                skinTop = Color.parseColor("#E8BE9E"),
                skinBottom = Color.parseColor("#A3785B"),
                hairColor = Color.parseColor("#334155"),
                irisColor = Color.parseColor("#38BDF8"),
                lipColor = Color.parseColor("#A26256"),
                markingColor = Color.parseColor("#38BDF8"),
                styleTag = "DETECTIVE"
            )
            else -> drawStylizedRealisticFace(
                canvas, s,
                skinTop = Color.parseColor("#D99B72"),
                skinBottom = Color.parseColor("#8B5232"),
                hairColor = Color.parseColor("#0F172A"),
                irisColor = Color.parseColor("#10B981"),
                lipColor = Color.parseColor("#8F473B"),
                markingColor = Color.parseColor("#FBBF24"),
                styleTag = "WARRIOR"
            )
        }
        return bmp
    }

    private fun drawStylizedRealisticFace(
        canvas: Canvas,
        s: Float,
        skinTop: Int,
        skinBottom: Int,
        hairColor: Int,
        irisColor: Int,
        lipColor: Int,
        markingColor: Int,
        styleTag: String
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Hair crown back layer
        paint.color = hairColor
        canvas.drawOval(RectF(s * 0.16f, s * 0.06f, s * 0.84f, s * 0.56f), paint)

        // Head & jawline shaded oval aligned to CanonicalMeshTopology (0.18..0.82 x, 0.14..0.86 y)
        paint.shader = LinearGradient(
            s * 0.5f, s * 0.14f, s * 0.5f, s * 0.86f,
            skinTop, skinBottom, Shader.TileMode.CLAMP
        )
        val facePath = Path().apply {
            moveTo(s * 0.50f, s * 0.14f)
            cubicTo(s * 0.76f, s * 0.14f, s * 0.81f, s * 0.36f, s * 0.78f, s * 0.58f)
            cubicTo(s * 0.75f, s * 0.74f, s * 0.62f, s * 0.85f, s * 0.50f, s * 0.86f)
            cubicTo(s * 0.38f, s * 0.85f, s * 0.25f, s * 0.74f, s * 0.22f, s * 0.58f)
            cubicTo(s * 0.19f, s * 0.36f, s * 0.24f, s * 0.14f, s * 0.50f, s * 0.14f)
            close()
        }
        canvas.drawPath(facePath, paint)
        paint.shader = null

        // Subtle cheek studio lighting highlights
        paint.shader = RadialGradient(
            s * 0.34f, s * 0.50f, s * 0.14f,
            Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(s * 0.34f, s * 0.50f, s * 0.14f, paint)
        paint.shader = RadialGradient(
            s * 0.66f, s * 0.50f, s * 0.14f,
            Color.argb(70, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawCircle(s * 0.66f, s * 0.50f, s * 0.14f, paint)
        paint.shader = null

        // Style-specific facial detail (beard, warpaint, neon cheek glow, fedora brim)
        when (styleTag) {
            "NEON_POP" -> {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.012f
                paint.color = markingColor
                canvas.drawLine(s * 0.24f, s * 0.48f, s * 0.35f, s * 0.52f, paint)
                canvas.drawLine(s * 0.76f, s * 0.48f, s * 0.65f, s * 0.52f, paint)
                paint.style = Paint.Style.FILL
            }
            "DIRECTOR" -> {
                // Groomed beard & mustache
                paint.color = Color.argb(185, 30, 25, 22)
                val beard = Path().apply {
                    moveTo(s * 0.25f, s * 0.60f)
                    quadTo(s * 0.50f, s * 0.64f, s * 0.75f, s * 0.60f)
                    quadTo(s * 0.70f, s * 0.85f, s * 0.50f, s * 0.86f)
                    quadTo(s * 0.30f, s * 0.85f, s * 0.25f, s * 0.60f)
                    close()
                }
                canvas.drawPath(beard, paint)
            }
            "WARRIOR" -> {
                paint.color = markingColor
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = s * 0.015f
                canvas.drawLine(s * 0.50f, s * 0.19f, s * 0.50f, s * 0.33f, paint)
                canvas.drawLine(s * 0.26f, s * 0.47f, s * 0.38f, s * 0.49f, paint)
                canvas.drawLine(s * 0.74f, s * 0.47f, s * 0.62f, s * 0.49f, paint)
                paint.style = Paint.Style.FILL
            }
            "DETECTIVE" -> {
                // Fedora hat shadow band across top forehead
                paint.color = Color.parseColor("#1E293B")
                canvas.drawRoundRect(RectF(s * 0.14f, s * 0.12f, s * 0.86f, s * 0.22f), s * 0.04f, s * 0.04f, paint)
                paint.color = markingColor
                canvas.drawRect(s * 0.22f, s * 0.19f, s * 0.78f, s * 0.21f, paint)
            }
        }

        // Eyebrows (around y = 0.31..0.32)
        paint.color = Color.parseColor("#1E1B18")
        paint.strokeWidth = s * 0.022f
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        canvas.drawLine(s * 0.27f, s * 0.32f, s * 0.42f, s * 0.31f, paint)
        canvas.drawLine(s * 0.58f, s * 0.31f, s * 0.73f, s * 0.32f, paint)
        paint.style = Paint.Style.FILL

        // Eyes (centers at (0.36, 0.39) and (0.64, 0.39))
        drawDetailedEye(canvas, s * 0.36f, s * 0.39f, s * 0.07f, s * 0.036f, irisColor)
        drawDetailedEye(canvas, s * 0.64f, s * 0.39f, s * 0.07f, s * 0.036f, irisColor)

        // Nose bridge & tip (0.50, 0.38..0.52)
        paint.color = Color.argb(65, 60, 30, 15)
        val noseShadow = Path().apply {
            moveTo(s * 0.48f, s * 0.38f)
            lineTo(s * 0.43f, s * 0.53f)
            lineTo(s * 0.50f, s * 0.55f)
            lineTo(s * 0.57f, s * 0.53f)
            lineTo(s * 0.52f, s * 0.38f)
            close()
        }
        canvas.drawPath(noseShadow, paint)

        // Lips (left 0.37, right 0.63, center y = 0.65)
        paint.color = lipColor
        val upperLip = Path().apply {
            moveTo(s * 0.37f, s * 0.65f)
            quadTo(s * 0.45f, s * 0.61f, s * 0.50f, s * 0.625f)
            quadTo(s * 0.55f, s * 0.61f, s * 0.63f, s * 0.65f)
            quadTo(s * 0.50f, s * 0.655f, s * 0.37f, s * 0.65f)
            close()
        }
        canvas.drawPath(upperLip, paint)

        paint.color = Color.argb(230, Color.red(lipColor), (Color.green(lipColor) * 0.85f).toInt(), (Color.blue(lipColor) * 0.85f).toInt())
        val lowerLip = Path().apply {
            moveTo(s * 0.37f, s * 0.65f)
            quadTo(s * 0.50f, s * 0.655f, s * 0.63f, s * 0.65f)
            quadTo(s * 0.50f, s * 0.705f, s * 0.37f, s * 0.65f)
            close()
        }
        canvas.drawPath(lowerLip, paint)
    }

    private fun drawDetailedEye(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        irisColor: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Sclera
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
        // Iris
        paint.color = irisColor
        canvas.drawCircle(cx, cy, ry * 0.92f, paint)
        // Pupil
        paint.color = Color.parseColor("#090A0F")
        canvas.drawCircle(cx, cy, ry * 0.45f, paint)
        // Catchlight
        paint.color = Color.WHITE
        canvas.drawCircle(cx - ry * 0.28f, cy - ry * 0.28f, ry * 0.22f, paint)
        // Eyeliner outline
        paint.color = Color.parseColor("#0F172A")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = ry * 0.22f
        canvas.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), paint)
    }

    private fun drawToonBot(canvas: Canvas, s: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Side bolts/ears
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawRoundRect(RectF(s * 0.10f, s * 0.36f, s * 0.20f, s * 0.52f), s * 0.03f, s * 0.03f, paint)
        canvas.drawRoundRect(RectF(s * 0.80f, s * 0.36f, s * 0.90f, s * 0.52f), s * 0.03f, s * 0.03f, paint)
        // Top antenna
        canvas.drawRect(s * 0.48f, s * 0.05f, s * 0.52f, s * 0.16f, paint)
        paint.color = Color.parseColor("#FF2E54")
        canvas.drawCircle(s * 0.50f, s * 0.05f, s * 0.035f, paint)

        // Metallic head chassis
        paint.shader = LinearGradient(
            s * 0.5f, s * 0.15f, s * 0.5f, s * 0.84f,
            Color.parseColor("#334155"), Color.parseColor("#0F172A"), Shader.TileMode.CLAMP
        )
        canvas.drawRoundRect(RectF(s * 0.18f, s * 0.15f, s * 0.82f, s * 0.84f), s * 0.14f, s * 0.14f, paint)
        paint.shader = null

        // Cyan neon border
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.015f
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawRoundRect(RectF(s * 0.18f, s * 0.15f, s * 0.82f, s * 0.84f), s * 0.14f, s * 0.14f, paint)
        paint.style = Paint.Style.FILL

        // Glowing LED Eyes at (0.35, 0.41) and (0.65, 0.41)
        paint.color = Color.parseColor("#090A0F")
        canvas.drawRoundRect(RectF(s * 0.24f, s * 0.31f, s * 0.76f, s * 0.50f), s * 0.06f, s * 0.06f, paint)
        paint.color = Color.parseColor("#00E5FF")
        canvas.drawRoundRect(RectF(s * 0.28f, s * 0.35f, s * 0.43f, s * 0.46f), s * 0.03f, s * 0.03f, paint)
        canvas.drawRoundRect(RectF(s * 0.57f, s * 0.35f, s * 0.72f, s * 0.46f), s * 0.03f, s * 0.03f, paint)
        paint.color = Color.WHITE
        canvas.drawCircle(s * 0.33f, s * 0.39f, s * 0.018f, paint)
        canvas.drawCircle(s * 0.62f, s * 0.39f, s * 0.018f, paint)

        // Nose sensor at (0.50, 0.52)
        paint.color = Color.parseColor("#38BDF8")
        canvas.drawCircle(s * 0.50f, s * 0.53f, s * 0.022f, paint)

        // Articulated voice grill mouth at (0.36..0.64, y = 0.67)
        paint.color = Color.parseColor("#090A0F")
        canvas.drawRoundRect(RectF(s * 0.34f, s * 0.62f, s * 0.66f, s * 0.72f), s * 0.03f, s * 0.03f, paint)
        paint.color = Color.parseColor("#00E5FF")
        for (i in 0..4) {
            val x = s * (0.38f + i * 0.06f)
            canvas.drawRoundRect(RectF(x - s * 0.01f, s * 0.64f, x + s * 0.01f, s * 0.70f), 4f, 4f, paint)
        }
    }

    private fun drawComicVillain(canvas: Canvas, s: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Dramatic purple/violet hood & collar
        paint.color = Color.parseColor("#3B0764")
        canvas.drawOval(RectF(s * 0.12f, s * 0.08f, s * 0.88f, s * 0.88f), paint)

        // Pale lavender-slate comic face
        paint.shader = LinearGradient(
            s * 0.5f, s * 0.16f, s * 0.5f, s * 0.85f,
            Color.parseColor("#DDD6FE"), Color.parseColor("#A78BFA"), Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(s * 0.21f, s * 0.16f, s * 0.79f, s * 0.85f), paint)
        paint.shader = null

        // Sharp arched villain brows
        paint.color = Color.parseColor("#1E1B4B")
        paint.strokeWidth = s * 0.026f
        paint.strokeCap = Paint.Cap.ROUND
        paint.style = Paint.Style.STROKE
        canvas.drawLine(s * 0.26f, s * 0.30f, s * 0.44f, s * 0.35f, paint)
        canvas.drawLine(s * 0.74f, s * 0.30f, s * 0.56f, s * 0.35f, paint)
        paint.style = Paint.Style.FILL

        // Glowing emerald eyes at (0.36, 0.40) and (0.64, 0.40)
        drawDetailedEye(canvas, s * 0.36f, s * 0.40f, s * 0.075f, s * 0.042f, Color.parseColor("#10B981"))
        drawDetailedEye(canvas, s * 0.64f, s * 0.40f, s * 0.075f, s * 0.042f, Color.parseColor("#10B981"))

        // Sharp comic nose at (0.50, 0.53)
        paint.color = Color.parseColor("#6D28D9")
        val nose = Path().apply {
            moveTo(s * 0.50f, s * 0.41f)
            lineTo(s * 0.45f, s * 0.54f)
            lineTo(s * 0.55f, s * 0.54f)
            close()
        }
        canvas.drawPath(nose, paint)

        // Smirking comic mouth at (0.37..0.63, 0.67)
        paint.color = Color.parseColor("#4C1D95")
        canvas.drawRoundRect(RectF(s * 0.36f, s * 0.64f, s * 0.64f, s * 0.70f), s * 0.03f, s * 0.03f, paint)
    }

    private fun drawCyberCat(canvas: Canvas, s: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Pointed cat ears
        val leftEar = Path().apply {
            moveTo(s * 0.18f, s * 0.38f)
            lineTo(s * 0.14f, s * 0.10f)
            lineTo(s * 0.40f, s * 0.22f)
            close()
        }
        val rightEar = Path().apply {
            moveTo(s * 0.82f, s * 0.38f)
            lineTo(s * 0.86f, s * 0.10f)
            lineTo(s * 0.60f, s * 0.22f)
            close()
        }
        paint.color = Color.parseColor("#1E293B")
        canvas.drawPath(leftEar, paint)
        canvas.drawPath(rightEar, paint)

        // Inner neon cyan ear triangles
        paint.color = Color.parseColor("#00E5FF")
        val leftInner = Path().apply {
            moveTo(s * 0.21f, s * 0.34f)
            lineTo(s * 0.18f, s * 0.15f)
            lineTo(s * 0.36f, s * 0.24f)
            close()
        }
        val rightInner = Path().apply {
            moveTo(s * 0.79f, s * 0.34f)
            lineTo(s * 0.82f, s * 0.15f)
            lineTo(s * 0.64f, s * 0.24f)
            close()
        }
        canvas.drawPath(leftInner, paint)
        canvas.drawPath(rightInner, paint)

        // Cat head fur oval
        paint.shader = RadialGradient(
            s * 0.50f, s * 0.52f, s * 0.38f,
            Color.parseColor("#334155"), Color.parseColor("#0F172A"), Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(s * 0.16f, s * 0.20f, s * 0.84f, s * 0.84f), paint)
        paint.shader = null

        // Expressive cat eyes at (0.35, 0.43) and (0.65, 0.43)
        paint.color = Color.parseColor("#10B981")
        canvas.drawOval(RectF(s * 0.26f, s * 0.36f, s * 0.44f, s * 0.49f), paint)
        canvas.drawOval(RectF(s * 0.56f, s * 0.36f, s * 0.74f, s * 0.49f), paint)
        // Vertical cat slit pupils
        paint.color = Color.parseColor("#090A0F")
        canvas.drawOval(RectF(s * 0.33f, s * 0.37f, s * 0.37f, s * 0.48f), paint)
        canvas.drawOval(RectF(s * 0.63f, s * 0.37f, s * 0.67f, s * 0.48f), paint)
        paint.color = Color.WHITE
        canvas.drawCircle(s * 0.33f, s * 0.40f, s * 0.015f, paint)
        canvas.drawCircle(s * 0.63f, s * 0.40f, s * 0.015f, paint)

        // Pink cat nose triangle at (0.50, 0.56)
        paint.color = Color.parseColor("#F43F5E")
        val catNose = Path().apply {
            moveTo(s * 0.45f, s * 0.53f)
            lineTo(s * 0.55f, s * 0.53f)
            lineTo(s * 0.50f, s * 0.59f)
            close()
        }
        canvas.drawPath(catNose, paint)

        // Cat W-mouth & glowing cyan whiskers
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.016f
        paint.strokeCap = Paint.Cap.ROUND
        paint.color = Color.parseColor("#F8FAFC")
        canvas.drawLine(s * 0.40f, s * 0.67f, s * 0.50f, s * 0.64f, paint)
        canvas.drawLine(s * 0.50f, s * 0.64f, s * 0.60f, s * 0.67f, paint)

        paint.color = Color.parseColor("#00E5FF")
        paint.strokeWidth = s * 0.010f
        canvas.drawLine(s * 0.12f, s * 0.55f, s * 0.32f, s * 0.57f, paint)
        canvas.drawLine(s * 0.12f, s * 0.62f, s * 0.32f, s * 0.61f, paint)
        canvas.drawLine(s * 0.88f, s * 0.55f, s * 0.68f, s * 0.57f, paint)
        canvas.drawLine(s * 0.88f, s * 0.62f, s * 0.68f, s * 0.61f, paint)
    }

    private fun drawGoldenPanda(canvas: Canvas, s: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        // Round black panda ears
        paint.color = Color.parseColor("#111827")
        canvas.drawCircle(s * 0.24f, s * 0.22f, s * 0.11f, paint)
        canvas.drawCircle(s * 0.76f, s * 0.22f, s * 0.11f, paint)
        paint.color = Color.parseColor("#F59E0B")
        canvas.drawCircle(s * 0.24f, s * 0.22f, s * 0.055f, paint)
        canvas.drawCircle(s * 0.76f, s * 0.22f, s * 0.055f, paint)

        // Cream-white panda head
        paint.shader = RadialGradient(
            s * 0.50f, s * 0.50f, s * 0.38f,
            Color.parseColor("#FFFBEB"), Color.parseColor("#E2E8F0"), Shader.TileMode.CLAMP
        )
        canvas.drawOval(RectF(s * 0.16f, s * 0.20f, s * 0.84f, s * 0.84f), paint)
        paint.shader = null

        // Classic dark panda eye patches around (0.35, 0.43) and (0.65, 0.43)
        paint.color = Color.parseColor("#111827")
        canvas.drawOval(RectF(s * 0.24f, s * 0.34f, s * 0.45f, s * 0.51f), paint)
        canvas.drawOval(RectF(s * 0.55f, s * 0.34f, s * 0.76f, s * 0.51f), paint)

        // Bright amber/brown panda eyes inside patches
        drawDetailedEye(canvas, s * 0.35f, s * 0.43f, s * 0.06f, s * 0.042f, Color.parseColor("#F59E0B"))
        drawDetailedEye(canvas, s * 0.65f, s * 0.43f, s * 0.06f, s * 0.042f, Color.parseColor("#F59E0B"))

        // Button nose at (0.50, 0.56)
        paint.color = Color.parseColor("#111827")
        canvas.drawOval(RectF(s * 0.44f, s * 0.52f, s * 0.56f, s * 0.59f), paint)

        // Rosy cheeks
        paint.color = Color.argb(110, 244, 63, 94)
        canvas.drawCircle(s * 0.27f, s * 0.58f, s * 0.055f, paint)
        canvas.drawCircle(s * 0.73f, s * 0.58f, s * 0.055f, paint)

        // Mouth line at (0.40..0.60, 0.67)
        paint.color = Color.parseColor("#111827")
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.018f
        paint.strokeCap = Paint.Cap.ROUND
        canvas.drawLine(s * 0.40f, s * 0.67f, s * 0.50f, s * 0.65f, paint)
        canvas.drawLine(s * 0.50f, s * 0.65f, s * 0.60f, s * 0.67f, paint)
    }
}
