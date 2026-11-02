/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.ln

/** Cutoff sweep of the tail low-pass: logarithmic from [fromHz] to [toHz] across the sweep, then held. */
object TailFxSweep {
    const val OPEN_HZ = 18_000.0
    const val CLOSED_HZ = 400.0

    fun cutoffAt(
        positionMs: Double,
        startMs: Double,
        durationMs: Double,
        fromHz: Double = OPEN_HZ,
        toHz: Double = CLOSED_HZ,
    ): Double {
        val p = ((positionMs - startMs) / durationMs).coerceIn(0.0, 1.0)
        return exp(ln(fromHz) + (ln(toHz) - ln(fromHz)) * p)
    }
}

/** One-pole low-pass. Cheap enough to run per sample on the audio thread. */
class OnePoleLowPass {
    private var y = 0.0

    fun process(
        x: Double,
        cutoffHz: Double,
        sampleRate: Int,
    ): Double {
        val a = 1.0 - exp(-2.0 * PI * cutoffHz / sampleRate)
        y += a * (x - y)
        return y
    }

    fun reset() {
        y = 0.0
    }
}

/**
 * Feedback echo for the tail of the outgoing song. Feed it the signal that should be echoed; it returns
 * only the echoes (wet), which the caller adds to the dry signal. Once the input is muted the echoes
 * ring out and die away by [feedback] on every repeat.
 */
class TailEcho(
    delayFrames: Int,
    private val feedback: Double = 0.5,
) {
    private val line = DoubleArray(delayFrames.coerceAtLeast(1))
    private var index = 0

    fun process(input: Double): Double {
        val delayed = line[index]
        line[index] = input + delayed * feedback
        index = (index + 1) % line.size
        return delayed
    }

    fun reset() = line.fill(0.0)
}

/** Dry level and echo send over the tail: the dry signal leaves with an equal-power curve. */
object TailFxMix {
    /** Echo repeats are heard from here; also the level of the repeats relative to the dry signal. */
    const val WET_LEVEL = 0.6

    fun dryGain(progress: Double): Double = kotlin.math.cos(PI / 2.0 * progress.coerceIn(0.0, 1.0))

    /** Echo is sent in as the song leaves, and stops once it is gone so the repeats can ring out. */
    fun sendLevel(progress: Double): Double = if (progress >= 1.0) 0.0 else progress.coerceIn(0.0, 1.0)

    /** How long the repeats stay audible after the cut: about -40 dB at feedback 0.5. */
    fun ringOutMs(echoMs: Long): Long = (echoMs * 6).coerceAtMost(4_000L)
}
