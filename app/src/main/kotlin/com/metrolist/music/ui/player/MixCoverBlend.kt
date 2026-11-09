/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.metrolist.music.playback.automix.MixVisual
import kotlin.math.roundToInt

private const val GRID = 32
private const val COARSE = 7

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

private fun easeInOutCubic(t: Float): Float =
    if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).let { it * it * it } / 2f

/**
 * The service reports progress in coarse steps. A non-bouncing spring follows each step on the frame
 * clock, so the motion stays continuous and eases into every new target. Read it only in draw or
 * layout lambdas, so the motion does not recompose the tree every frame.
 */
@Composable
private fun rememberMixProgress(target: Float): Animatable<Float, AnimationVector1D> {
    val clamped = target.coerceIn(0f, 1f)
    val animated = remember { Animatable(clamped) }
    LaunchedEffect(clamped) {
        animated.animateTo(
            clamped,
            spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
        )
    }
    return animated
}

/**
 * The cover at [scale] of the cell, centred, with mirror images filling the 3x3 cells around it.
 * Cells are placed in the layout phase, so the scale changes without recomposition.
 */
@Composable
private fun MirroredArtwork(
    artwork: String?,
    contentScale: ContentScale,
    scale: () -> Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val request = remember(artwork) { ImageRequest.Builder(context).data(artwork).build() }
    Layout(
        modifier = modifier,
        content = {
            for (row in -1..1) {
                for (col in -1..1) {
                    Box(
                        modifier =
                            Modifier.graphicsLayer {
                                scaleX = if (col != 0) -1f else 1f
                                scaleY = if (row != 0) -1f else 1f
                            },
                    ) {
                        AsyncImage(
                            model = request,
                            contentDescription = null,
                            contentScale = contentScale,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val height = constraints.maxHeight
        val s = scale()
        val cellW = (width * s).roundToInt()
        val cellH = (height * s).roundToInt()
        val originX = (width - cellW) / 2
        val originY = (height - cellH) / 2
        val cellConstraints = Constraints.fixed(cellW, cellH)
        val placeables = measurables.map { it.measure(cellConstraints) }
        layout(width, height) {
            var i = 0
            for (row in -1..1) {
                for (col in -1..1) {
                    placeables[i++].place(originX + cellW * col, originY + cellH * row)
                }
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
    val progress = rememberMixProgress(visual.progress)
    val contentScale = if (cropArtwork) ContentScale.Crop else ContentScale.Fit

    Box(modifier = modifier.clipToBounds()) {
        MirroredArtwork(
            artwork = visual.outgoingArtwork,
            contentScale = contentScale,
            scale = { 1f - 0.2f * easeInOutCubic(progress.value) },
            modifier =
                Modifier.fillMaxSize().graphicsLayer {
                    val p = ease(progress.value)
                    alpha = 1f - p
                    renderEffect = if (blurEnabled && p > 0.01f) BlurEffect(28f * density * p, 28f * density * p) else null
                },
        )
        MirroredArtwork(
            artwork = visual.incomingArtwork,
            contentScale = contentScale,
            scale = { 0.88f + 0.12f * easeInOutCubic(progress.value) },
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        val p = ease(progress.value)
                        renderEffect = if (blurEnabled && p < 0.99f) BlurEffect(28f * density * (1f - p), 28f * density * (1f - p)) else null
                    }.drawWithContent {
                        drawContent()
                        drawImage(
                            image = mottledMask(ease(progress.value)),
                            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                            filterQuality = FilterQuality.High,
                            blendMode = BlendMode.DstIn,
                        )
                    },
        )
    }
}

private fun backgroundRequest(context: Context, artwork: String?) =
    ImageRequest
        .Builder(context)
        .data(artwork)
        .size(100, 100)
        .allowHardware(false)
        .build()

/** The blurred player background following the same mix: the incoming colours rise over the outgoing ones. */
@Composable
fun MixBackgroundBlend(
    visual: MixVisual,
    backgroundAlpha: Float,
    blurRadius: Dp,
) {
    val context = LocalContext.current
    val progress = rememberMixProgress(visual.progress)
    val outgoing = remember(visual.outgoingArtwork) { backgroundRequest(context, visual.outgoingArtwork) }
    val incoming = remember(visual.incomingArtwork) { backgroundRequest(context, visual.incomingArtwork) }
    Box(modifier = Modifier.alpha(backgroundAlpha)) {
        AsyncImage(
            model = outgoing,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().blur(blurRadius),
        )
        AsyncImage(
            model = incoming,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxSize()
                    .blur(blurRadius)
                    .graphicsLayer { alpha = ease(progress.value) },
        )
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
