package com.example.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.model.LanguageMode
import com.example.model.StudioSettings
import com.example.model.VideoResolution
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextAlabaster
import com.example.ui.theme.TextSlate

@Composable
fun SettingsSheet(
    settings: StudioSettings,
    onFlipCamera: () -> Unit,
    onSmoothingChanged: (Float) -> Unit,
    onBlendOpacityChanged: (Float) -> Unit,
    onResolutionChanged: (VideoResolution) -> Unit,
    onMirrorChanged: (Boolean) -> Unit,
    onDetailedMouthChanged: (Boolean) -> Unit,
    onShowWireframeChanged: (Boolean) -> Unit,
    onUseDemoMotionChanged: (Boolean) -> Unit,
    onLanguageModeChanged: (LanguageMode) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(StudioSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
            .testTag("settings_sheet")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "স্টুডিও সেটিংস / Studio Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextAlabaster,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Camera, One Euro Filter, Blend & Mouth Rig",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Settings / বন্ধ করুন",
                    tint = TextAlabaster
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // 1. Camera Lens Flip
        SettingToggleRow(
            titleBnEn = "সামনের ক্যামেরা / Front Selfie Camera",
            subtitleBnEn = if (settings.isFrontCamera) {
                "সক্রিয়: ফ্রন্ট ক্যামেরা / Active: Front Camera"
            } else {
                "সক্রিয়: ব্যাক ক্যামেরা / Active: Rear Camera"
            },
            checked = settings.isFrontCamera,
            onCheckedChange = { onFlipCamera() },
            tag = "setting_camera_flip_switch"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Mirror Toggle
        SettingToggleRow(
            titleBnEn = "মিরর ভিউ / Mirror Camera & Landmarks",
            subtitleBnEn = "আয়নার মতো ডান-বাম মিরর করবে / Flip horizontal preview symmetrically",
            checked = settings.isMirrored,
            onCheckedChange = onMirrorChanged,
            tag = "setting_mirror_switch"
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 3. One Euro Filter Smoothing Strength Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(StudioCard)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "স্মুথিং (One Euro Filter) / Smoothing",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextAlabaster
                )
                Text(
                    text = "${(settings.smoothingStrength * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "মুখের কাঁপুনি ও ড্রিফট বন্ধ করে / Eliminates landmark jitter while keeping fast expressions responsive",
                style = MaterialTheme.typography.labelSmall,
                color = TextSlate
            )
            Slider(
                value = settings.smoothingStrength,
                onValueChange = onSmoothingChanged,
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan
                ),
                modifier = Modifier.testTag("smoothing_slider")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 4. Blend / Opacity Slider
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(StudioCard)
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "ব্লেন্ড ওপাসিটি / Character Blend Opacity",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextAlabaster
                )
                Text(
                    text = "${(settings.blendOpacity * 100).toInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = "ক্যারেক্টার মাস্কের স্বচ্ছতা নিয়ন্ত্রণ করুন / Adjust transparency of the warped face mask",
                style = MaterialTheme.typography.labelSmall,
                color = TextSlate
            )
            Slider(
                value = settings.blendOpacity,
                onValueChange = onBlendOpacityChanged,
                valueRange = 0.15f..1f,
                colors = SliderDefaults.colors(
                    thumbColor = ElectricCyan,
                    activeTrackColor = ElectricCyan
                ),
                modifier = Modifier.testTag("opacity_slider")
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 5. Toggleable Mouth Interior Mode (Confirmed User Choice!)
        SettingToggleRow(
            titleBnEn = "মুখের ভেতর দাঁত ও জিহ্বা / Detailed Mouth Interior",
            subtitleBnEn = if (settings.detailedMouthInterior) {
                "চালু: মুখ খুললে অন্ধকার গহ্বর + দাঁত ও জিহ্বা দেখাবে / Dark cavity with subtle teeth & tongue"
            } else {
                "বন্ধ: মুখ খুললে শুধু সাধারণ কালো গহ্বর দেখাবে / Simple dark mouth cavity only"
            },
            checked = settings.detailedMouthInterior,
            onCheckedChange = onDetailedMouthChanged,
            tag = "setting_mouth_detail_switch"
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 6. Resolution Selector (720p HD vs 1080p FHD)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(StudioCard)
                .padding(14.dp)
        ) {
            Text(
                text = "ভিডিও ও ছবির রেজোলিউশন / Output Resolution",
                style = MaterialTheme.typography.labelLarge,
                color = TextAlabaster
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                VideoResolution.entries.forEach { res ->
                    val active = settings.resolution == res
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (active) ElectricCyan else StudioObsidian)
                            .border(
                                1.dp,
                                if (active) ElectricCyan else StudioBorder,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onResolutionChanged(res) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${res.labelBn} • ${res.labelEn}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (active) StudioObsidian else TextAlabaster,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 7. Wireframe Mesh Debug & Auto Demo Motion toggles
        SettingToggleRow(
            titleBnEn = "৪৭৮ মেস ওয়্যারফ্রেম / Show 478-Mesh Wireframe",
            subtitleBnEn = "লাইভ ট্রায়াঙ্গুলেশন জাল ও ল্যান্ডমার্ক দেখুন / Overlay Delaunay mesh triangles",
            checked = settings.showMeshWireframe,
            onCheckedChange = onShowWireframeChanged,
            tag = "setting_wireframe_switch"
        )

        Spacer(modifier = Modifier.height(10.dp))

        SettingToggleRow(
            titleBnEn = "অটো এক্সপ্রেশন প্রিভিউ / Demo Expression Motion",
            subtitleBnEn = "ক্যামেরার সামনে কেউ না থাকলেও অটো কথা ও চোখের পলক দেখাবে / Simulate live talking & blinking",
            checked = settings.useDemoMotionFeed,
            onCheckedChange = onUseDemoMotionChanged,
            tag = "setting_demo_motion_switch"
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = StudioBorder)
        Spacer(modifier = Modifier.height(12.dp))

        // 8. Language Display Mode
        Text(
            text = "ভাষা প্রদর্শন / UI Language Display",
            style = MaterialTheme.typography.labelLarge,
            color = TextAlabaster
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            LanguageMode.entries.forEach { mode ->
                val selected = settings.languageMode == mode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) StudioViolet else StudioCard)
                        .clickable { onLanguageModeChanged(mode) }
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextAlabaster,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingToggleRow(
    titleBnEn: String,
    subtitleBnEn: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    tag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioCard)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(
                text = titleBnEn,
                style = MaterialTheme.typography.labelLarge,
                color = TextAlabaster
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitleBnEn,
                style = MaterialTheme.typography.labelSmall,
                color = TextSlate
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = StudioObsidian,
                checkedTrackColor = ElectricCyan
            ),
            modifier = Modifier.testTag(tag)
        )
    }
}
