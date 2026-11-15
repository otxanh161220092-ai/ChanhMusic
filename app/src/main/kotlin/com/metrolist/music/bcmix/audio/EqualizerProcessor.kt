package com.metrolist.music.bcmix

import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi

/**
 * The equaliser slot of BitChord's DSP chain. Metrolist's own equaliser
 * (`CustomEqualizerAudioProcessor`) stays the one the user controls, so this
 * slot passes audio through untouched and the chain is wired to Metrolist's
 * equaliser where the sink is built.
 */
@UnstableApi
class EqualizerProcessor : BaseAudioProcessor() {
    /** Never engaged here, so the limiter is not held for an EQ that is not running. */
    val isEnabled: Boolean get() = false
    val isShaping: Boolean get() = false

    override fun onConfigure(inputAudioFormat: androidx.media3.common.audio.AudioProcessor.AudioFormat):
        androidx.media3.common.audio.AudioProcessor.AudioFormat = inputAudioFormat

    override fun queueInput(inputBuffer: java.nio.ByteBuffer) {
        replaceOutputBuffer(inputBuffer.remaining()).put(inputBuffer).flip()
    }

    fun configure(sampleRate: Int, channelCount: Int) = Unit
    fun process(block: com.metrolist.music.bcmix.audio.AudioBlock) = Unit
    fun flush() = Unit
    fun reset() = Unit
}
