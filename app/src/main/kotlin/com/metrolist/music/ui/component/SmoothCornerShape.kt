package com.metrolist.music.ui.component

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.min

/**
 * Rounded rectangle with smoother corners than a circular arc. Each corner uses
 * a longer cubic handle (0.64 instead of 0.55 of the radius), which gives the
 * gradual curvature of a continuous-curvature corner.
 */
class SmoothCornerShape(
    private val radius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val w = size.width
        val h = size.height
        val r = with(density) { radius.toPx() }.coerceAtMost(min(w, h) / 2f)
        val k = r * HANDLE
        val path =
            Path().apply {
                moveTo(r, 0f)
                lineTo(w - r, 0f)
                cubicTo(w - r + k, 0f, w, r - k, w, r)
                lineTo(w, h - r)
                cubicTo(w, h - r + k, w - r + k, h, w - r, h)
                lineTo(r, h)
                cubicTo(r - k, h, 0f, h - r + k, 0f, h - r)
                lineTo(0f, r)
                cubicTo(0f, r - k, r - k, 0f, r, 0f)
                close()
            }
        return Outline.Generic(path)
    }

    private companion object {
        const val HANDLE = 0.64f
    }
}
