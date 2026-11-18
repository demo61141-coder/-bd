package com.example.ui.components

import android.content.Context
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.model.CharacterItem
import com.example.model.StudioCaptureItem
import com.example.ui.theme.CineRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextAlabaster
import com.example.ui.theme.TextSlate
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun StudioGallerySheet(
    captures: List<StudioCaptureItem>,
    customCharacters: List<CharacterItem>,
    onDeleteCapture: (StudioCaptureItem) -> Unit,
    onDeleteCustomCharacter: (CharacterItem) -> Unit,
    onEditCustomCharacter: (CharacterItem) -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(StudioSurface)
            .border(1.dp, StudioBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(20.dp)
            .testTag("studio_gallery_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "স্টুডিও গ্যালারি / Studio Captures & Custom Faces",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextAlabaster,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Saved directly to Phone Gallery (DCIM/FaceSwapCam) & Studio",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldReady
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_gallery_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close / বন্ধ করুন",
                    tint = TextAlabaster
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (customCharacters.isNotEmpty()) {
                item {
                    Text(
                        text = "আপলোড করা ক্যারেক্টার / Saved Custom Characters (${customCharacters.size})",
                        style = MaterialTheme.typography.labelLarge,
                        color = StudioViolet,
                        fontWeight = FontWeight.Bold
                    )
                }
                items(customCharacters, key = { "custom_${it.id}" }) { charItem ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(StudioCard)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${charItem.nameBn} • ${charItem.nameEn}",
                                style = MaterialTheme.typography.labelLarge,
                                color = TextAlabaster
                            )
                            Text(
                                text = "${charItem.rigType.badgeBn} / ${charItem.rigType.badgeEn}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ElectricCyan
                            )
                        }
                        IconButton(onClick = {
                            onClose()
                            onEditCustomCharacter(charItem)
                        }) {
                            Text("Edit", color = ElectricCyan, style = MaterialTheme.typography.labelMedium)
                        }
                        IconButton(onClick = { onDeleteCustomCharacter(charItem) }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Delete Character",
                                tint = CineRed
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "রেকর্ড করা ভিডিও ও ছবি / Captured Videos & Photos (${captures.size})",
                    style = MaterialTheme.typography.labelLarge,
                    color = ElectricCyan,
                    fontWeight = FontWeight.Bold
                )
            }

            if (captures.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(StudioCard)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "এখনো কোনো ছবি বা ভিডিও তোলা হয়নি। ক্যামেরা স্ক্রিনে ছবি বা রেকর্ড বাটনে ট্যাপ করুন!\nNo captures yet — tap Photo or Record Video on the camera screen!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSlate
                        )
                    }
                }
            } else {
                items(captures, key = { it.id }) { item ->
                    CaptureRowCard(
                        item = item,
                        onShare = { shareCaptureItem(context, item) },
                        onDelete = { onDeleteCapture(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CaptureRowCard(
    item: StudioCaptureItem,
    onShare: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(item.createdAt))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioCard)
            .border(1.dp, StudioBorder, RoundedCornerShape(16.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(
                        if (item.isVideo) CineRed.copy(alpha = 0.2f) else ElectricCyan.copy(alpha = 0.2f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (item.isVideo) Icons.Default.Videocam else Icons.Default.Image,
                    contentDescription = null,
                    tint = if (item.isVideo) CineRed else ElectricCyan
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = if (item.isVideo) {
                        "ভিডিও / Video • ${item.characterName} (${(item.durationMs / 1000).coerceAtLeast(1)}s)"
                    } else {
                        "ছবি / Photo • ${item.characterName}"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = TextAlabaster,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$formattedDate • গ্যালারিতে সেভড / Saved to Gallery",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldReady
                )
            }
        }

        Row {
            IconButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share / শেয়ার",
                    tint = ElectricCyan
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.DeleteOutline,
                    contentDescription = "Delete / মুছুন",
                    tint = CineRed
                )
            }
        }
    }
}

private fun shareCaptureItem(context: Context, item: StudioCaptureItem) {
    runCatching {
        val file = File(item.filePath)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val mime = if (item.isVideo) "video/mp4" else "image/jpeg"
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share FaceSwap Capture"))
    }
}
