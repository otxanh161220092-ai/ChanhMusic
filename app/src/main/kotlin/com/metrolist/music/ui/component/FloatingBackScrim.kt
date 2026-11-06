/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Dark fade behind the floating back button, extending up behind the status bar. Same gradient as the
 * home header so the button stays readable over any cover art.
 */
@Composable
fun FloatingBackScrim(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.85f),
                        0.6f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ).windowInsetsPadding(WindowInsets.statusBars)
                // Covers the 48dp back button (8dp top margin) below the status bar.
                .padding(bottom = 72.dp),
    )
}
