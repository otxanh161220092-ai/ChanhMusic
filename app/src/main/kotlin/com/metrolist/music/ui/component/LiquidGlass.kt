/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.imageResource
import com.metrolist.music.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.clipRect

/**
 * Liquid glass for round top-bar buttons, bottom to top:
 * 1. white layer in Overlay blend mode,
 * 2. grey-white layer at 50% opacity,
 * 3. the highlight & border image, in Overlay blend mode, over everything.
 */
fun Modifier.liquidGlass(highlight: ImageBitmap): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawBehind {
            drawRect(Color.White, blendMode = BlendMode.Overlay)
            drawRect(Color(0xFFD9D9D9).copy(alpha = 0.5f))
        }
        .drawWithContent {
            drawContent()
            drawHighlight(highlight)
        }

private fun DrawScope.drawHighlight(image: ImageBitmap) {
    // Clipped to the control's own bounds so the ring never spills into neighbouring buttons.
    clipRect(left = 0f, top = 0f, right = size.width, bottom = size.height) {
        drawImage(
            image = image,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            blendMode = BlendMode.Overlay,
        )
    }
}

/** [liquidGlass] clipped to [shape], with the highlight image loaded from resources. */
@Composable
fun Modifier.liquidGlassSurface(shape: Shape): Modifier {
    val highlight = ImageBitmap.imageResource(R.drawable.liquid_glass_highlight)
    return this.clip(shape).liquidGlass(highlight)
}

/** Highlight & border layer only (Overlay blend), for controls that already have their own overlay. */
fun Modifier.highlightBorder(highlight: ImageBitmap): Modifier =
    this.clip(CircleShape).drawWithContent {
        drawContent()
        drawHighlight(highlight)
    }

/** [highlightBorder] with the supplied highlight image from resources. */
@Composable
fun Modifier.highlightBorder(): Modifier = highlightBorder(ImageBitmap.imageResource(R.drawable.liquid_glass_highlight))
