package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CharacterCategory
import com.example.model.CharacterItem
import com.example.model.LiveFacePose
import com.example.model.NormalizedBox
import com.example.model.Point2D
import com.example.model.PuppetConfig
import com.example.model.RigType
import com.example.rendering.FaceSwapRenderer
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CineRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextAlabaster
import com.example.ui.theme.TextSlate
import com.example.viewmodel.ManualSetupDraft
import kotlin.math.hypot

private enum class AnchorPin(val index: Int, val labelBnEn: String, val color: Color) {
    LEFT_EYE(0, "1. বাম চোখ / Left Eye", ElectricCyan),
    RIGHT_EYE(1, "2. ডান চোখ / Right Eye", ElectricCyan),
    NOSE_TIP(2, "3. নাক / Nose Tip", EmeraldReady),
    MOUTH_LEFT(3, "4. মুখের বাম / Mouth Left", AmberWarn),
    MOUTH_RIGHT(4, "5. মুখের ডান / Mouth Right", CineRed)
}

@Composable
fun ManualSetupSheet(
    draft: ManualSetupDraft,
    detailedMouthInterior: Boolean,
    onCancel: () -> Unit,
    onSave: (nameEn: String, nameBn: String, config: PuppetConfig) -> Unit
) {
    BackHandler(onBack = onCancel)

    var nameEn by remember(draft.id) { mutableStateOf(draft.nameEn) }
    var nameBn by remember(draft.id) { mutableStateOf(draft.nameBn) }
    var config by remember(draft.id) { mutableStateOf(draft.puppetConfig) }
    var selectedPinIndex by remember { mutableIntStateOf(0) }
    var editTab by remember { mutableIntStateOf(0) } // 0 = 5 Points + Boxes, 1 = Live Puppet Warp Test
    var testJawOpen by remember { mutableFloatStateOf(0.45f) }
    var testEyeBlink by remember { mutableFloatStateOf(0.0f) }
    var canvasSize by remember { mutableStateOf(IntSize(1, 1)) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("manual_setup_editor")
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCancel) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Cancel / বাতিল",
                        tint = TextAlabaster
                    )
                }
                Column {
                    Text(
                        text = "ম্যানুয়াল সেটআপ / Manual Puppet Setup",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextAlabaster,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Cartoon, Animal & Custom 2D Rig Editor",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioViolet
                    )
                }
            }

            IconButton(
                onClick = { config = PuppetConfig() }
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Points / রিসেট",
                    tint = ElectricCyan
                )
            }
        }

        // Info Banner
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(StudioViolet.copy(alpha = 0.16f))
                .border(1.dp, StudioViolet.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            Text(
                text = draft.bannerMessageBnEn,
                style = MaterialTheme.typography.labelMedium,
                color = TextAlabaster
            )
        }

        // Mode Switcher: Edit 5 Points & Boxes vs Live Warp Test
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                0 to "৫টি পয়েন্ট ও বক্স এডিট / Edit 5 Points & Boxes",
                1 to "লাইভ পাপেট টেস্ট / Live Warp Preview"
            ).forEach { (idx, title) ->
                val active = editTab == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) ElectricCyan else StudioCard)
                        .clickable { editTab = idx }
                        .padding(vertical = 10.dp, horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (active) StudioObsidian else TextAlabaster,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Active Pin Quick Selector (in Edit Mode)
        if (editTab == 0) {
            Text(
                text = "ট্যাপ করে পয়েন্ট বসান অথবা সরাসরি ড্র্যাগ করুন / Select a point to tap-place or drag any pin & region box:",
                style = MaterialTheme.typography.labelSmall,
                color = TextSlate,
                modifier = Modifier.padding(bottom = 6.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                AnchorPin.entries.forEach { pin ->
                    val selected = selectedPinIndex == pin.index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) pin.color.copy(alpha = 0.28f) else StudioCard)
                            .border(
                                width = if (selected) 1.5.dp else 1.dp,
                                color = if (selected) pin.color else StudioBorder,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable { selectedPinIndex = pin.index }
                            .padding(vertical = 6.dp, horizontal = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = pin.labelBnEn,
                            fontSize = 9.sp,
                            color = TextAlabaster,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }

        // Interactive Square Canvas (Checkerboard PNG Transparency + Image + Pins/Boxes or Live Test)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .border(1.5.dp, StudioBorder, RoundedCornerShape(18.dp))
                .onSizeChanged { canvasSize = it }
                .pointerInput(editTab, selectedPinIndex) {
                    if (editTab == 0) {
                        detectTapGestures { offset ->
                            val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                            val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                            val nx = (offset.x / w).coerceIn(0.05f, 0.95f)
                            val ny = (offset.y / h).coerceIn(0.05f, 0.95f)
                            config = updatePinAndBoxes(config, selectedPinIndex, Point2D(nx, ny))
                            selectedPinIndex = (selectedPinIndex + 1) % 5
                        }
                    }
                }
                .pointerInput(editTab) {
                    if (editTab == 0) {
                        var activeDragTarget = -1 // 0..4 pins, 10=leftEyeBox, 11=rightEyeBox, 12=mouthBox
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                val sx = startOffset.x / w
                                val sy = startOffset.y / h

                                val pins = listOf(
                                    config.leftEye,
                                    config.rightEye,
                                    config.noseTip,
                                    config.mouthLeft,
                                    config.mouthRight
                                )
                                val nearestPin = pins.indices.minByOrNull { idx ->
                                    hypot(pins[idx].x - sx, pins[idx].y - sy)
                                } ?: 0
                                val minPinDist = hypot(pins[nearestPin].x - sx, pins[nearestPin].y - sy)

                                activeDragTarget = when {
                                    minPinDist < 0.08f -> {
                                        selectedPinIndex = nearestPin
                                        nearestPin
                                    }
                                    isInsideBox(sx, sy, config.leftEyeBox) -> 10
                                    isInsideBox(sx, sy, config.rightEyeBox) -> 11
                                    isInsideBox(sx, sy, config.mouthBox) -> 12
                                    else -> nearestPin
                                }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                val w = canvasSize.width.toFloat().coerceAtLeast(1f)
                                val h = canvasSize.height.toFloat().coerceAtLeast(1f)
                                val dx = dragAmount.x / w
                                val dy = dragAmount.y / h

                                config = when (activeDragTarget) {
                                    0 -> movePin(config, 0, dx, dy)
                                    1 -> movePin(config, 1, dx, dy)
                                    2 -> movePin(config, 2, dx, dy)
                                    3 -> movePin(config, 3, dx, dy)
                                    4 -> movePin(config, 4, dx, dy)
                                    10 -> config.copy(leftEyeBox = shiftBox(config.leftEyeBox, dx, dy))
                                    11 -> config.copy(rightEyeBox = shiftBox(config.rightEyeBox, dx, dy))
                                    12 -> config.copy(mouthBox = shiftBox(config.mouthBox, dx, dy))
                                    else -> config
                                }
                            }
                        )
                    }
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // Draw checkerboard background to highlight PNG transparency
                val tileSize = 24.dp.toPx()
                val cols = (w / tileSize).toInt() + 1
                val rows = (h / tileSize).toInt() + 1
                for (r in 0 until rows) {
                    for (c in 0 until cols) {
                        val isDark = (r + c) % 2 == 0
                        drawRect(
                            color = if (isDark) Color(0xFF141824) else Color(0xFF1E2436),
                            topLeft = Offset(c * tileSize, r * tileSize),
                            size = Size(tileSize, tileSize)
                        )
                    }
                }

                if (editTab == 0) {
                    // Draw character image full canvas
                    drawContext.canvas.nativeCanvas.drawBitmap(
                        draft.bitmap,
                        null,
                        android.graphics.RectF(0f, 0f, w, h),
                        null
                    )

                    // Draw 3 Draggable Region Boxes (Left Eye, Right Eye, Mouth)
                    fun drawRegionBox(box: NormalizedBox, color: Color) {
                        val left = box.left * w
                        val top = box.top * h
                        val bw = box.width * w
                        val bh = box.height * h
                        drawRect(
                            color = color.copy(alpha = 0.16f),
                            topLeft = Offset(left, top),
                            size = Size(bw, bh)
                        )
                        drawRect(
                            color = color,
                            topLeft = Offset(left, top),
                            size = Size(bw, bh),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }

                    drawRegionBox(config.leftEyeBox, ElectricCyan)
                    drawRegionBox(config.rightEyeBox, ElectricCyan)
                    drawRegionBox(config.mouthBox, CineRed)

                    // Draw 5 Anchor Points
                    val pins = listOf(
                        config.leftEye to AnchorPin.LEFT_EYE,
                        config.rightEye to AnchorPin.RIGHT_EYE,
                        config.noseTip to AnchorPin.NOSE_TIP,
                        config.mouthLeft to AnchorPin.MOUTH_LEFT,
                        config.mouthRight to AnchorPin.MOUTH_RIGHT
                    )

                    pins.forEach { (pt, meta) ->
                        val px = pt.x * w
                        val py = pt.y * h
                        val isSelected = selectedPinIndex == meta.index
                        drawCircle(
                            color = StudioObsidian,
                            radius = if (isSelected) 12.dp.toPx() else 9.dp.toPx(),
                            center = Offset(px, py)
                        )
                        drawCircle(
                            color = meta.color,
                            radius = if (isSelected) 9.dp.toPx() else 6.5.dp.toPx(),
                            center = Offset(px, py)
                        )
                    }
                } else {
                    // Live 2D Puppet Warp Preview inside editor
                    val previewCharacter = CharacterItem(
                        id = draft.id,
                        nameEn = nameEn,
                        nameBn = nameBn,
                        subtitleEn = "",
                        subtitleBn = "",
                        category = CharacterCategory.CUSTOM,
                        rigType = RigType.MANUAL_PUPPET,
                        puppetConfig = config
                    )
                    val testPose = LiveFacePose(
                        isTracking = true,
                        jawOpen = testJawOpen,
                        eyeBlinkLeft = testEyeBlink,
                        eyeBlinkRight = testEyeBlink,
                        faceCenterX = 0.50f,
                        faceCenterY = 0.50f,
                        faceScale = 0.62f
                    )
                    FaceSwapRenderer.renderWarpedCharacter(
                        canvas = drawContext.canvas.nativeCanvas,
                        viewportWidth = w,
                        viewportHeight = h,
                        character = previewCharacter,
                        characterBitmap = draft.bitmap,
                        pose = testPose,
                        blendOpacity = 1.0f,
                        detailedMouthInterior = detailedMouthInterior,
                        showMeshWireframe = false
                    )
                }
            }
        }

        // Live Expression Test Sliders when in Live Warp Preview tab
        if (editTab == 1) {
            Spacer(modifier = Modifier.height(12.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(StudioCard)
                    .padding(12.dp)
            ) {
                Text(
                    text = "মুখ খোলা টেস্ট / Test Jaw Open (${(testJawOpen * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextAlabaster
                )
                Slider(
                    value = testJawOpen,
                    onValueChange = { testJawOpen = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = CineRed,
                        activeTrackColor = CineRed
                    )
                )
                Text(
                    text = "চোখের পলক টেস্ট / Test Eye Blink (${(testEyeBlink * 100).toInt()}%)",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextAlabaster
                )
                Slider(
                    value = testEyeBlink,
                    onValueChange = { testEyeBlink = it },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricCyan,
                        activeTrackColor = ElectricCyan
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Character Name Inputs (Bengali & English)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = nameBn,
                onValueChange = { nameBn = it },
                label = { Text("বাংলা নাম / Bengali Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = nameEn,
                onValueChange = { nameEn = it },
                label = { Text("English Name") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Save Button
        Button(
            onClick = { onSave(nameEn, nameBn, config) },
            colors = ButtonDefaults.buttonColors(
                containerColor = ElectricCyan,
                contentColor = StudioObsidian
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("save_manual_character_button")
        ) {
            Icon(imageVector = Icons.Default.Check, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "পাপেট ক্যারেক্টার সেভ করুন / Save 2D Puppet Character",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

private fun isInsideBox(x: Float, y: Float, box: NormalizedBox): Boolean {
    return x in box.left..box.right && y in box.top..box.bottom
}

private fun shiftBox(box: NormalizedBox, dx: Float, dy: Float): NormalizedBox {
    val w = box.width
    val h = box.height
    val newLeft = (box.left + dx).coerceIn(0.03f, 0.97f - w)
    val newTop = (box.top + dy).coerceIn(0.03f, 0.97f - h)
    return NormalizedBox(newLeft, newTop, newLeft + w, newTop + h)
}

private fun movePin(cfg: PuppetConfig, index: Int, dx: Float, dy: Float): PuppetConfig {
    fun shiftPt(pt: Point2D) = Point2D(
        (pt.x + dx).coerceIn(0.05f, 0.95f),
        (pt.y + dy).coerceIn(0.05f, 0.95f)
    )
    return when (index) {
        0 -> updatePinAndBoxes(cfg, 0, shiftPt(cfg.leftEye))
        1 -> updatePinAndBoxes(cfg, 1, shiftPt(cfg.rightEye))
        2 -> updatePinAndBoxes(cfg, 2, shiftPt(cfg.noseTip))
        3 -> updatePinAndBoxes(cfg, 3, shiftPt(cfg.mouthLeft))
        4 -> updatePinAndBoxes(cfg, 4, shiftPt(cfg.mouthRight))
        else -> cfg
    }
}

private fun updatePinAndBoxes(cfg: PuppetConfig, index: Int, newPt: Point2D): PuppetConfig {
    return when (index) {
        0 -> {
            val hw = cfg.leftEyeBox.width * 0.5f
            val hh = cfg.leftEyeBox.height * 0.5f
            cfg.copy(
                leftEye = newPt,
                leftEyeBox = NormalizedBox(
                    (newPt.x - hw).coerceIn(0.03f, 0.90f),
                    (newPt.y - hh).coerceIn(0.03f, 0.90f),
                    (newPt.x + hw).coerceIn(0.10f, 0.97f),
                    (newPt.y + hh).coerceIn(0.10f, 0.97f)
                )
            )
        }
        1 -> {
            val hw = cfg.rightEyeBox.width * 0.5f
            val hh = cfg.rightEyeBox.height * 0.5f
            cfg.copy(
                rightEye = newPt,
                rightEyeBox = NormalizedBox(
                    (newPt.x - hw).coerceIn(0.03f, 0.90f),
                    (newPt.y - hh).coerceIn(0.03f, 0.90f),
                    (newPt.x + hw).coerceIn(0.10f, 0.97f),
                    (newPt.y + hh).coerceIn(0.10f, 0.97f)
                )
            )
        }
        2 -> cfg.copy(noseTip = newPt)
        3 -> {
            val right = cfg.mouthRight
            val cy = (newPt.y + right.y) * 0.5f
            val hh = cfg.mouthBox.height * 0.5f
            cfg.copy(
                mouthLeft = newPt,
                mouthBox = NormalizedBox(
                    left = (minOf(newPt.x, right.x) - 0.03f).coerceIn(0.04f, 0.90f),
                    top = (cy - hh).coerceIn(0.04f, 0.90f),
                    right = (maxOf(newPt.x, right.x) + 0.03f).coerceIn(0.10f, 0.96f),
                    bottom = (cy + hh).coerceIn(0.10f, 0.96f)
                )
            )
        }
        4 -> {
            val left = cfg.mouthLeft
            val cy = (left.y + newPt.y) * 0.5f
            val hh = cfg.mouthBox.height * 0.5f
            cfg.copy(
                mouthRight = newPt,
                mouthBox = NormalizedBox(
                    left = (minOf(left.x, newPt.x) - 0.03f).coerceIn(0.04f, 0.90f),
                    top = (cy - hh).coerceIn(0.04f, 0.90f),
                    right = (maxOf(left.x, newPt.x) + 0.03f).coerceIn(0.10f, 0.96f),
                    bottom = (cy + hh).coerceIn(0.10f, 0.96f)
                )
            )
        }
        else -> cfg
    }
}
