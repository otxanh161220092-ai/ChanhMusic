/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop

/**
 * Floating back button in the top-left corner. It is real liquid glass: it samples the root backdrop
 * (the same one the bottom bar and mini player use), and it sits outside the navigation content, so it
 * never records itself. The chevron is drawn with [BlendMode.Difference] over white, so it turns black
 * on light parts of the screen and white on dark parts, based on what is actually behind it.
 */
@Composable
fun GlassBackButton(
    backdrop: LayerBackdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .glassSurface(
                    backdrop = backdrop,
                    shape = CircleShape,
                    fallbackColor = Color.White.copy(alpha = 0.15f),
                    tint = Color.White,
                    mask = Color(0xFFD9D9D9).copy(alpha = 0.3f),
                )
                .highlightBorder()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier =
                Modifier
                    .size(20.dp)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val chevron =
                Path().apply {
                    moveTo(size.width * 0.7f, size.height * 0.08f)
                    lineTo(size.width * 0.28f, size.height * 0.5f)
                    lineTo(size.width * 0.7f, size.height * 0.92f)
                }
            drawPath(
                path = chevron,
                color = Color.White,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                blendMode = BlendMode.Difference,
            )
        }
    }
}

/** Global switch for the per-screen top-bar back icons. Off: only the floating glass back button navigates back. */
object FloatingBackState {
    var showOriginalBack by mutableStateOf(false)
}
