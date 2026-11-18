package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AmberWarn
import com.example.ui.theme.CineRed
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldReady
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioCard
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioViolet
import com.example.ui.theme.TextAlabaster
import com.example.ui.theme.TextSlate

@Composable
fun CreatorGuideSheet(
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
            .testTag("creator_guide_sheet")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "ক্রিয়েটর গাইড / Creator Face Guide",
                    style = MaterialTheme.typography.titleLarge,
                    color = TextAlabaster,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "কোন ছবি সবচেয়ে ভালো কাজ করে / Which faces work best & PNG tips",
                    style = MaterialTheme.typography.labelSmall,
                    color = ElectricCyan
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.testTag("close_guide_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Guide / বন্ধ করুন",
                    tint = TextAlabaster
                )
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Section 1: Best Realistic Faces
        GuideSectionCard(
            icon = Icons.Default.CheckCircle,
            accentColor = EmeraldReady,
            titleBn = "১. বাস্তব মানুষের ছবি (Realistic 478-Mesh) — যা ভালো কাজ করে",
            titleEn = "1. What Works Best for Realistic Faces",
            bulletsBnEn = listOf(
                "সোজা সামনে তাকানো ছবি (Front-facing symmetrical face) — দুই চোখ ও ঠোঁট পরিষ্কার দেখা যায়।",
                "সমান ও পরিষ্কার আলো (Clear, even lighting) — মুখে কড়া ছায়া ছাড়া।",
                "সানগ্লাস বা মাস্ক ছাড়া (No sunglasses, hands, or masks covering eyes/mouth)।",
                "স্বাভাবিক বা বন্ধ ঠোঁট (Neutral or closed mouth) — যাতে আপনার মুখ খুললে ওয়ার্প নিখুঁত হয়।"
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Section 2: Faces to Avoid
        GuideSectionCard(
            icon = Icons.Default.ErrorOutline,
            accentColor = CineRed,
            titleBn = "২. যে ধরনের ছবি এড়িয়ে চলবেন",
            titleEn = "2. Which Images Don't Work Well",
            bulletsBnEn = listOf(
                "একপাশ থেকে তোলা ছবি (Extreme side profile or 3/4 tilted angles)।",
                "অতিরিক্ত ঘোলা বা অন্ধকার ছবি (Blurry, low-light, or heavily shadowed faces)।",
                "কালো সানগ্লাস বা হাত দিয়ে মুখ ঢাকা ছবি (Occluded eyes or mouth)।"
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Section 3: Cartoon & Animal Faces (2D Puppet Rig)
        GuideSectionCard(
            icon = Icons.Default.Pets,
            accentColor = StudioViolet,
            titleBn = "৩. কার্টুন ও প্রাণীর ছবি (Cartoon & Animal 2D Puppet)",
            titleEn = "3. Tips for Cartoon, Anime & Animal Faces",
            bulletsBnEn = listOf(
                "কার্টুন বা প্রাণীর ছবি আপলোড করলে অ্যাপ অটোমেটিক 'ম্যানুয়াল সেটআপ (Manual Setup)' ওপেন করবে।",
                "৫টি পয়েন্ট ট্যাপ বা ড্র্যাগ করুন: বাম চোখ (Left Eye), ডান চোখ (Right Eye), নাক (Nose), মুখের বাম (Mouth Left) ও মুখের ডান (Mouth Right)।",
                "চোখ ও মুখের রিজিয়ন বক্স (Draggable Boxes) অ্যাডজাস্ট করুন — আপনার চোখের পলকে (eyeBlink) চোখ বন্ধ হবে এবং মুখ খুললে (jawOpen) নিচের চোয়াল নড়বে।",
                "সোজা সামনে তাকানো কার্টুন বা প্রাণীর মুখ (যেমন বাঘ, বিড়াল, পান্ডা, অ্যানিমে) সবচেয়ে ভালো অ্যানিমেট হয়।"
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Section 4: Transparent PNG & Remove Background Hint
        GuideSectionCard(
            icon = Icons.Default.AutoFixHigh,
            accentColor = AmberWarn,
            titleBn = "৪. ট্রান্সপারেন্ট PNG ও ব্যাকগ্রাউন্ড রিমুভ টিপস",
            titleEn = "4. Transparent PNG & Remove Background Hint",
            bulletsBnEn = listOf(
                "কার্টুন ও প্রাণীর মাস্কের জন্য স্বচ্ছ ব্যাকগ্রাউন্ড (Transparent PNG) ব্যবহার করলে আপনার লাইভ ক্যামেরা ফিডের ওপর সবচেয়ে সুন্দর দেখায়।",
                "টিপস (Remove Background Hint): গ্যালারি থেকে আপলোড করার আগে ফোনের ফটো কাটআউট বা যেকোনো Background Remover টুল দিয়ে ছবির পেছনের ব্যাকগ্রাউন্ড মুছে PNG হিসেবে সেভ করে নিন।"
            )
        )
    }
}

@Composable
private fun GuideSectionCard(
    icon: ImageVector,
    accentColor: Color,
    titleBn: String,
    titleEn: String,
    bulletsBnEn: List<String>
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(StudioCard)
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    text = titleBn,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextAlabaster,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = titleEn,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        bulletsBnEn.forEach { line ->
            Row(
                modifier = Modifier.padding(vertical = 3.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "• ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accentColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSlate
                )
            }
        }
    }
}
