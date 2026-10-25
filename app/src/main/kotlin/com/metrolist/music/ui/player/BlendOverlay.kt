/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Draws the content into a layer that is composited onto what is already behind it with [mode].
 * Use only on small elements: every blended element costs an extra layer.
 */
fun Modifier.blendOverlay(
    mode: BlendMode = BlendMode.Overlay,
    alpha: Float = 1f,
): Modifier =
    this.drawWithContent {
        drawIntoCanvas { canvas ->
            val paint =
                Paint().apply {
                    blendMode = mode
                    this.alpha = alpha
                }
            canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
            drawContent()
            canvas.restore()
        }
    }

/**
 * Vibrant "plus lighter" like look for white elements. Screen keeps white elements highlighting
 * whatever is behind them on both dark and bright artwork, where Overlay would vanish on dark areas.
 */
fun Modifier.vibrant(
    enabled: Boolean,
    alpha: Float = 1f,
): Modifier = if (enabled) blendOverlay(BlendMode.Screen, alpha) else this

/** Fades the bottom [fraction] of the content to transparent, revealing the background behind it. */
fun Modifier.fadeBottomEdge(fraction: Float = 0.4f): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to Color.Black,
                        (1f - fraction) to Color.Black,
                        1f to Color.Transparent,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
