/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.drawRect
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

/** Root layer that records the app content, so floating bars can render glass over it. */
val LocalGlassBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** Liquid glass surface over the recorded backdrop; plain [fallbackColor] when no backdrop is available. */
fun Modifier.glassSurface(
    backdrop: LayerBackdrop?,
    shape: Shape,
    fallbackColor: Color,
    tint: Color,
): Modifier =
    if (backdrop == null) {
        this.background(fallbackColor, shape)
    } else {
        this.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                blur(8f.dp.toPx())
                lens(24f.dp.toPx(), 24f.dp.toPx())
            },
            onDrawSurface = { drawRect(tint) },
        )
    }
