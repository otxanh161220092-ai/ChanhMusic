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
import androidx.compose.ui.graphics.lerp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Live mesh background in the spirit of Apple Music: many soft blobs, one per cover colour,
 * drift over a base tinted from the dominant colour. Every motion term completes a whole number
 * of cycles per loop, so the restart is invisible.
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
    val base = lerp(palette.first(), Color.Black, 0.7f)
    Box(modifier = modifier.fillMaxSize().background(base)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val radius = maxOf(w, h) * 0.6f
            val blobCount = 6
            for (i in 0 until blobCount) {
                val color = palette[i % palette.size]
                val phase = i * 1.3
                val cx = w * (0.5f + 0.45f * sin(2 * PI * (1 + i % 2) * time + phase).toFloat())
                val cy = h * (0.5f + 0.4f * cos(2 * PI * (2 + i % 3) * time + phase * 0.6).toFloat())
                drawRect(
                    brush =
                        Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0f)),
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
                            0f to Color.Black.copy(alpha = 0f),
                            0.6f to Color.Black.copy(alpha = 0.15f),
                            1f to Color.Black.copy(alpha = 0.4f),
                        ),
                    ),
        )
    }
}
