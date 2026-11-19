/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * How much of the vocal stem comes back over the instrumental while karaoke is on: 0 is the
 * pure instrumental, 1 is the mix with the vocals at full level. Not applied to playback yet.
 */
object KaraokeMix {

    private val level = MutableStateFlow(0f)
    val backingLevel: StateFlow<Float> = level.asStateFlow()

    fun setBackingLevel(value: Float) {
        level.value = value.coerceIn(0f, 1f)
    }
}
