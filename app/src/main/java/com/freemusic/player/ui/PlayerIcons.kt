package com.freemusic.player.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few player glyphs that material-icons-core does not ship.
 *
 * They are defined here on purpose: pulling in material-icons-extended for
 * half a dozen icons adds 15,000+ classes and several MB to the APK, and it
 * makes dexing very slow. These are the standard Material paths.
 */
object PlayerIcons {

    private fun vector(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black)
        ).build()

    val Pause: ImageVector by lazy {
        vector("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")
    }

    val SkipNext: ImageVector by lazy {
        vector("SkipNext", "M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z")
    }

    val SkipPrevious: ImageVector by lazy {
        vector("SkipPrevious", "M6 6h2v12H6zm3.5 6l8.5 6V6z")
    }

    val Shuffle: ImageVector by lazy {
        vector(
            "Shuffle",
            "M10.59 9.17L5.41 4 4 5.41l5.17 5.17 1.42-1.41zM14.5 4l2.04 2.04L4 18.59 " +
                "5.41 20 17.96 7.46 20 9.5V4h-5.5zm.33 9.41l-1.41 1.41 3.13 3.13L14.5 20H20v-5.5l" +
                "-2.04 2.04-3.13-3.13z"
        )
    }

    val Repeat: ImageVector by lazy {
        vector(
            "Repeat",
            "M7 7h10v3l4-4-4-4v3H5v6h2V7zm10 10H7v-3l-4 4 4 4v-3h12v-6h-2v4z"
        )
    }
}
