package com.example.ui.components

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.FaceRetouchingOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.DemoCharacterCatalog
import com.example.model.CharacterItem
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
import com.example.video.DetectedVideoFaceSlot
import com.example.video.MatchedFrameFace
import com.example.video.ScannedVideoSession
import com.example.video.VideoMultiFaceProcessor
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val SLOT_BADGE_COLORS = listOf(
    ElectricCyan,
    StudioViolet,
    EmeraldReady,
    AmberWarn,
    CineRed
)

@Composable
fun VideoSwapStudioSheet(
    session: ScannedVideoSession?,
    isScanning: Boolean,
    isExporting: Boolean,
    exportProgress: Float,
    allCharacters: List<CharacterItem>,
    blendOpacity: Float,
    detailedMouthInterior: Boolean,
    processor: VideoMultiFaceProcessor,
    onPickAnotherVideo: () -> Unit,
    onUploadCustomFaceImage: () -> Unit,
    onAssignCharacterToSlot: (slotIndex: Int, characterId: String?) -> Unit,
    onExportSwappedVideo: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    var selectedSlotIndex by remember(session?.videoUri) { mutableIntStateOf(0) }
    var currentFrameIndex by remember(session?.videoUri) { mutableIntStateOf(0) }
    var isPlayingPreview by remember(session?.videoUri) { mutableStateOf(true) }
    var compositedPreviewBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Continuous playback loop through sampled video frames with real-time multi-face warping
    LaunchedEffect(session, isPlayingPreview) {
        val s = session ?: return@LaunchedEffect
        if (s.sampleFrames.isEmpty()) return@LaunchedEffect
        while (isActive && isPlayingPreview) {
            delay(180L)
            currentFrameIndex = (currentFrameIndex + 1) % s.sampleFrames.size
        }
    }

    // Re-render current video frame whenever frame index or face-slot character assignments change
    LaunchedEffect(
        session,
        currentFrameIndex,
        session?.detectedSlots,
        blendOpacity,
        detailedMouthInterior,
        allCharacters
    ) {
        val s = session ?: return@LaunchedEffect
        if (s.sampleFrames.isEmpty()) return@LaunchedEffect
        val clampedIdx = currentFrameIndex.coerceIn(0, s.sampleFrames.lastIndex)
        val rawFrame = s.sampleFrames[clampedIdx]
        val matched: List<MatchedFrameFace> = processor.matchFacesInFrame(
            frame = rawFrame,
            slots = s.detectedSlots,
            frameIndex = clampedIdx,
            session = s
        )
        compositedPreviewBitmap = processor.renderMultiFaceFrame(
            sourceFrame = rawFrame,
            matchedFaces = matched,
            slots = s.detectedSlots,
            allCharacters = allCharacters,
            blendOpacity = blendOpacity,
            detailedMouthInterior = detailedMouthInterior,
            showMeshWireframe = false
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .testTag("video_swap_studio_sheet")
    ) {
        // Top Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close / বন্ধ করুন",
                        tint = TextAlabaster
                    )
                }
                Column {
                    Text(
                        text = "ভিডিও মাল্টি-ফেস স্টুডিও / Video FaceSwap",
                        style = MaterialTheme.typography.titleMedium,
                        color = TextAlabaster,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "ভিডিওর সবগুলো মুখ আলাদাভাবে পরিবর্তন করুন / Swap multiple faces in pre-recorded video",
                        style = MaterialTheme.typography.labelSmall,
                        color = ElectricCyan
                    )
                }
            }

            IconButton(
                onClick = onPickAnotherVideo,
                modifier = Modifier.testTag("pick_another_video_button")
            ) {
                Icon(
                    imageVector = Icons.Default.VideoFile,
                    contentDescription = "Pick Video / অন্য ভিডিও নিন",
                    tint = ElectricCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        when {
            isScanning -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(StudioCard)
                        .border(1.dp, ElectricCyan, RoundedCornerShape(22.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = ElectricCyan)
                        Spacer(modifier = Modifier.height(14.dp))
                        Text(
                            text = "ভিডিও স্ক্যান করে সবগুলো মুখ খোঁজা হচ্ছে...",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextAlabaster,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Scanning video frames & clustering all detected faces...",
                            style = MaterialTheme.typography.labelMedium,
                            color = ElectricCyan,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            session == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(StudioCard)
                        .border(1.5.dp, ElectricCyan, RoundedCornerShape(22.dp))
                        .clickable(onClick = onPickAnotherVideo)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.VideoFile,
                            contentDescription = null,
                            tint = ElectricCyan,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "ফোন থেকে আগের রেকর্ড করা ভিডিও সিলেক্ট করুন\nSelect a Pre-Recorded Video from Gallery",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextAlabaster,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "ভিডিওতে যতগুলো মানুষের মুখ থাকবে সবগুলো আলাদাভাবে শনাক্ত হবে এবং প্রতিটি মুখ ইচ্ছেমতো পরিবর্তন করতে পারবেন।",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSlate,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            else -> {
                // 1. Live Multi-Face Swapped Video Preview Viewport
                val aspect = (session.videoWidth.toFloat() / session.videoHeight.coerceAtLeast(1))
                    .coerceIn(0.56f, 1.4f)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(aspect.coerceAtLeast(0.75f))
                        .clip(RoundedCornerShape(20.dp))
                        .background(StudioCard)
                        .border(1.5.dp, StudioBorder, RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    val bmpToShow = compositedPreviewBitmap ?: session.sampleFrames.firstOrNull()
                    if (bmpToShow != null) {
                        Image(
                            bitmap = bmpToShow.asImageBitmap(),
                            contentDescription = "Multi-Face Video Preview",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Top-left badge showing how many faces were found
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(StudioObsidian.copy(alpha = 0.80f))
                            .border(1.dp, ElectricCyan, RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "${session.detectedSlots.size}টি মুখ পাওয়া গেছে • ${session.detectedSlots.size} Faces Found",
                            style = MaterialTheme.typography.labelSmall,
                            color = ElectricCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Timeline Play/Pause & Frame Scrubber
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(StudioCard)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { isPlayingPreview = !isPlayingPreview }) {
                        Icon(
                            imageVector = if (isPlayingPreview) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = "Play/Pause",
                            tint = ElectricCyan
                        )
                    }
                    Slider(
                        value = currentFrameIndex.toFloat(),
                        onValueChange = {
                            isPlayingPreview = false
                            currentFrameIndex = it.toInt().coerceIn(0, (session.sampleFrames.size - 1).coerceAtLeast(0))
                        },
                        valueRange = 0f..(session.sampleFrames.size - 1).coerceAtLeast(1).toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = ElectricCyan,
                            activeTrackColor = ElectricCyan
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "${(session.durationMs / 1000).coerceAtLeast(1)}s",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSlate
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 2. Detected Faces in Video Row (Select which person's face to swap!)
                Text(
                    text = "১. ভিডিওতে শনাক্ত করা মুখ সিলেক্ট করুন / 1. Select a Face in the Video (${session.detectedSlots.size}):",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextAlabaster,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    items(session.detectedSlots, key = { it.slotIndex }) { slot ->
                        val isSelectedSlot = slot.slotIndex == selectedSlotIndex
                        val badgeColor = SLOT_BADGE_COLORS[slot.slotIndex % SLOT_BADGE_COLORS.size]
                        val assignedChar = allCharacters.find { it.id == slot.assignedCharacterId }

                        DetectedFaceSlotCard(
                            slot = slot,
                            assignedCharacter = assignedChar,
                            isSelected = isSelectedSlot,
                            badgeColor = badgeColor,
                            onClick = { selectedSlotIndex = slot.slotIndex }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Assign a Replacement Character to the Selected Face Slot
                val activeSlot = session.detectedSlots.find { it.slotIndex == selectedSlotIndex }
                    ?: session.detectedSlots.first()
                Text(
                    text = "২. '${activeSlot.labelBn} (${activeSlot.labelEn})'-এর জন্য নতুন মুখ বেছে নিন / 2. Choose Replacement Face:",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(horizontal = 2.dp)
                ) {
                    // Option A: Keep Original Face (আসল মুখ রাখুন)
                    item(key = "keep_original_face") {
                        val isOriginal = activeSlot.assignedCharacterId == null
                        Column(
                            modifier = Modifier
                                .width(78.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isOriginal) EmeraldReady.copy(alpha = 0.2f) else StudioCard)
                                .border(
                                    width = if (isOriginal) 2.dp else 1.dp,
                                    color = if (isOriginal) EmeraldReady else StudioBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onAssignCharacterToSlot(activeSlot.slotIndex, null)
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(StudioObsidian),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FaceRetouchingOff,
                                    contentDescription = "Keep Original",
                                    tint = EmeraldReady
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "আসল মুখ\nOriginal",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isOriginal) EmeraldReady else TextAlabaster,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 11.sp
                            )
                        }
                    }

                    // Option B: Upload a new face photo from Gallery
                    item(key = "upload_new_custom_face") {
                        Column(
                            modifier = Modifier
                                .width(78.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(StudioCard)
                                .border(1.5.dp, ElectricCyan, RoundedCornerShape(16.dp))
                                .clickable(onClick = onUploadCustomFaceImage)
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(ElectricCyan.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddPhotoAlternate,
                                    contentDescription = "Upload Face",
                                    tint = ElectricCyan
                                )
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "+ নতুন মুখ\nUpload Face",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                lineHeight = 11.sp
                            )
                        }
                    }

                    // Option C: All Built-In & Custom Characters
                    items(allCharacters, key = { it.id }) { charItem ->
                        val context = LocalContext.current
                        val bmp = remember(charItem.id, charItem.customImagePath) {
                            DemoCharacterCatalog.getCharacterBitmap(context, charItem)
                        }
                        val isAssigned = activeSlot.assignedCharacterId == charItem.id

                        Column(
                            modifier = Modifier
                                .width(78.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isAssigned) ElectricCyan.copy(alpha = 0.18f) else StudioCard)
                                .border(
                                    width = if (isAssigned) 2.dp else 1.dp,
                                    color = if (isAssigned) ElectricCyan else StudioBorder,
                                    shape = RoundedCornerShape(16.dp)
                                )
                                .clickable {
                                    onAssignCharacterToSlot(activeSlot.slotIndex, charItem.id)
                                }
                                .padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = charItem.nameEn,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.height(5.dp))
                            Text(
                                text = "${charItem.nameBn}\n${charItem.nameEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isAssigned) ElectricCyan else TextAlabaster,
                                fontWeight = if (isAssigned) FontWeight.Bold else FontWeight.Normal,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                lineHeight = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 4. Export & Save Swapped Video to Phone Gallery Button
                if (isExporting) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioCard)
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "মাল্টি-ফেস ভিডিও রেন্ডার ও গ্যালারিতে সেভ হচ্ছে... ${(exportProgress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = TextAlabaster,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { exportProgress },
                            color = ElectricCyan,
                            trackColor = StudioObsidian,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = onPickAnotherVideo,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = StudioCard,
                                contentColor = TextAlabaster
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                        ) {
                            Icon(imageVector = Icons.Default.VideoFile, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "অন্য ভিডিও / Change Video",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Button(
                            onClick = onExportSwappedVideo,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ElectricCyan,
                                contentColor = StudioObsidian
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1.35f)
                                .height(52.dp)
                                .testTag("export_swapped_video_button")
                        ) {
                            Icon(imageVector = Icons.Default.SaveAlt, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "ভিডিও সেভ করুন / Save Video",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetectedFaceSlotCard(
    slot: DetectedVideoFaceSlot,
    assignedCharacter: CharacterItem?,
    isSelected: Boolean,
    badgeColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) badgeColor.copy(alpha = 0.20f) else StudioCard)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) badgeColor else StudioBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Image(
                bitmap = slot.thumbnail.asImageBitmap(),
                contentDescription = slot.labelEn,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .border(1.5.dp, badgeColor, CircleShape)
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(badgeColor),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${slot.slotIndex + 1}",
                    fontSize = 10.sp,
                    color = StudioObsidian,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = "${slot.labelBn} • ${slot.labelEn}",
                style = MaterialTheme.typography.labelLarge,
                color = TextAlabaster,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (assignedCharacter != null) {
                    "➜ ${assignedCharacter.nameBn} (${assignedCharacter.nameEn})"
                } else {
                    "• আসল মুখ / Original Face"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (assignedCharacter != null) badgeColor else TextSlate
            )
        }
    }
}
