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
    passes: Int = 1,
): Modifier =
    this.drawWithContent {
        drawIntoCanvas { canvas ->
            val paint =
                Paint().apply {
                    blendMode = mode
                    this.alpha = alpha
                }
            repeat(passes) {
                canvas.saveLayer(Rect(0f, 0f, size.width, size.height), paint)
                drawContent()
                canvas.restore()
            }
        }
    }

/**
 * Overlay highlight for white elements: they brighten whatever is behind them instead of being flat
 * white. Two passes keep them visible on dark areas, where a single Overlay pass is faint.
 */
fun Modifier.vibrant(
    enabled: Boolean,
    alpha: Float = 1f,
): Modifier = if (enabled) blendOverlay(BlendMode.Overlay, alpha, passes = 2) else this

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

/** Fades the top and bottom [fraction] of the content to transparent, so scrolled lines dissolve at the edges. */
fun Modifier.fadeVerticalEdges(fraction: Float = 0.12f): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        fraction to Color.Black,
                        (1f - fraction) to Color.Black,
                        1f to Color.Transparent,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }

/** Like [fadeBottomEdge], but the bottom edge is a smile curve: higher at the sides, lower in the middle. */
fun Modifier.fadeBottomEdgeCurved(fraction: Float = 0.3f, sideCut: Float = 0.1f): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithContent {
            drawContent()
            val w = size.width
            val h = size.height
            drawRect(
                brush =
                    Brush.verticalGradient(
                        *Array(9) { i ->
                            val t = i / 8f
                            // Eased (smoothstep) fade from fully visible to transparent over the bottom [fraction].
                            val k = ((t - (1f - fraction)) / fraction).coerceIn(0f, 1f)
                            val eased = 1f - (k * k * (3f - 2f * k))
                            t to Color.Black.copy(alpha = eased)
                        },
                    ),
                blendMode = BlendMode.DstIn,
            )
            val yLeft = h * (1f - sideCut)
            val controlY = 2f * h - yLeft
            val below =
                androidx.compose.ui.graphics.Path().apply {
                    moveTo(0f, yLeft)
                    quadraticTo(w / 2f, controlY, w, yLeft)
                    lineTo(w, h)
                    lineTo(0f, h)
                    close()
                }
            drawPath(path = below, color = Color.Black, blendMode = BlendMode.DstOut)
        }
