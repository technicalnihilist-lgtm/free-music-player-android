package com.freemusic.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val FreeMusicColors = darkColorScheme(
    primary = Color(0xFF7C4DFF),
    onPrimary = Color.White,
    secondary = Color(0xFF00E0B8),
    onSecondary = Color(0xFF00251E),
    background = Color(0xFF0B0B10),
    onBackground = Color(0xFFEDEDF2),
    surface = Color(0xFF16161F),
    onSurface = Color(0xFFEDEDF2),
    surfaceVariant = Color(0xFF22222E),
    onSurfaceVariant = Color(0xFFB6B6C4),
    error = Color(0xFFFF6B6B)
)

@Composable
fun FreeMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FreeMusicColors, content = content)
}
