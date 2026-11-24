/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp

/**
 * A soft shadow drawn only outside a pill / rounded shape. Unlike [androidx.compose.ui.draw.shadow],
 * nothing is drawn inside the shape, so a translucent (glass) surface does not show the shadow through it.
 * The corner radius is half the smaller side, i.e. a capsule.
 */
fun Modifier.outerShadow(
    color: Color = Color.Black.copy(alpha = 0.3f),
    blur: Dp = 12.dp,
    offsetY: Dp = 0.dp,
): Modifier = this.drawBehind {
    val blurPx = blur.toPx()
    val dy = offsetY.toPx()
    val w = size.width
    val h = size.height
    val radius = minOf(w, h) / 2f
    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        val inner = Path().apply {
            addRoundRect(0f, dy, w, h + dy, radius, radius, Path.Direction.CW)
        }
        val outer = Path().apply {
            val pad = blurPx * 3f
            addRect(-pad, -pad, w + pad, h + dy + pad, Path.Direction.CW)
        }
        val ring = Path()
        ring.op(outer, inner, Path.Op.DIFFERENCE)
        native.save()
        native.clipPath(ring)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color.toArgb()
            maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
        }
        native.drawPath(inner, paint)
        native.restore()
    }
}
