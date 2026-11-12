/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component


import androidx.compose.foundation.background
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow

/** Root layer that records the app content, so floating bars can render glass over it. */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** User-adjustable blur radius (dp) shared by every liquid glass surface. */
val LocalGlassBlur = staticCompositionLocalOf { 5f }

/** Liquid glass surface over the recorded backdrop; plain [fallbackColor] when no backdrop is available. */
fun Modifier.glassSurface(
    backdrop: LayerBackdrop?,
    shape: Shape,
    fallbackColor: Color,
    tint: Color,
    mask: Color = Color.Transparent,
    lensDp: Float = 36f,
    lensAmount: Float = 1f,
    blurOnly: Boolean = false,
): Modifier =
    composed {
    val blurDp = LocalGlassBlur.current
    if (backdrop == null) {
        this.background(fallbackColor, shape)
    } else {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                if (blurOnly) {
                    blur(blurDp.dp.toPx())
                } else {
                    vibrancy()
                    blur(blurDp.dp.toPx())
                    lens(lensDp.dp.toPx(), lensDp.dp.toPx() * lensAmount, chromaticAberration = true)
                }
            },
            highlight = { Highlight.Default },
            shadow = { Shadow(alpha = 0.2f) },
            onDrawSurface = {
                drawRect(tint, blendMode = BlendMode.Overlay)
                drawRect(mask)
            },
        )
    }
    }
