/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.lyrics.words

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp

internal val LYRICS_LINE_HORIZONTAL_PADDING = 12.dp

/** Whether the lyric surface is on screen; always true here, since the app pauses nothing else. */
internal val LocalVisualWorkActive = compositionLocalOf { true }

/** Mask bitmaps are immutable on Android, so there is nothing to mark. */
internal fun ImageBitmap.markPixelsImmutable() = Unit
