/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

/**
 * Collects a track's mono audio at [BeatThisTracker.SAMPLE_RATE] while it is being decoded, so the beat
 * model can run afterwards without decoding the song a second time. Linear interpolation is enough
 * here: the beat model only uses content below about 11 kHz.
 *
 * Holds at most [maxSeconds] of audio. Past that, [toFloatArray] returns null and automix falls back
 * to its onset-based grid, so memory stays bounded for very long tracks.
 */
class BeatInputBuffer(
    private val inputRate: Int,
    private val maxSeconds: Int = MAX_SECONDS,
) {
    private val step = inputRate.toDouble() / BeatThisTracker.SAMPLE_RATE
    private val capacity = maxSeconds * BeatThisTracker.SAMPLE_RATE
    private var samples = FloatArray(minOf(capacity, BeatThisTracker.SAMPLE_RATE * 60))
    private var count = 0
    private var overflowed = false

    private var inputIndex = 0L
    private var previous = 0f
    private var nextOutput = 0.0

    fun feed(mono: FloatArray, frames: Int) {
        if (overflowed) return
        for (i in 0 until frames) {
            val current = mono[i]
            // Emit every output time that falls between the previous input sample and this one.
            while (nextOutput <= inputIndex.toDouble()) {
                val fraction = (nextOutput - (inputIndex - 1)).toFloat()
                append(previous + (current - previous) * fraction)
                if (overflowed) return
                nextOutput += step
            }
            previous = current
            inputIndex += 1
        }
    }

    private fun append(value: Float) {
        if (count >= capacity) {
            overflowed = true
            samples = FloatArray(0)
            return
        }
        if (count == samples.size) samples = samples.copyOf(minOf(capacity, samples.size * 2))
        samples[count++] = value
    }

    /** The collected audio, or null when the track was longer than the buffer allows. */
    fun toFloatArray(): FloatArray? = if (overflowed || count == 0) null else samples.copyOf(count)

    companion object {
        const val MAX_SECONDS = 360
    }
}
