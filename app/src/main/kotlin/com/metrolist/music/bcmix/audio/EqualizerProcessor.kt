package com.metrolist.music.bcmix

import com.metrolist.music.bcmix.audio.AudioBlock

/**
 * The equaliser slot of BitChord's DSP chain. Metrolist's own equaliser
 * (`CustomEqualizerAudioProcessor`) stays the one the user controls, so this
 * slot passes audio through untouched; the chain is wired to Metrolist's
 * equaliser where the sink is built.
 */
class EqualizerProcessor {
    /** Never engaged here, so the limiter is not held for an EQ that is not running. */
    val isEnabled: Boolean get() = false
    val isShaping: Boolean get() = false

    fun configure(sampleRate: Int, channelCount: Int) = Unit
    fun process(block: AudioBlock) = Unit
    fun flush() = Unit
    fun reset() = Unit
}
