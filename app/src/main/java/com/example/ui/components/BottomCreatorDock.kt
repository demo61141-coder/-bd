package com.example.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.example.model.CharacterCategory
import com.example.model.CharacterItem
import com.example.model.LanguageMode
import com.example.model.RigType
import com.example.ui.theme.CineRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceGlass
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextAlabaster
import com.example.ui.theme.TextSlate

@Composable
fun BottomCreatorDock(
    categories: List<CharacterCategory>,
    selectedCategory: CharacterCategory,
    characters: List<CharacterItem>,
    selectedCharacter: CharacterItem,
    languageMode: LanguageMode,
    isRecording: Boolean,
    onSelectCategory: (CharacterCategory) -> Unit,
    onSelectCharacter: (CharacterItem) -> Unit,
    onUploadFromGallery: () -> Unit,
    onOpenManualRigEditor: (CharacterItem) -> Unit,
    onCapturePhoto: () -> Unit,
    onToggleRecordVideo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
            .background(StudioSurfaceGlass)
            .border(
                width = 1.dp,
                color = StudioBorder,
                shape = RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp)
            )
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(top = 10.dp, bottom = 12.dp)
    ) {
        // 1. Category Filter Chips + Manual Rig Fine-Tune Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            LazyRow(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(end = 8.dp)
            ) {
                items(categories) { category ->
                    val isSelected = category == selectedCategory
                    val chipText = when (languageMode) {
                        LanguageMode.BILINGUAL -> "${category.labelBn} • ${category.labelEn}"
                        LanguageMode.BENGALI -> category.labelBn
                        LanguageMode.ENGLISH -> category.labelEn
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) ElectricCyan else StudioCard)
                            .border(
                                1.dp,
                                if (isSelected) ElectricCyan else StudioBorder,
                                RoundedCornerShape(16.dp)
                            )
                            .clickable { onSelectCategory(category) }
                            .padding(horizontal = 11.dp, vertical = 6.dp)
                            .testTag("category_chip_${category.name.lowercase()}")
                    ) {
                        Text(
                            text = chipText,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isSelected) StudioObsidian else TextAlabaster,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }
            }

            // Edit 5-Point Rig Button for active character
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(StudioViolet.copy(alpha = 0.22f))
                    .border(1.dp, StudioViolet, RoundedCornerShape(16.dp))
                    .clickable { onOpenManualRigEditor(selectedCharacter) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
                    .testTag("edit_rig_button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Edit 5-Point Rig / পাপেট এডিট",
                    tint = TextAlabaster,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when (languageMode) {
                        LanguageMode.BILINGUAL -> "রিগ এডিট / Rig"
                        LanguageMode.BENGALI -> "পাপেট রিগ"
                        LanguageMode.ENGLISH -> "Edit Rig"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextAlabaster,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 2. Character Carousel (+ Upload Button + Demo & Custom Characters)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("character_carousel"),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item(key = "upload_button_item") {
                UploadCharacterCard(
                    languageMode = languageMode,
                    onClick = onUploadFromGallery
                )
            }

            items(characters, key = { it.id }) { item ->
                CharacterCarouselCard(
                    item = item,
                    isSelected = item.id == selectedCharacter.id,
                    languageMode = languageMode,
                    onClick = { onSelectCharacter(item) }
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Shutter Row: Photo Capture Button + Central Video/Audio Record Button + Active Character Info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Photo Snapshot Button
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClick = onCapturePhoto)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .testTag("photo_capture_button")
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(StudioCard)
                        .border(1.5.dp, ElectricCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Capture Photo / ছবি তুলুন",
                        tint = ElectricCyan,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = when (languageMode) {
                        LanguageMode.BILINGUAL -> "ছবি / Photo"
                        LanguageMode.BENGALI -> "ছবি তুলুন"
                        LanguageMode.ENGLISH -> "Photo"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = TextAlabaster
                )
            }

            // Center: Primary Video + Microphone Audio Record Shutter Button
            val innerCorner by animateDpAsState(
                targetValue = if (isRecording) 10.dp else 32.dp,
                label = "shutter_corner"
            )
            val innerSize by animateDpAsState(
                targetValue = if (isRecording) 32.dp else 54.dp,
                label = "shutter_size"
            )
            val ringColor by animateColorAsState(
                targetValue = if (isRecording) CineRed else TextAlabaster,
                label = "shutter_ring"
            )

            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(68.dp)
                        .clip(CircleShape)
                        .border(3.dp, ringColor, CircleShape)
                        .clickable(onClick = onToggleRecordVideo)
                        .padding(6.dp)
                        .testTag("video_record_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(innerSize)
                            .clip(RoundedCornerShape(innerCorner))
                            .background(CineRed),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.Videocam,
                            contentDescription = if (isRecording) "Stop Recording / থামুন" else "Record Video / ভিডিও রেকর্ড",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = if (isRecording) {
                        when (languageMode) {
                            LanguageMode.BILINGUAL -> "থামুন / Stop"
                            LanguageMode.BENGALI -> "থামুন"
                            LanguageMode.ENGLISH -> "Stop"
                        }
                    } else {
                        when (languageMode) {
                            LanguageMode.BILINGUAL -> "ভিডিও রেকর্ড / Record"
                            LanguageMode.BENGALI -> "ভিডিও রেকর্ড"
                            LanguageMode.ENGLISH -> "Record Video"
                        }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isRecording) CineRed else TextAlabaster,
                    fontWeight = FontWeight.Bold
                )
            }

            // Right: Active Character Mode Summary Badge
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.width(96.dp)
            ) {
                Text(
                    text = selectedCharacter.nameBn,
                    style = MaterialTheme.typography.labelMedium,
                    color = TextAlabaster,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = selectedCharacter.nameEn,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSlate,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(
                            if (selectedCharacter.rigType == RigType.AUTO_MESH) {
                                ElectricCyan.copy(alpha = 0.18f)
                            } else {
                                StudioViolet.copy(alpha = 0.22f)
                            }
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (selectedCharacter.rigType == RigType.AUTO_MESH) "478 MESH" else "2D PUPPET",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (selectedCharacter.rigType == RigType.AUTO_MESH) ElectricCyan else StudioViolet
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadCharacterCard(
    languageMode: LanguageMode,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(StudioCard)
            .border(1.5.dp, ElectricCyan, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("upload_character_button"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(ElectricCyan.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.AddPhotoAlternate,
                contentDescription = "Upload Face / ছবি আপলোড",
                tint = ElectricCyan,
                modifier = Modifier.size(26.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when (languageMode) {
                LanguageMode.BILINGUAL -> "+ আপলোড\nUpload"
                LanguageMode.BENGALI -> "+ আপলোড\nগ্যালারি"
                LanguageMode.ENGLISH -> "+ Upload\nGallery"
            },
            style = MaterialTheme.typography.labelSmall,
            color = ElectricCyan,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 12.sp
        )
    }
}

@Composable
private fun CharacterCarouselCard(
    item: CharacterItem,
    isSelected: Boolean,
    languageMode: LanguageMode,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnailBitmap: Bitmap = remember(item.id, item.customImagePath) {
        DemoCharacterCatalog.getCharacterBitmap(context, item)
    }
    val borderColor = when {
        isSelected -> ElectricCyan
        item.rigType == RigType.MANUAL_PUPPET -> StudioViolet.copy(alpha = 0.55f)
        else -> StudioBorder
    }

    Column(
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isSelected) ElectricCyan.copy(alpha = 0.16f) else StudioCard)
            .border(if (isSelected) 2.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 7.dp, horizontal = 4.dp)
            .testTag("character_card_${item.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(StudioObsidian)
                .border(1.dp, borderColor, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = thumbnailBitmap.asImageBitmap(),
                contentDescription = item.nameEn,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = item.shortLabel(languageMode),
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) ElectricCyan else TextAlabaster,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 11.sp
        )
    }
}
