/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Colour for small secondary text (subtitles, metadata). On a dark background it is a light grey
 * (whiter than grey); on a light background it is a dark grey (blacker than grey).
 */
@Composable
@ReadOnlyComposable
fun subtitleGrayColor(): Color {
    val background = MaterialTheme.colorScheme.background
    return if (background.luminance() < 0.5f) Color(0xFFBDBDBD) else Color(0xFF4A4A4A)
}
