/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.liquidtabs

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape

/** Scale factor applied to tab items while the pill is pressed. */
val LocalLiquidBottomTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * Fully rounded pill shape. Must be a [androidx.compose.foundation.shape.CornerBasedShape]:
 * the backdrop lens effect rejects any other shape type.
 */
fun Capsule(): Shape = RoundedCornerShape(percent = 50)
