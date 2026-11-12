package com.metrolist.music.ui.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Rounded rectangle with smoother corners than a circular arc. Each corner uses a
 * longer cubic handle (0.64 of the radius instead of 0.55), giving the gradual
 * curvature of an iOS continuous-curvature corner.
 *
 * It extends [CornerBasedShape] on purpose: the Kyant lens effect only accepts
 * RoundedRectangularShape or CornerBasedShape and throws for any other Shape.
 */
class SmoothCornerShape(
    topStart: CornerSize,
    topEnd: CornerSize,
    bottomEnd: CornerSize,
    bottomStart: CornerSize,
) : CornerBasedShape(topStart, topEnd, bottomEnd, bottomStart) {
    constructor(radius: Dp) : this(CornerSize(radius), CornerSize(radius), CornerSize(radius), CornerSize(radius))

    override fun createOutline(
        size: Size,
        topStart: Float,
        topEnd: Float,
        bottomEnd: Float,
        bottomStart: Float,
        layoutDirection: LayoutDirection,
    ): Outline {
        val w = size.width
        val h = size.height
        val maxR = min(w, h) / 2f
        val tl = topStart.coerceIn(0f, maxR)
        val tr = topEnd.coerceIn(0f, maxR)
        val br = bottomEnd.coerceIn(0f, maxR)
        val bl = bottomStart.coerceIn(0f, maxR)
        val path =
            Path().apply {
                moveTo(tl, 0f)
                lineTo(w - tr, 0f)
                val ktr = tr * HANDLE
                cubicTo(w - tr + ktr, 0f, w, tr - ktr, w, tr)
                lineTo(w, h - br)
                val kbr = br * HANDLE
                cubicTo(w, h - br + kbr, w - br + kbr, h, w - br, h)
                lineTo(bl, h)
                val kbl = bl * HANDLE
                cubicTo(bl - kbl, h, 0f, h - bl + kbl, 0f, h - bl)
                lineTo(0f, tl)
                val ktl = tl * HANDLE
                cubicTo(0f, tl - ktl, tl - ktl, 0f, tl, 0f)
                close()
            }
        return Outline.Generic(path)
    }

    override fun copy(
        topStart: CornerSize,
        topEnd: CornerSize,
        bottomEnd: CornerSize,
        bottomStart: CornerSize,
    ): CornerBasedShape = SmoothCornerShape(topStart, topEnd, bottomEnd, bottomStart)

    private companion object {
        const val HANDLE = 0.64f
    }
}

/** Drop-in replacement for [androidx.compose.foundation.shape.RoundedCornerShape] using smooth corners. */
fun smoothCornerShape(radius: Dp): CornerBasedShape = SmoothCornerShape(radius)

fun smoothCornerShape(
    topStart: Dp = 0.dp,
    topEnd: Dp = 0.dp,
    bottomEnd: Dp = 0.dp,
    bottomStart: Dp = 0.dp,
): CornerBasedShape =
    SmoothCornerShape(
        CornerSize(topStart),
        CornerSize(topEnd),
        CornerSize(bottomEnd),
        CornerSize(bottomStart),
    )

/** Percentage radius, same as RoundedCornerShape(percent). */
fun smoothCornerShape(percent: Int): CornerBasedShape = SmoothCornerShape(CornerSize(percent))
