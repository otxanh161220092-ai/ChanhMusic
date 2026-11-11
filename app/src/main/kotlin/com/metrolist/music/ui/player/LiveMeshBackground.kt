package com.metrolist.music.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Live mesh background in the spirit of Apple Music: soft blobs in the cover colours drift
 * slowly over a dark base, with a shade at the bottom for text legibility.
 */
@Composable
fun LiveMeshBackground(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val palette =
        if (colors.isEmpty()) listOf(Color(0xFF3B2A44), Color(0xFF1E2A3A), Color(0xFF3A2A22)) else colors
    val time by rememberInfiniteTransition(label = "liveMesh").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(24000, easing = LinearEasing), RepeatMode.Restart),
        label = "liveMeshTime",
    )
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val radius = maxOf(w, h) * 0.75f
            // Every term uses an integer number of cycles per loop, so the value at t=1 equals t=0
            // and the restart is invisible. Different integers per blob keep the motion uneven.
            palette.take(4).forEachIndexed { i, color ->
                val phase = i * 1.7
                val cx = w * (0.5f + 0.4f * sin(2 * PI * (1 + i % 2) * time + phase).toFloat())
                val cy = h * (0.45f + 0.3f * cos(2 * PI * (2 + i % 3) * time + phase * 0.5).toFloat())
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.85f), color.copy(alpha = 0f)),
                            center = Offset(cx, cy),
                            radius = radius,
                        ),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.1f),
                            0.55f to Color.Black.copy(alpha = 0.25f),
                            1f to Color.Black.copy(alpha = 0.5f),
                        ),
                    ),
        )
    }
}
