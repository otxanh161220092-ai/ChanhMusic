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
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware

private val MAX_BLUR = 110.dp

/** Pixel-by-pixel blur: sharp to the middle of the height, then blurs quickly to [maxRadius] below it. */
private const val VARIABLE_BLUR_AGSL = """
uniform shader image;
uniform float2 size;
uniform float maxRadius;

half4 main(float2 coord) {
    float t = clamp((coord.y / size.y - 0.5) / 0.15, 0.0, 1.0);
    float r = t * maxRadius;
    if (r < 1.0) {
        return image.eval(coord);
    }
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
    return sum / half(total);
}
"""

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun variableBlurEffect(
    width: Float,
    height: Float,
    maxRadiusPx: Float,
): RenderEffect {
    val shader = RuntimeShader(VARIABLE_BLUR_AGSL)
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("maxRadius", maxRadiusPx)
    return RenderEffect.createRuntimeShaderEffect(shader, "image")
}

/** Each band blurs more than the one above and takes over from [fromFraction] of the height downward. */
private data class BlurBand(val radius: Dp, val fromFraction: Float)

private val blurBands =
    listOf(
        BlurBand(radius = 4.dp, fromFraction = 0.15f),
        BlurBand(radius = 18.dp, fromFraction = 0.35f),
        BlurBand(radius = 48.dp, fromFraction = 0.55f),
        BlurBand(radius = 110.dp, fromFraction = 0.75f),
    )

/**
 * Full-bleed cover: sharp at the top, blurring more the lower it goes. The cover is cropped to fill
 * the screen, so its sides run past the edges. Android 13+ gets a continuous blur; older versions get
 * stacked bands.
 */
@Composable
fun ProgressiveBlurArtwork(
    artwork: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sharp = remember(artwork) { ImageRequest.Builder(context).data(artwork).size(400, 400).allowHardware(false).build() }
    val blurred =
        remember(artwork) { ImageRequest.Builder(context).data(artwork).size(200, 200).allowHardware(false).build() }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val useRuntimeBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    val runtimeBlur =
        remember(size, density) {
            if (useRuntimeBlur && size.width > 0 && size.height > 0) {
                variableBlurEffect(size.width.toFloat(), size.height.toFloat(), with(density) { MAX_BLUR.toPx() })
                    .asComposeRenderEffect()
            } else {
                null
            }
        }

    Box(modifier = modifier.fillMaxSize().onSizeChanged { size = it }) {
        AsyncImage(
            model = sharp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().graphicsLayer { renderEffect = runtimeBlur },
        )
        if (runtimeBlur == null) {
            blurBands.forEach { band ->
                AsyncImage(
                    model = blurred,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                            .blur(band.radius)
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    Brush.verticalGradient(
                                        0f to Color.Transparent,
                                        band.fromFraction to Color.Transparent,
                                        (band.fromFraction + 0.15f).coerceAtMost(1f) to Color.Black,
                                        1f to Color.Black,
                                    ),
                                    blendMode = BlendMode.DstIn,
                                )
                            },
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.6f to Color.Black.copy(alpha = 0.1f),
                            1f to Color.Black.copy(alpha = 0.3f),
                        ),
                    ),
        )
    }
}
