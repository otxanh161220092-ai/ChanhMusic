/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.bcmix.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Karaoke's vocal reduction. Lead vocals sit in the centre of a stereo mix, so
 * removing the centre leaves an approximate instrumental for the user to sing over.
 *
 * Off (the default) it returns before it reads a sample, so the audio block passes
 * through untouched. Karaoke is never switched on while Automix is on.
 */
object VocalCancel {
    @Volatile
    var enabled: Boolean = false
        private set

    private val _state = MutableStateFlow(false)
    val state: StateFlow<Boolean> = _state.asStateFlow()

    /** How much of the centre channel is removed: 1 removes it fully, which also thins the instruments there. */
    private const val CENTRE_REMOVAL = 0.9f

    /** Restores the level the removal takes away. */
    private const val MAKEUP = 1.4f

    fun set(on: Boolean) {
        enabled = on
        _state.value = on
    }

    fun process(block: AudioBlock) {
        if (!enabled || block.channelCount != 2 || block.frameCount == 0) return
        val s = block.samples
        val end = block.frameCount * 2
        var i = 0
        while (i < end) {
            val l = s[i]
            val r = s[i + 1]
            val centre = (l + r) * 0.5f * CENTRE_REMOVAL
            s[i] = (l - centre) * MAKEUP
            s[i + 1] = (r - centre) * MAKEUP
            i += 2
        }
    }
}
