/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.imageResource
import com.metrolist.music.R
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorFilter
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
    // Explicit circular clip: only the disc of the control is painted, so the square corners of the
    // ring image never reach the neighbouring buttons.
    val disc = Path().apply { addOval(Rect(Offset.Zero, size)) }
    clipPath(disc) {
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
    this
        .graphicsLayer {
            shape = CircleShape
            clip = true
            compositingStrategy = CompositingStrategy.Offscreen
        }
        .drawWithContent {
        drawContent()
        drawHighlight(highlight)
    }

/** [highlightBorder] with the supplied highlight image from resources. */
@Composable
fun Modifier.highlightBorder(): Modifier = highlightBorder(ImageBitmap.imageResource(R.drawable.liquid_glass_highlight))

/**
 * Highlight & border layer for pill-shaped surfaces (mini player, bottom bar). The pill ring image is
 * stretched over the whole surface, and everything outside the pill is clipped away.
 */
@Composable
fun Modifier.highlightPill(): Modifier {
    val image = ImageBitmap.imageResource(R.drawable.liquid_glass_pill_highlight)
    return this.drawWithContent {
        drawContent()
        // Only the pill itself is painted: the ring image's square corners are cut away by the path.
        val pill = Path().apply {
            addRoundRect(RoundRect(Rect(Offset.Zero, size), CornerRadius(size.height / 2f)))
        }
        clipPath(pill) {
            drawImage(
                image = image,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                colorFilter = pillRingFilter,
                blendMode = BlendMode.SrcOver,
            )
        }
    }
}

/**
 * The supplied ring image is an opaque white pill with dark edges. Drawn as is, its white fill
 * covers the whole bar. This filter keeps only the dark edge: black ink whose opacity follows how
 * dark each pixel is, so the white interior becomes transparent. The image file itself is unchanged.
 */
private val pillRingFilter: ColorFilter =
    ColorFilter.colorMatrix(
        ColorMatrix(
            floatArrayOf(
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                0f, 0f, 0f, 0f, 0f,
                -0.3f, -0.59f, -0.11f, 0f, 1f,
            ),
        ),
    )
