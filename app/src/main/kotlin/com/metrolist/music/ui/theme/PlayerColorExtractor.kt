/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette

object PlayerColorExtractor {
    fun extractGradientColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> {
        val primaryColor = Color(palette.rankedColors(1, fallbackColor).first())
        return listOf(
            primaryColor,
            primaryColor.copy(
                red = primaryColor.red * 0.6f,
                green = primaryColor.green * 0.6f,
                blue = primaryColor.blue * 0.6f,
            ),
            Color.Black,
        )
    }

    fun extractMeshColors(
        palette: Palette,
        fallbackColor: Int,
    ): List<Color> {
        val colors =
            (palette.swatches.sortedByDescending { it.population }.map { it.rgb } + palette.rankedColors(6, fallbackColor).toList())
                .map { Color(it) }
                .distinct()
                .take(6)
        return colors.ifEmpty { listOf(Color(fallbackColor)) }
    }
}
