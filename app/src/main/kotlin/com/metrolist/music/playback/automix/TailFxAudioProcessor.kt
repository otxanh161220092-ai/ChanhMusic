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
 * Sweeps a low-pass filter closed over the tail of one player's stream, so the outgoing song
 * darkens as it is cut. Times are on this processor's clock: frames since the last flush.
 * Scheduling from another thread only swaps an immutable value.
 */
@UnstableApi
class TailFxAudioProcessor : BaseAudioProcessor() {
    private class Sweep(
        val startMs: Long,
        val durationMs: Long,
    )

    @Volatile
    private var sweep: Sweep? = null

    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0
    private var channelCount = 0
    private var sampleRate = 0
    private var framesProcessed = 0L
    private var filters = emptyArray<OnePoleLowPass>()

    fun scheduleSweep(
        startMs: Long,
        durationMs: Long,
    ) {
        sweep = Sweep(startMs.coerceAtLeast(0L), durationMs.coerceAtLeast(1L))
    }

    fun clearSweep() {
        sweep = null
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
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        framesProcessed = 0L
        filters.forEach { it.reset() }
    }

    override fun onReset() {
        sweep = null
        framesProcessed = 0L
        filters = emptyArray()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val frameBytes = bytesPerSample * channelCount
        val frames = inputBuffer.remaining() / frameBytes
        val output = replaceOutputBuffer(frames * frameBytes)
        val current = sweep

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)

        repeat(frames) {
            val positionMs = framesProcessed * 1000.0 / sampleRate
            val filtered = current != null && positionMs >= current.startMs
            val cutoff =
                if (filtered) {
                    TailFxSweep.cutoffAt(positionMs, current!!.startMs.toDouble(), current.durationMs.toDouble())
                } else {
                    0.0
                }
            for (c in 0 until channelCount) {
                val x = readSample(inputBuffer)
                val y = if (filtered) filters[c].process(x, cutoff, sampleRate) else x
                writeSample(output, y)
            }
            framesProcessed++
        }
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
