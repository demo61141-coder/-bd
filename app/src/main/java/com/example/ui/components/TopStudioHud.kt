package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FaceRetouchingNatural
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.CharacterItem
import com.example.model.LanguageMode
import com.example.model.LiveFacePose
import com.example.model.RigType
import com.example.ui.theme.CineRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurfaceGlass
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextAlabaster
import com.example.ui.theme.TextSlate

@Composable
fun TopStudioHud(
    activeCharacter: CharacterItem,
    livePose: LiveFacePose,
    languageMode: LanguageMode,
    capturesCount: Int,
    isRecording: Boolean,
    recordingElapsedSec: Long,
    onFlipCamera: () -> Unit,
    onOpenStudioGallery: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand + Live Tracking Telemetry Pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(StudioSurfaceGlass)
                    .border(1.dp, StudioBorder, RoundedCornerShape(24.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(9.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isRecording -> CineRed
                                livePose.isTracking -> EmeraldReady
                                else -> com.example.ui.theme.AmberWarn
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = when (languageMode) {
                            LanguageMode.BILINGUAL -> "FaceSwap Cam • ফেসসোয়াপ"
                            LanguageMode.BENGALI -> "ফেসসোয়াপ ক্যাম স্টুডিও"
                            LanguageMode.ENGLISH -> "FaceSwap Cam Studio"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = TextAlabaster,
                        fontWeight = FontWeight.Bold
                    )
                    val statusText = if (livePose.isTracking) {
                        val rigLabel = if (activeCharacter.rigType == RigType.AUTO_MESH) "478 Mesh" else "2D Puppet"
                        "Tracking • ট্র্যাকিং ($rigLabel • ${livePose.fps} FPS)"
                    } else {
                        "মুখ খুঁজছে... • Point at Face (${livePose.fps} FPS)"
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (!livePose.isTracking) {
                            com.example.ui.theme.AmberWarn
                        } else if (activeCharacter.rigType == RigType.AUTO_MESH) {
                            ElectricCyan
                        } else {
                            StudioViolet
                        }
                    )
                }
            }

            // Action Icon Buttons: Camera Flip, Captures Gallery, Guide, Settings
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudIconButton(
                    icon = Icons.Default.Cameraswitch,
                    contentDesc = "Flip Camera / ক্যামেরা পরিবর্তন",
                    tag = "hud_flip_camera_button",
                    onClick = onFlipCamera
                )

                BadgedBox(
                    badge = {
                        if (capturesCount > 0) {
                            Badge(
                                containerColor = ElectricCyan,
                                contentColor = StudioObsidian
                            ) {
                                Text(
                                    text = capturesCount.toString(),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                ) {
                    HudIconButton(
                        icon = Icons.Default.PhotoLibrary,
                        contentDesc = "Studio Captures / স্টুডিও গ্যালারি",
                        tag = "hud_studio_gallery_button",
                        onClick = onOpenStudioGallery
                    )
                }

                HudIconButton(
                    icon = Icons.Default.HelpOutline,
                    contentDesc = "Creator Guide / নির্দেশিকা",
                    tag = "hud_guide_button",
                    onClick = onOpenGuide
                )

                HudIconButton(
                    icon = Icons.Default.Settings,
                    contentDesc = "Studio Settings / সেটিংস",
                    tag = "hud_settings_button",
                    onClick = onOpenSettings
                )
            }
        }

        // Active Recording Timer Banner
        AnimatedVisibility(
            visible = isRecording,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            val mins = recordingElapsedSec / 60
            val secs = recordingElapsedSec % 60
            val timeFormatted = "%02d:%02d".format(java.util.Locale.US, mins, secs)

            Row(
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(CineRed.copy(alpha = 0.90f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FaceRetouchingNatural,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "REC $timeFormatted • অডিও ও ভিডিও রেকর্ড হচ্ছে",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun HudIconButton(
    icon: ImageVector,
    contentDesc: String,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .size(44.dp)
            .clip(CircleShape)
            .background(StudioSurfaceGlass)
            .border(1.dp, StudioBorder, CircleShape)
            .clickable(onClick = onClick)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDesc,
            tint = TextAlabaster,
            modifier = Modifier.size(21.dp)
        )
    }
}
