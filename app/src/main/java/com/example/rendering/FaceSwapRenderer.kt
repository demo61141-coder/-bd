package com.example.rendering

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.example.model.CanonicalMeshTopology
import com.example.model.CharacterItem
import com.example.model.LiveFacePose
import com.example.model.Point2D
import com.example.model.PuppetConfig
import com.example.model.RigType
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Hardware-accelerated mesh triangulation & 2D puppet warp renderer.
 * Supports both:
 * 1. [RigType.AUTO_MESH]: 36-vertex Delaunay triangle mesh warping (for Realistic & auto-detected human faces)
 * 2. [RigType.MANUAL_PUPPET]: 2D puppet grid deformation driven by the 5 manual anchor points,
 *    draggable eye/mouth region boxes, live head pose (yaw/pitch/roll), eyeBlink, and jawOpen.
 * 3. Toggleable Inner Mouth Rendering: Detailed Mouth (dark cavity + upper/lower teeth + tongue)
 *    or Simple Dark Mouth Cavity.
 */
object FaceSwapRenderer {

    private val meshPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val mouthCavityPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val teethPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#F1F5F9")
        style = Paint.Style.FILL
    }
    private val tonguePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lipShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(150, 12, 6, 10)
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val wireframeLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(175, 0, 229, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.8f
    }
    private val wireframeDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E5FF")
        style = Paint.Style.FILL
    }

    fun renderWarpedCharacter(
        canvas: Canvas,
        viewportWidth: Float,
        viewportHeight: Float,
        character: CharacterItem,
        characterBitmap: Bitmap,
        pose: LiveFacePose,
        blendOpacity: Float,
        detailedMouthInterior: Boolean,
        showMeshWireframe: Boolean
    ) {
        if (viewportWidth <= 1f || viewportHeight <= 1f || characterBitmap.isRecycled) return

        val alphaInt = (blendOpacity.coerceIn(0.05f, 1.0f) * 255).toInt()
        meshPaint.alpha = alphaInt
        meshPaint.shader = BitmapShader(characterBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)

        when (character.rigType) {
            RigType.AUTO_MESH -> {
                renderTriangulatedFaceMesh(
                    canvas = canvas,
                    vw = viewportWidth,
                    vh = viewportHeight,
                    character = character,
                    bmpWidth = characterBitmap.width.toFloat(),
                    bmpHeight = characterBitmap.height.toFloat(),
                    pose = pose,
                    detailedMouthInterior = detailedMouthInterior,
                    alphaInt = alphaInt
                )
            }
            RigType.MANUAL_PUPPET -> {
                renderPuppetMeshWarp(
                    canvas = canvas,
                    vw = viewportWidth,
                    vh = viewportHeight,
                    character = character,
                    bmpWidth = characterBitmap.width.toFloat(),
                    bmpHeight = characterBitmap.height.toFloat(),
                    pose = pose,
                    detailedMouthInterior = detailedMouthInterior,
                    alphaInt = alphaInt
                )
            }
        }

        if (showMeshWireframe) {
            drawTriangulatedWireframeOverlay(
                canvas = canvas,
                vw = viewportWidth,
                vh = viewportHeight,
                pose = pose
            )
        }
    }

    /**
     * Mode 1: Real-time Delaunay Triangle Mesh Warping onto the user's tracked landmarks.
     */
    private fun renderTriangulatedFaceMesh(
        canvas: Canvas,
        vw: Float,
        vh: Float,
        character: CharacterItem,
        bmpWidth: Float,
        bmpHeight: Float,
        pose: LiveFacePose,
        detailedMouthInterior: Boolean,
        alphaInt: Int
    ) {
        val srcPoints = character.meshControlPoints
        val dstPoints = pose.controlPoints
        val count = minOf(srcPoints.size, dstPoints.size)
        if (count < 36) return

        val indices = CanonicalMeshTopology.triangleIndices
        val vertexCount = indices.size
        val verts = FloatArray(vertexCount * 2)
        val texs = FloatArray(vertexCount * 2)

        for (i in 0 until vertexCount) {
            val ptIdx = indices[i]
            val dst = dstPoints[ptIdx]
            val src = srcPoints[ptIdx]

            verts[i * 2] = dst.x * vw
            verts[i * 2 + 1] = dst.y * vh

            texs[i * 2] = src.x * bmpWidth
            texs[i * 2 + 1] = src.y * bmpHeight
        }

        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            verts.size,
            verts,
            0,
            texs,
            0,
            null,
            0,
            null,
            0,
            0,
            meshPaint
        )

        // Draw dynamic open mouth interior when the user opens their mouth
        if (pose.jawOpen > 0.04f) {
            val mouthLeft = dstPoints[25]
            val mouthRight = dstPoints[27]
            val upperInner = dstPoints[29]
            val lowerInner = dstPoints[30]

            drawOpenMouthInterior(
                canvas = canvas,
                leftX = mouthLeft.x * vw,
                leftY = mouthLeft.y * vh,
                rightX = mouthRight.x * vw,
                rightY = mouthRight.y * vh,
                topCenterX = upperInner.x * vw,
                topCenterY = upperInner.y * vh,
                bottomCenterX = lowerInner.x * vw,
                bottomCenterY = lowerInner.y * vh,
                jawOpen = pose.jawOpen,
                detailedMouthInterior = detailedMouthInterior,
                alphaInt = alphaInt
            )
        }
    }

    /**
     * Mode 2: 2D Puppet Mesh Warp for Cartoon, Animal, and Manual-Setup Characters.
     * Uses an elastic 11x11 vertex grid deformed by:
     * - Head pose (yaw, pitch, roll)
     * - Left & Right Eye Box vertical squash on blink (eyeBlinkLeft, eyeBlinkRight)
     * - Mouth Box lower-half vertical stretch on jawOpen
     */
    private fun renderPuppetMeshWarp(
        canvas: Canvas,
        vw: Float,
        vh: Float,
        character: CharacterItem,
        bmpWidth: Float,
        bmpHeight: Float,
        pose: LiveFacePose,
        detailedMouthInterior: Boolean,
        alphaInt: Int
    ) {
        val cfg: PuppetConfig = character.puppetConfig
        val gridCols = 10
        val gridRows = 10
        val numVerts = (gridCols + 1) * (gridRows + 1)
        val gridX = FloatArray(numVerts)
        val gridY = FloatArray(numVerts)
        val texX = FloatArray(numVerts)
        val texY = FloatArray(numVerts)

        // Compute character scale & center on screen aligned to the user's live face
        val headWidthPx = minOf(vw, vh) * (pose.faceScale * 1.48f)
        val headHeightPx = headWidthPx * (bmpHeight / bmpWidth.coerceAtLeast(1f))
        val nosePivotU = cfg.noseTip.x
        val nosePivotV = cfg.noseTip.y

        val centerX = pose.faceCenterX * vw
        val centerY = pose.faceCenterY * vh

        val rollRad = Math.toRadians(pose.rollDegrees.toDouble()).toFloat()
        val cosR = cos(rollRad)
        val sinR = sin(rollRad)
        val yawSkew = (pose.yawDegrees / 45f).coerceIn(-0.45f, 0.45f)
        val pitchSkew = (pose.pitchDegrees / 45f).coerceIn(-0.35f, 0.35f)

        for (r in 0..gridRows) {
            val uBase = r.toFloat() / gridRows
            for (c in 0..gridCols) {
                val idx = r * (gridCols + 1) + c
                val u = c.toFloat() / gridCols
                val v = uBase

                texX[idx] = u * bmpWidth
                texY[idx] = v * bmpHeight

                var du = u
                var dv = v

                // 1. Left Eye Blink Squash inside leftEyeBox
                if (pose.eyeBlinkLeft > 0.02f) {
                    val box = cfg.leftEyeBox
                    val dx = (u - box.centerX) / (box.width * 0.75f)
                    val dy = (v - box.centerY) / (box.height * 0.85f)
                    val distSq = dx * dx + dy * dy
                    if (distSq < 1f) {
                        val weight = (1f - distSq) * (1f - distSq)
                        dv += (box.centerY - v) * pose.eyeBlinkLeft * 0.85f * weight
                    }
                }

                // 2. Right Eye Blink Squash inside rightEyeBox
                if (pose.eyeBlinkRight > 0.02f) {
                    val box = cfg.rightEyeBox
                    val dx = (u - box.centerX) / (box.width * 0.75f)
                    val dy = (v - box.centerY) / (box.height * 0.85f)
                    val distSq = dx * dx + dy * dy
                    if (distSq < 1f) {
                        val weight = (1f - distSq) * (1f - distSq)
                        dv += (box.centerY - v) * pose.eyeBlinkRight * 0.85f * weight
                    }
                }

                // 3. Jaw Open downward displacement for lower mouth & chin
                val mBox = cfg.mouthBox
                if (pose.jawOpen > 0.02f && v > mBox.centerY - 0.02f) {
                    val horizDist = abs(u - mBox.centerX) / (mBox.width * 0.95f)
                    if (horizDist < 1f) {
                        val hWeight = (1f - horizDist * horizDist)
                        val vFactor = ((v - mBox.centerY) / 0.25f).coerceIn(0f, 1f)
                        dv += pose.jawOpen * 0.13f * hWeight * (0.35f + 0.65f * vFactor)
                    }
                }

                // 4. Transform from character UV space around noseTip pivot to screen space with yaw/pitch/roll
                var localX = (du - nosePivotU) * headWidthPx
                var localY = (dv - nosePivotV) * headHeightPx

                // Subtle 2.5D parallax perspective shift for head yaw and pitch
                val radialFactor = 1f - hypot(du - 0.5f, dv - 0.5f).coerceIn(0f, 0.7f)
                localX += yawSkew * headWidthPx * 0.22f * radialFactor
                localY += pitchSkew * headHeightPx * 0.16f * radialFactor
                localX *= (1f - abs(yawSkew) * 0.12f)

                val rotX = localX * cosR - localY * sinR
                val rotY = localX * sinR + localY * cosR

                gridX[idx] = centerX + rotX
                gridY[idx] = centerY + rotY
            }
        }

        // Build triangle list from grid
        val triCount = gridCols * gridRows * 2
        val verts = FloatArray(triCount * 3 * 2)
        val texs = FloatArray(triCount * 3 * 2)
        var ptr = 0

        fun emitVertex(vIdx: Int) {
            verts[ptr] = gridX[vIdx]
            verts[ptr + 1] = gridY[vIdx]
            texs[ptr] = texX[vIdx]
            texs[ptr + 1] = texY[vIdx]
            ptr += 2
        }

        for (r in 0 until gridRows) {
            for (c in 0 until gridCols) {
                val i00 = r * (gridCols + 1) + c
                val i10 = i00 + 1
                val i01 = (r + 1) * (gridCols + 1) + c
                val i11 = i01 + 1

                emitVertex(i00)
                emitVertex(i10)
                emitVertex(i01)

                emitVertex(i10)
                emitVertex(i11)
                emitVertex(i01)
            }
        }

        canvas.drawVertices(
            Canvas.VertexMode.TRIANGLES,
            verts.size,
            verts,
            0,
            texs,
            0,
            null,
            0,
            null,
            0,
            0,
            meshPaint
        )

        // Render animated open mouth interior inside the puppet's mouth region
        if (pose.jawOpen > 0.04f) {
            fun transformPuppetPoint(u: Float, v: Float): Point2D {
                var localX = (u - nosePivotU) * headWidthPx
                var localY = (v - nosePivotV) * headHeightPx
                val radialFactor = 1f - hypot(u - 0.5f, v - 0.5f).coerceIn(0f, 0.7f)
                localX += yawSkew * headWidthPx * 0.22f * radialFactor
                localY += pitchSkew * headHeightPx * 0.16f * radialFactor
                localX *= (1f - abs(yawSkew) * 0.12f)
                val rotX = localX * cosR - localY * sinR
                val rotY = localX * sinR + localY * cosR
                return Point2D(centerX + rotX, centerY + rotY)
            }

            val mLeft = transformPuppetPoint(cfg.mouthLeft.x, cfg.mouthLeft.y)
            val mRight = transformPuppetPoint(cfg.mouthRight.x, cfg.mouthRight.y)
            val mCenterU = (cfg.mouthLeft.x + cfg.mouthRight.x) * 0.5f
            val mCenterV = (cfg.mouthLeft.y + cfg.mouthRight.y) * 0.5f
            val topLip = transformPuppetPoint(mCenterU, mCenterV - pose.jawOpen * 0.015f)
            val bottomLip = transformPuppetPoint(
                mCenterU,
                mCenterV + pose.jawOpen * (cfg.mouthBox.height * 0.85f).coerceAtLeast(0.08f)
            )

            drawOpenMouthInterior(
                canvas = canvas,
                leftX = mLeft.x,
                leftY = mLeft.y,
                rightX = mRight.x,
                rightY = mRight.y,
                topCenterX = topLip.x,
                topCenterY = topLip.y,
                bottomCenterX = bottomLip.x,
                bottomCenterY = bottomLip.y,
                jawOpen = pose.jawOpen,
                detailedMouthInterior = detailedMouthInterior,
                alphaInt = alphaInt
            )
        }
    }

    /**
     * Renders the open mouth interior cavity between the upper and lower lips.
     * Supports both:
     * - Detailed Mouth Mode (dark oral cavity + upper teeth row + lower teeth hint + tongue)
     * - Simple Dark Cavity Mode (clean dark mouth opening)
     */
    private fun drawOpenMouthInterior(
        canvas: Canvas,
        leftX: Float,
        leftY: Float,
        rightX: Float,
        rightY: Float,
        topCenterX: Float,
        topCenterY: Float,
        bottomCenterX: Float,
        bottomCenterY: Float,
        jawOpen: Float,
        detailedMouthInterior: Boolean,
        alphaInt: Int
    ) {
        val mouthPath = Path().apply {
            moveTo(leftX, leftY)
            quadTo(topCenterX, topCenterY, rightX, rightY)
            quadTo(bottomCenterX, bottomCenterY, leftX, leftY)
            close()
        }

        // 1. Dark mouth interior cavity
        mouthCavityPaint.alpha = alphaInt
        mouthCavityPaint.shader = LinearGradient(
            topCenterX, topCenterY, bottomCenterX, bottomCenterY,
            Color.parseColor("#14070C"),
            Color.parseColor("#2B0B16"),
            Shader.TileMode.CLAMP
        )
        canvas.drawPath(mouthPath, mouthCavityPaint)

        // 2. Optional Detailed Teeth & Tongue clipped cleanly to the mouth opening
        if (detailedMouthInterior && jawOpen > 0.08f) {
            val mouthWidth = hypot(rightX - leftX, rightY - leftY).coerceAtLeast(10f)
            val mouthHeight = hypot(bottomCenterX - topCenterX, bottomCenterY - topCenterY).coerceAtLeast(4f)

            canvas.save()
            canvas.clipPath(mouthPath)

            // Tongue at the bottom of the oral cavity
            val tongueTopY = topCenterY + mouthHeight * 0.48f
            tonguePaint.alpha = alphaInt
            tonguePaint.shader = LinearGradient(
                bottomCenterX, tongueTopY, bottomCenterX, bottomCenterY,
                Color.parseColor("#E11D48"),
                Color.parseColor("#9F1239"),
                Shader.TileMode.CLAMP
            )
            canvas.drawOval(
                RectF(
                    leftX + mouthWidth * 0.16f,
                    tongueTopY,
                    rightX - mouthWidth * 0.16f,
                    bottomCenterY + mouthHeight * 0.28f
                ),
                tonguePaint
            )

            // Upper teeth bar along the top lip
            teethPaint.alpha = (alphaInt * 0.92f).toInt()
            val teethBottomY = topCenterY + mouthHeight * 0.28f
            canvas.drawRoundRect(
                RectF(
                    leftX + mouthWidth * 0.12f,
                    topCenterY - mouthHeight * 0.12f,
                    rightX - mouthWidth * 0.12f,
                    teethBottomY
                ),
                mouthWidth * 0.06f,
                mouthWidth * 0.06f,
                teethPaint
            )

            canvas.restore()
        }

        // Subtle inner lip crease outline for depth
        lipShadowPaint.alpha = (alphaInt * 0.7f).toInt()
        canvas.drawPath(mouthPath, lipShadowPaint)
    }

    private fun drawTriangulatedWireframeOverlay(
        canvas: Canvas,
        vw: Float,
        vh: Float,
        pose: LiveFacePose
    ) {
        val pts = pose.controlPoints
        val indices = CanonicalMeshTopology.triangleIndices
        var i = 0
        while (i + 2 < indices.size) {
            val p0 = pts[indices[i]]
            val p1 = pts[indices[i + 1]]
            val p2 = pts[indices[i + 2]]

            val x0 = p0.x * vw
            val y0 = p0.y * vh
            val x1 = p1.x * vw
            val y1 = p1.y * vh
            val x2 = p2.x * vw
            val y2 = p2.y * vh

            canvas.drawLine(x0, y0, x1, y1, wireframeLinePaint)
            canvas.drawLine(x1, y1, x2, y2, wireframeLinePaint)
            canvas.drawLine(x2, y2, x0, y0, wireframeLinePaint)
            i += 3
        }

        for (pt in pts) {
            canvas.drawCircle(pt.x * vw, pt.y * vh, 3.5f, wireframeDotPaint)
        }
    }
}
