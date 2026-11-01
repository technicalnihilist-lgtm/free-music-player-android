package com.freemusic.player.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Palette from the "liquid glass" design: near-black canvas, violet primary,
 *  mint accent and a pink highlight. */
object Palette {
    val Bg = Color(0xFF07070D)
    val Text = Color(0xFFF5F5FA)
    val Muted = Color(0x9EEBEBF5)
    val Primary = Color(0xFF8A5CFF)
    val Accent = Color(0xFF2DF5C8)
    val Pink = Color(0xFFFF5CA8)
    val GlassTop = Color(0x2BFFFFFF)
    val GlassBottom = Color(0x0DFFFFFF)
    val Hairline = Color(0x38FFFFFF)
}

private val FreeMusicColors = darkColorScheme(
    primary = Palette.Primary,
    onPrimary = Color.White,
    secondary = Palette.Accent,
    onSecondary = Color(0xFF00251E),
    background = Palette.Bg,
    onBackground = Palette.Text,
    surface = Color(0xFF101018),
    onSurface = Palette.Text,
    surfaceVariant = Color(0xFF1A1A26),
    onSurfaceVariant = Palette.Muted,
    error = Color(0xFFFF6B6B)
)

@Composable
fun FreeMusicTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = FreeMusicColors, content = content)
}
