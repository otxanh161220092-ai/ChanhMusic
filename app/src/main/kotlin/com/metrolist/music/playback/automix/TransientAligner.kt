/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.abs
import kotlin.math.sqrt

/** A short piece of an onset (kick) curve: [values] sampled every [hopMs], the first at the slice start. */
class OnsetSlice(
    val values: FloatArray,
    val hopMs: Double,
)

/**
 * Aligns the kicks of song B with those of song A by cross-correlation:
 * (f * g)[n] = sum over m of f[m] * g[m + n]. The lag n where the correlation peaks is the shift that
 * makes the two onset curves coincide, to the resolution of one hop.
 */
object TransientAligner {
    const val MIN_CORRELATION = 0.15

    /**
     * @param a onset curve of A over the overlap, starting at the position where the overlap opens.
     * @param b onset curve of B starting [padMs] (of B's own time) before the position B starts at.
     * @param factor tempo factor applied to B, so B's time runs [factor] times faster than A's.
     * @param maxLagMs largest shift to look for, in A's time.
     * @return how much later (positive) or earlier (negative) B must start, in B's own time, or null
     * when the curves are too flat or too different for the peak to mean anything.
     */
    fun alignmentShiftMs(
        a: OnsetSlice,
        b: OnsetSlice,
        factor: Double,
        padMs: Double,
        maxLagMs: Double,
    ): Double? {
        val n = a.values.size
        if (n < 16 || b.values.size < 16 || a.hopMs <= 0.0 || b.hopMs <= 0.0) return null

        fun bAt(timeInA: Double): Double {
            val index = (timeInA * factor + padMs) / b.hopMs
            val i = index.toInt()
            if (i < 0 || i + 1 >= b.values.size) return 0.0
            val f = index - i
            return b.values[i] * (1.0 - f) + b.values[i + 1] * f
        }

        val maxLagSteps = (maxLagMs / a.hopMs).toInt()
        var normA = 0.0
        for (v in a.values) normA += v.toDouble() * v
        if (normA <= 1e-12) return null

        var bestScore = -1.0
        var bestLag = 0
        val scores = DoubleArray(2 * maxLagSteps + 1)
        for (lag in -maxLagSteps..maxLagSteps) {
            var sum = 0.0
            var normB = 0.0
            for (i in 0 until n) {
                val bv = bAt(i * a.hopMs + lag * a.hopMs)
                sum += a.values[i] * bv
                normB += bv * bv
            }
            val score = if (normB > 1e-12) sum / sqrt(normA * normB) else 0.0
            scores[lag + maxLagSteps] = score
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        if (bestScore < MIN_CORRELATION) return null

        // Parabolic interpolation around the peak for sub-hop accuracy.
        var refined = bestLag.toDouble()
        val k = bestLag + maxLagSteps
        if (k > 0 && k < scores.size - 1) {
            val l = scores[k - 1]
            val c = scores[k]
            val r = scores[k + 1]
            val denom = l - 2 * c + r
            if (abs(denom) > 1e-12) refined += 0.5 * (l - r) / denom
        }
        return refined * a.hopMs * factor
    }
}
