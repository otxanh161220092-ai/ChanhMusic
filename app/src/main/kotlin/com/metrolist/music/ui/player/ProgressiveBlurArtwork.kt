/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import kotlin.math.roundToInt

private val MAX_BLUR = 110.dp

private val BACKGROUND_BLUR = 100.dp

/** Rows of the cover stacked down; each odd row is a mirror of the one above. */
private const val MIRROR_ROWS = 4

/** The cover is enlarged past the screen width, so its sides are cropped and it reaches further down. */
private const val COVER_SCALE = 1.5f

/**
 * Pixel-by-pixel blur. The image stays sharp down to [squareBottom]; below it, it softens over [softPx]
 * into a blur whose radius grows to [maxRadius].
 */
private const val VARIABLE_BLUR_AGSL = """
uniform shader image;
uniform float maxRadius;
uniform float squareBottom;
uniform float softPx;

half4 main(float2 coord) {
    float t = clamp((coord.y - squareBottom) / softPx, 0.0, 1.0);
    half4 sharp = image.eval(coord);
    if (t <= 0.0) {
        return sharp;
    }
    float r = t * maxRadius;
    half4 sum = half4(0.0);
    float total = 0.0;
    for (int i = -4; i <= 4; i++) {
        for (int j = -4; j <= 4; j++) {
            float2 o = float2(float(i), float(j)) * (r / 4.0);
            float w = exp(-dot(o, o) / (0.5 * r * r + 1.0));
            sum += image.eval(coord + o) * half(w);
            total += w;
        }
    }
    return mix(sharp, sum / half(total), half(t));
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun variableBlurEffect(
    maxRadiusPx: Float,
    squareBottomPx: Float,
    softPx: Float,
): RenderEffect {
    val shader = RuntimeShader(VARIABLE_BLUR_AGSL)
    shader.setFloatUniform("maxRadius", maxRadiusPx)
    shader.setFloatUniform("squareBottom", squareBottomPx)
    shader.setFloatUniform("softPx", softPx)
    return RenderEffect.createRuntimeShaderEffect(shader, "image")
}


/** The cover as a square [COVER_SCALE] times the width, repeated down with every other row mirrored. */
@Composable
private fun MirroredCoverStack(
    request: ImageRequest,
    modifier: Modifier = Modifier,
) {
    Layout(
        modifier = modifier.clipToBounds(),
        content = {
            repeat(MIRROR_ROWS) { row ->
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.graphicsLayer { scaleY = if (row % 2 == 1) -1f else 1f },
                )
            }
        },
    ) { measurables, constraints ->
        val side = (constraints.maxWidth * COVER_SCALE).roundToInt()
        val left = (constraints.maxWidth - side) / 2
        val placeables = measurables.map { it.measure(Constraints.fixed(side, side)) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { row, placeable -> placeable.place(left, row * side) }
        }
    }
}

/**
 * The current track's cover: enlarged past the screen width, sharp at the top, with mirrored copies
 * below that soften into blur toward the bottom.
 */
@Composable
fun StackedCoverArtwork(
    artwork: String?,
    modifier: Modifier = Modifier,
) {
    if (artwork == null) return
    val context = LocalContext.current
    val density = LocalDensity.current
    val request = remember(artwork) { ImageRequest.Builder(context).data(artwork).size(400, 400).allowHardware(false).build() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val useRuntimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val runtimeBlur =
        remember(size, density) {
            if (useRuntimeBlur && size.width > 0) {
                val side = size.width * COVER_SCALE
                variableBlurEffect(
                    maxRadiusPx = with(density) { MAX_BLUR.toPx() },
                    squareBottomPx = side,
                    softPx = size.width * 0.2f,
                ).asComposeRenderEffect()
            } else {
                null
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { size = it }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val w = this.size.width
                    val h = this.size.height
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.Black,
                            (w * COVER_SCALE / h) to Color.Black,
                            (w * (COVER_SCALE + 0.5f) / h).coerceAtMost(1f) to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
    ) {
        MirroredCoverStack(
            request = request,
            modifier = Modifier.fillMaxSize().graphicsLayer { renderEffect = runtimeBlur },
        )
    }
}

/** Full-bleed background: the cover blurred as one soft field across the whole screen. */
@Composable
fun ProgressiveBlurArtwork(
    artwork: String?,
    modifier: Modifier = Modifier,
) {
    if (artwork == null) return
    val context = LocalContext.current
    val request =
        remember(artwork) { ImageRequest.Builder(context).data(artwork).size(200, 200).allowHardware(false).build() }
    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(BACKGROUND_BLUR),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.1f),
                            1f to Color.Black.copy(alpha = 0.3f),
                        ),
                    ),
        )
    }
}
