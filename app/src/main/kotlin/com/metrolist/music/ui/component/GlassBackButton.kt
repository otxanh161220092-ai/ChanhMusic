/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.metrolist.music.R
import kotlin.math.roundToInt

/**
 * Floating glass pill for two or more actions, drawn on the same layer as [GlassBackButton].
 * The glass is the back button's glass; the rim is [R.drawable.glass_ring_overlay] split into
 * left cap, right cap and a one-pixel centre column stretched across, so the rim keeps its
 * circular ends at any width instead of turning into an ellipse.
 */
@Composable
fun FloatingGlassPill(
    backdrop: LayerBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val ring = ImageBitmap.imageResource(R.drawable.glass_ring_overlay)
    Box(
        modifier =
            modifier
                .height(48.dp)
                .clip(shape)
                .glassSurface(
                    backdrop = backdrop,
                    shape = shape,
                    fallbackColor = Color.White.copy(alpha = 0.15f),
                    tint = Color.White,
                    mask = Color(0xFFD9D9D9).copy(alpha = 0.3f),
                    lensDp = 6f,
                ),
    ) {
        Row(
            modifier =
                Modifier
                    .height(48.dp)
                    .drawWithContent {
                        drawContent()
                        drawRingPill(ring)
                    },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            content()
        }
    }
}

private fun DrawScope.drawRingPill(ring: ImageBitmap) {
    val h = size.height.roundToInt()
    val w = size.width.roundToInt()
    val src = ring.width
    val half = src / 2
    val cap = h / 2
    // Left cap: left half of the ring at its natural proportion.
    drawImage(
        image = ring,
        srcOffset = IntOffset(0, 0),
        srcSize = IntSize(half, ring.height),
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(cap, h),
    )
    // Right cap: right half.
    drawImage(
        image = ring,
        srcOffset = IntOffset(half, 0),
        srcSize = IntSize(half, ring.height),
        dstOffset = IntOffset(w - cap, 0),
        dstSize = IntSize(cap, h),
    )
    // Middle: the top/bottom arc sits in the centre column; stretch that column across the gap.
    if (w - 2 * cap > 0) {
        drawImage(
            image = ring,
            srcOffset = IntOffset(half - 1, 0),
            srcSize = IntSize(2, ring.height),
            dstOffset = IntOffset(cap, 0),
            dstSize = IntSize(w - 2 * cap, h),
        )
    }
}

/**
 * Floating back button in the top-left corner. It is real liquid glass: it samples the root backdrop
 * (the same one the bottom bar and mini player use), and it sits outside the navigation content, so it
 * never records itself. The chevron is pure black or pure white, chosen from the average luminance of
 * the real pixels directly beneath the button (read from the window with [PixelCopy]).
 */
@Composable
fun GlassBackButton(
    backdrop: LayerBackdrop?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var bounds by remember { mutableStateOf<Rect?>(null) }
    val backgroundIsLight by rememberBackdropIsLight(bounds)

    Box(
        modifier =
            modifier
                .size(48.dp)
                .onGloballyPositioned { bounds = it.windowRect() }
                .clip(CircleShape)
                .glassSurface(
                    backdrop = backdrop,
                    shape = CircleShape,
                    fallbackColor = Color.White.copy(alpha = 0.15f),
                    tint = Color.White,
                    mask = Color(0xFFD9D9D9).copy(alpha = 0.3f),
                    // A small lens keeps the edge refraction without magnifying the content behind the button.
                    lensDp = 6f,
                )
                .highlightBorder()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        val chevronColor = Color.Black
        Canvas(modifier = Modifier.size(20.dp)) {
            val chevron =
                Path().apply {
                    moveTo(size.width * 0.7f, size.height * 0.08f)
                    lineTo(size.width * 0.28f, size.height * 0.5f)
                    lineTo(size.width * 0.7f, size.height * 0.92f)
                }
            drawPath(
                path = chevron,
                color = chevronColor,
                style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/** Global switch for the per-screen top-bar back icons. Off: only the floating glass back button navigates back. */
object FloatingBackState {
    var showOriginalBack by mutableStateOf(false)
    /** Actions a screen wants shown in the floating pill on the top-right, on the same layer as the back button. */
    var floatingActions: (@Composable () -> Unit)? by mutableStateOf(null)
    /** True while the floating back button is on screen; the title slot then leaves room for it. */
    var floatingVisible by mutableStateOf(false)
}

/** Stands in for a screen's original navigation icon: renders it only when enabled, otherwise reserves the button's space when the floating back button is showing. */
@Composable
fun OriginalBackSlot(content: @Composable () -> Unit) {
    if (FloatingBackState.showOriginalBack) {
        content()
    } else if (FloatingBackState.floatingVisible) {
        Spacer(Modifier.size(72.dp))
    }
}

/** Places the floating actions at the top-right, level with the floating back button. */
@Composable
fun FloatingActionsSlot(actions: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(end = 16.dp, top = 8.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        actions()
    }
}
