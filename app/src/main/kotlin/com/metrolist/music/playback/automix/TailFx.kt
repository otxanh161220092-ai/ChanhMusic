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
