/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware

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
 * the screen, so its sides run past the edges.
 */
@Composable
fun ProgressiveBlurArtwork(
    artwork: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sharp = remember(artwork) { ImageRequest.Builder(context).data(artwork).size(400, 400).allowHardware(false).build() }
    val blurred =
        remember(artwork) { ImageRequest.Builder(context).data(artwork).size(200, 200).allowHardware(false).build() }

    Box(modifier = modifier.fillMaxSize()) {
        AsyncImage(
            model = sharp,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
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
