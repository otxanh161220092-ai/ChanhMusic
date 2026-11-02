/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Ends the outgoing song with an effect tail: a low-pass filter that closes over the fade, the song's
 * own fade-out, and a feedback echo that keeps ringing after the song is gone.
 *
 * The tail is scheduled relative to "now" ([scheduleTail] takes a delay), because this processor's
 * clock is frames since the last flush, which differs from the track position after any seek.
 * Scheduling from another thread only swaps an immutable value.
 */
@UnstableApi
class TailFxAudioProcessor : BaseAudioProcessor() {
    private class Tail(
        val startMs: Double,
        val durationMs: Long,
        val echoMs: Long,
    )

    @Volatile
    private var tail: Tail? = null

    @Volatile
    private var clockMs = 0.0
    private var echoes = emptyArray<TailEcho>()

    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0
    private var channelCount = 0
    private var sampleRate = 0
    private var framesProcessed = 0L
    private var filters = emptyArray<OnePoleLowPass>()

    /**
     * Starts the tail [delayMs] from now (in this stream's own time) and lets it run for [durationMs].
     * [echoMs] is the echo delay; 0 turns the echo and the in-processor fade off (low-pass only).
     */
    fun scheduleTail(
        delayMs: Long,
        durationMs: Long,
        echoMs: Long,
    ) {
        tail = Tail(clockMs + delayMs.coerceAtLeast(0L), durationMs.coerceAtLeast(1L), echoMs.coerceAtLeast(0L))
        if (echoMs > 0 && sampleRate > 0) {
            echoes = Array(channelCount) { TailEcho((sampleRate * echoMs / 1000).toInt()) }
        }
    }

    fun clearTail() {
        tail = null
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        bytesPerSample =
            when (inputAudioFormat.encoding) {
                C.ENCODING_PCM_16BIT -> 2
                C.ENCODING_PCM_FLOAT -> 4
                else -> throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
            }
        encoding = inputAudioFormat.encoding
        channelCount = inputAudioFormat.channelCount
        sampleRate = inputAudioFormat.sampleRate
        filters = Array(channelCount) { OnePoleLowPass() }
        echoes = emptyArray()
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        framesProcessed = 0L
        clockMs = 0.0
        tail = null
        filters.forEach { it.reset() }
        echoes.forEach { it.reset() }
    }

    override fun onReset() {
        tail = null
        framesProcessed = 0L
        clockMs = 0.0
        filters = emptyArray()
        echoes = emptyArray()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val frameBytes = bytesPerSample * channelCount
        val frames = inputBuffer.remaining() / frameBytes
        val output = replaceOutputBuffer(frames * frameBytes)
        val current = tail

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)

        repeat(frames) {
            val positionMs = framesProcessed * 1000.0 / sampleRate
            val active = current != null && positionMs >= current.startMs
            val progress = if (active) (positionMs - current!!.startMs) / current.durationMs else 0.0
            val cutoff = if (active) TailFxSweep.cutoffAt(positionMs, current!!.startMs, current.durationMs.toDouble()) else 0.0
            val echoOn = active && current!!.echoMs > 0 && echoes.size == channelCount
            for (c in 0 until channelCount) {
                val x = readSample(inputBuffer)
                var y = x
                if (active) {
                    val filtered = filters[c].process(x, cutoff, sampleRate)
                    y = filtered
                    if (echoOn) {
                        val wet = echoes[c].process(filtered * TailFxMix.sendLevel(progress))
                        y = filtered * TailFxMix.dryGain(progress) + wet * TailFxMix.WET_LEVEL
                    }
                }
                writeSample(output, y)
            }
            framesProcessed++
        }
        clockMs = framesProcessed * 1000.0 / sampleRate
        output.flip()
    }

    private fun readSample(buffer: ByteBuffer): Double =
        if (encoding == C.ENCODING_PCM_16BIT) buffer.getShort().toDouble() else buffer.getFloat().toDouble()

    private fun writeSample(
        buffer: ByteBuffer,
        value: Double,
    ) {
        if (encoding == C.ENCODING_PCM_16BIT) {
            buffer.putShort(value.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
        } else {
            buffer.putFloat(value.toFloat())
        }
    }
}
