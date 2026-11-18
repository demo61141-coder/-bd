package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = StudioObsidian,
    primaryContainer = ElectricCyanDim,
    onPrimaryContainer = TextAlabaster,
    secondary = StudioViolet,
    onSecondary = TextAlabaster,
    tertiary = CineRed,
    onTertiary = TextAlabaster,
    background = StudioObsidian,
    onBackground = TextAlabaster,
    surface = StudioSurface,
    onSurface = TextAlabaster,
    surfaceVariant = StudioCard,
    onSurfaceVariant = TextSlate,
    outline = StudioBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content
    )
}
