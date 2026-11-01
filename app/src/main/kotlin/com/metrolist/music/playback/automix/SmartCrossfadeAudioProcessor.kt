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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Applies an equal-power fade (cos for fade-out, sin for fade-in) to the PCM stream of ONE player.
 *
 * Times are in milliseconds on this processor's own clock: frames counted since the last flush
 * (i.e. since the stream started or was seeked). Gain is computed per frame and smoothed with a
 * ~3 ms one-pole filter, so there are no steps at fade boundaries, when a plan changes, or after a
 * flush. That is what keeps the transition free of pops and clicks.
 *
 * A single processor only sees one stream. A real overlap between two songs needs one player per
 * song, each with its own instance: A gets [scheduleFadeOut], B gets [scheduleFadeIn].
 *
 * Runs on the audio thread. [scheduleFadeOut], [scheduleFadeIn] and [clearFade] may be called from
 * any thread; they only swap an immutable plan object.
 */
@UnstableApi
class SmartCrossfadeAudioProcessor : BaseAudioProcessor() {
    private enum class Mode { FADE_OUT, FADE_IN }

    private class FadePlan(
        val mode: Mode,
        val startMs: Long,
        val durationMs: Long,
    )

    @Volatile
    private var plan: FadePlan? = null

    private var encoding = C.ENCODING_INVALID
    private var bytesPerSample = 0
    private var channelCount = 0
    private var sampleRate = 0
    private var smoothingCoefficient = 0.0

    private var framesProcessed = 0L
    private var currentGain = 1.0

    /** Ramps this stream from full volume to silence, starting at [startMs] and lasting [durationMs]. */
    fun scheduleFadeOut(
        startMs: Long,
        durationMs: Long,
    ) {
        plan = FadePlan(Mode.FADE_OUT, startMs.coerceAtLeast(0L), durationMs.coerceAtLeast(1L))
    }

    /** Ramps this stream from silence to full volume, starting at [startMs] and lasting [durationMs]. */
    fun scheduleFadeIn(
        startMs: Long,
        durationMs: Long,
    ) {
        plan = FadePlan(Mode.FADE_IN, startMs.coerceAtLeast(0L), durationMs.coerceAtLeast(1L))
    }

    fun clearFade() {
        plan = null
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
        smoothingCoefficient = exp(-1.0 / (SMOOTHING_TIME_CONSTANT_S * sampleRate))
        return inputAudioFormat
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        framesProcessed = 0L
    }

    override fun onReset() {
        plan = null
        framesProcessed = 0L
        currentGain = 1.0
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val frameBytes = bytesPerSample * channelCount
        val frames = inputBuffer.remaining() / frameBytes
        val output = replaceOutputBuffer(frames * frameBytes)
        val currentPlan = plan

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)

        repeat(frames) {
            val target = targetGain(currentPlan, framesProcessed)
            currentGain = target + (currentGain - target) * smoothingCoefficient
            val gain = currentGain

            repeat(channelCount) {
                if (encoding == C.ENCODING_PCM_16BIT) {
                    val sample = inputBuffer.getShort() * gain
                    output.putShort(sample.roundToInt().coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort())
                } else {
                    output.putFloat((inputBuffer.getFloat() * gain).toFloat())
                }
            }
            framesProcessed++
        }
        output.flip()
    }

    /**
     * Equal-power curve: cos²+sin² = 1, so perceived loudness stays constant through the overlap.
     * Before the fade starts the gain is 1 for fade-out and 0 for fade-in; after it ends, 0 and 1.
     */
    private fun targetGain(
        currentPlan: FadePlan?,
        frame: Long,
    ): Double {
        if (currentPlan == null) return 1.0
        val positionMs = frame * 1000.0 / sampleRate
        val t = ((positionMs - currentPlan.startMs) / currentPlan.durationMs).coerceIn(0.0, 1.0)
        val angle = t * PI / 2.0
        return when (currentPlan.mode) {
            Mode.FADE_OUT -> cos(angle)
            Mode.FADE_IN -> sin(angle)
        }
    }

    private companion object {
        /** Time constant of the gain smoother. Long enough to remove clicks, short enough to be inaudible as lag. */
        const val SMOOTHING_TIME_CONSTANT_S = 0.003
    }
}
