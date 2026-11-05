/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.blur
import androidx.compose.foundation.background
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.metrolist.music.playback.automix.MixVisual

private const val GRID = 32
private const val COARSE = 7
private const val MASK_LEVELS = 96

/** Smooth blotchy noise in 0..1: random values on a coarse grid, bilinearly spread over the mask. */
private val mottle: FloatArray by lazy {
    val rnd = java.util.Random(11)
    val coarse = Array(COARSE) { FloatArray(COARSE) { rnd.nextFloat() } }
    FloatArray(GRID * GRID) { idx ->
        val x = (idx % GRID) / (GRID - 1f) * (COARSE - 1)
        val y = (idx / GRID) / (GRID - 1f) * (COARSE - 1)
        val x0 = x.toInt().coerceAtMost(COARSE - 2)
        val y0 = y.toInt().coerceAtMost(COARSE - 2)
        val fx = x - x0
        val fy = y - y0
        val top = coarse[y0][x0] * (1 - fx) + coarse[y0][x0 + 1] * fx
        val bottom = coarse[y0 + 1][x0] * (1 - fx) + coarse[y0 + 1][x0 + 1] * fx
        top * (1 - fy) + bottom * fy
    }
}

/** Alpha mask that reveals the incoming cover in patches: some blotches open early, others late. */
private fun mottledMask(progress: Float): ImageBitmap {
    val edge = 0.35f
    val pixels = IntArray(GRID * GRID)
    for (i in pixels.indices) {
        val reveal = ((progress * (1f + edge) - mottle[i]) / edge).coerceIn(0f, 1f)
        val alpha = (reveal * reveal * (3f - 2f * reveal) * 255f).toInt()
        pixels[i] = (alpha shl 24) or 0xFFFFFF
    }
    val bitmap = Bitmap.createBitmap(GRID, GRID, Bitmap.Config.ARGB_8888)
    bitmap.setPixels(pixels, 0, GRID, 0, 0, GRID, GRID)
    return bitmap.asImageBitmap()
}

private fun ease(t: Float): Float = t * t * (3f - 2f * t)

/**
 * The cover tiled as a mirrored 2x2 grid, so when it is scaled down the empty edges show mirrored
 * artwork instead of the background.
 */
@Composable
private fun MirroredArtwork(
    artwork: String?,
    contentScale: ContentScale,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val w = maxWidth
        val h = maxHeight
        @Composable
        fun Cell(flipX: Boolean, flipY: Boolean) {
            Box(
                modifier =
                    Modifier
                        .size(w, h)
                        .graphicsLayer {
                            scaleX = if (flipX) -1f else 1f
                            scaleY = if (flipY) -1f else 1f
                        },
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context).data(artwork).build(),
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(modifier = Modifier.requiredSize(w * 2, h * 2)) {
            Row(modifier = Modifier.requiredWidth(w * 2).height(h)) {
                Cell(flipX = false, flipY = false)
                Cell(flipX = true, flipY = false)
            }
            Row(modifier = Modifier.requiredWidth(w * 2).height(h)) {
                Cell(flipX = false, flipY = true)
                Cell(flipX = true, flipY = true)
            }
        }
    }
}

/**
 * The two covers while a mix runs: the outgoing one shrinks further, fades and blurs away while the
 * incoming one comes up from a slightly smaller size through blotchy patches out of a blur.
 */
@Composable
fun MixCoverBlend(
    visual: MixVisual,
    cropArtwork: Boolean,
    blurEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // The service reports progress in steps; a short tween between them keeps the motion continuous.
    val target = visual.progress.coerceIn(0f, 1f)
    val smoothed = remember { Animatable(target) }
    LaunchedEffect(target) { smoothed.animateTo(target, tween(durationMillis = 120, easing = LinearEasing)) }
    val p = ease(smoothed.value)
    val mask = remember((p * MASK_LEVELS).toInt()) { mottledMask(p) }
    val contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit

    Box(modifier = modifier.clipToBounds()) {
        MirroredArtwork(
            artwork = visual.outgoingArtwork,
            contentScale = contentScale,
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    alpha = 1f - p
                    val scale = 1f - 0.2f * p
                    scaleX = scale
                    scaleY = scale
                    renderEffect = if (blurEnabled && p > 0.01f) BlurEffect(28f * density * p, 28f * density * p) else null
                },
        )
        MirroredArtwork(
            artwork = visual.incomingArtwork,
            contentScale = contentScale,
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        val scale = 0.88f + 0.12f * p
                        scaleX = scale
                        scaleY = scale
                        renderEffect = if (blurEnabled && p < 0.99f) BlurEffect(28f * density * (1f - p), 28f * density * (1f - p)) else null
                    }.drawWithContent {
                        drawContent()
                        drawImage(
                            image = mask,
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.High,
                            blendMode = BlendMode.DstIn,
                        )
                    },
        )
    }
}

/** The blurred player background following the same mix: the incoming colours rise over the outgoing ones. */
@Composable
fun MixBackgroundBlend(
    visual: MixVisual,
    backgroundAlpha: Float,
    blurRadius: Dp,
) {
    val context = LocalContext.current
    val p = ease(visual.progress.coerceIn(0f, 1f))
    Box(modifier = Modifier.alpha(backgroundAlpha)) {
        for ((artwork, layerAlpha) in listOf(visual.outgoingArtwork to 1f, visual.incomingArtwork to p)) {
            AsyncImage(
                model =
                    ImageRequest
                        .Builder(context)
                        .data(artwork)
                        .size(100, 100)
                        .allowHardware(false)
                        .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(layerAlpha).blur(blurRadius),
            )
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
