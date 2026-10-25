/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Plain Kotlin signal analysis for Automix: no Android types, so it can be unit tested on the JVM. */

const val LOUDNESS_HOP_MS = 100

private const val MIN_BPM = 70.0
private const val MAX_BPM = 180.0

/** A beat grid measured near one point of a track. All times are in milliseconds of track time. */
class BeatGrid(
    val downbeatMs: Double,
    val beatMs: Double,
) {
    val barMs: Double get() = beatMs * 4.0

    fun nearestBar(timeMs: Double): Double = downbeatMs + Math.round((timeMs - downbeatMs) / barMs) * barMs

    fun firstBarAtOrAfter(timeMs: Double): Double = downbeatMs + ceil((timeMs - downbeatMs) / barMs) * barMs
}

class TrackFeatures(
    val durationMs: Long,
    val loudnessDb: FloatArray,
    val bpm: Float,
    val bpmConfidence: Float,
    private val onset: FloatArray,
    private val lowOnset: FloatArray,
    private val periodHops: Double,
    private val hopMs: Double,
) {
    val hasTempo: Boolean get() = bpm > 0f && bpmConfidence >= MIN_TEMPO_CONFIDENCE

    /**
     * Measures the beat grid in a window of [spanMs] around [centerMs] using the global tempo. Measuring
     * locally keeps the grid correct near the mix point even if the tempo drifts a little over the song.
     */
    fun gridNear(
        centerMs: Double,
        spanMs: Double = 16_000.0,
    ): BeatGrid? {
        if (periodHops <= 0.0 || onset.size < 8) return null
        val center = (centerMs / hopMs).roundToInt()
        val half = (spanMs / hopMs / 2.0).roundToInt()
        val lo = max(0, center - half)
        val hi = min(onset.size - 1, center + half)
        if (hi - lo < periodHops * 8) return null

        val p = periodHops
        var bestPhase = 0
        var bestScore = -1.0
        for (phase in 0 until ceil(p).toInt()) {
            var score = 0.0
            var count = 0
            var t = lo + phase.toDouble()
            while (t <= hi) {
                score += onset[t.roundToInt().coerceIn(0, onset.size - 1)]
                count++
                t += p
            }
            if (count > 0 && score / count > bestScore) {
                bestScore = score / count
                bestPhase = phase
            }
        }

        // The kick tends to land hardest on beat one, so pick the beat of the bar with the most low-band energy.
        val barSums = DoubleArray(4)
        val barCounts = IntArray(4)
        var k = 0
        var t = lo + bestPhase.toDouble()
        while (t <= hi) {
            barSums[k % 4] += lowOnset[t.roundToInt().coerceIn(0, lowOnset.size - 1)]
            barCounts[k % 4]++
            k++
            t += p
        }
        var bestBeat = 0
        var bestBeatScore = -1.0
        for (i in 0 until 4) {
            val avg = if (barCounts[i] > 0) barSums[i] / barCounts[i] else 0.0
            if (avg > bestBeatScore) {
                bestBeatScore = avg
                bestBeat = i
            }
        }

        val downbeatHop = lo + bestPhase + bestBeat * p
        return BeatGrid(downbeatMs = downbeatHop * hopMs, beatMs = p * hopMs)
    }

    companion object {
        const val MIN_TEMPO_CONFIDENCE = 1.4f
    }
}

private class FloatBuf(initial: Int = 4096) {
    var data = FloatArray(initial)
    var size = 0

    fun add(v: Float) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = v
    }

    fun toArray(): FloatArray = data.copyOf(size)
}

/** Feed mono PCM in the range -1..1 with [feed], then call [finish]. */
class FeatureExtractor(
    private val sampleRate: Int,
) {
    private val onsetHop = (sampleRate / 200).coerceAtLeast(1)
    private val loudHop = (sampleRate * LOUDNESS_HOP_MS / 1000).coerceAtLeast(1)
    private val lowPassCoef = (1.0 - exp(-2.0 * Math.PI * 150.0 / sampleRate)).toFloat()

    private var lowPassState = 0f
    private var hopCount = 0
    private var fullEnergy = 0.0
    private var lowEnergy = 0.0
    private var loudCount = 0
    private var loudEnergy = 0.0
    private var totalSamples = 0L

    private val fullHops = FloatBuf()
    private val lowHops = FloatBuf()
    private val loudness = FloatBuf()

    fun feed(
        mono: FloatArray,
        count: Int,
    ) {
        for (i in 0 until count) {
            val x = mono[i]
            lowPassState += lowPassCoef * (x - lowPassState)
            val sq = x * x
            fullEnergy += sq
            lowEnergy += lowPassState * lowPassState
            loudEnergy += sq
            if (++hopCount == onsetHop) {
                fullHops.add((fullEnergy / onsetHop).toFloat())
                lowHops.add((lowEnergy / onsetHop).toFloat())
                hopCount = 0
                fullEnergy = 0.0
                lowEnergy = 0.0
            }
            if (++loudCount == loudHop) {
                loudness.add((10.0 * log10(loudEnergy / loudHop + 1e-10)).toFloat())
                loudCount = 0
                loudEnergy = 0.0
            }
        }
        totalSamples += count
    }

    fun finish(): TrackFeatures {
        val durationMs = totalSamples * 1000L / sampleRate
        val hopMs = onsetHop * 1000.0 / sampleRate
        val loud = loudness.toArray()
        val full = fullHops.toArray()
        val low = lowHops.toArray()
        val n = full.size
        if (n < 600) return TrackFeatures(durationMs, loud, 0f, 0f, FloatArray(0), FloatArray(0), 0.0, hopMs)

        val fullFlux = FloatArray(n)
        val lowFlux = FloatArray(n)
        var prevFull = ln(1f + 1000f * full[0])
        var prevLow = ln(1f + 1000f * low[0])
        for (i in 1 until n) {
            val f = ln(1f + 1000f * full[i])
            val l = ln(1f + 1000f * low[i])
            fullFlux[i] = max(0f, f - prevFull)
            lowFlux[i] = max(0f, l - prevLow)
            prevFull = f
            prevLow = l
        }

        val onset = removeLocalMean(FloatArray(n) { lowFlux[it] + 0.5f * fullFlux[it] }, 50)
        val lowOnset = removeLocalMean(lowFlux, 50)
        val smoothOnset = smooth(onset)
        val smoothLow = smooth(lowOnset)

        val hopSec = hopMs / 1000.0
        val minLag = (60.0 / (MAX_BPM * hopSec)).roundToInt()
        val maxLag = (60.0 / (MIN_BPM * hopSec)).roundToInt()
        val ac = DoubleArray(2 * maxLag + 2)
        for (lag in minLag until ac.size) {
            if (lag >= n) break
            var sum = 0.0
            for (i in 0 until n - lag) sum += onset[i].toDouble() * onset[i + lag]
            ac[lag] = sum / (n - lag)
        }

        var bestLag = -1
        var bestScore = 0.0
        var scoreSum = 0.0
        var scoreCount = 0
        for (lag in minLag..maxLag) {
            val raw = ac[lag] + 0.5 * ac[2 * lag]
            val bpm = 60.0 / (lag * hopSec)
            val octaves = ln(bpm / 120.0) / ln(2.0)
            val prior = exp(-0.5 * (octaves / 0.9) * (octaves / 0.9))
            scoreSum += raw
            scoreCount++
            val score = raw * prior
            if (score > bestScore) {
                bestScore = score
                bestLag = lag
            }
        }
        val meanRaw = if (scoreCount > 0) scoreSum / scoreCount else 0.0
        if (bestLag < 0 || meanRaw <= 1e-12) {
            return TrackFeatures(durationMs, loud, 0f, 0f, smoothOnset, smoothLow, 0.0, hopMs)
        }

        var period = bestLag.toDouble()
        if (bestLag > minLag && bestLag < maxLag) {
            val y1 = ac[bestLag - 1]
            val y2 = ac[bestLag]
            val y3 = ac[bestLag + 1]
            val denom = y1 - 2 * y2 + y3
            if (abs(denom) > 1e-12) period += (0.5 * (y1 - y3) / denom).coerceIn(-1.0, 1.0)
        }
        period = refinePeriod(smoothOnset, period)

        val bpm = (60.0 / (period * hopSec)).toFloat()
        val rawAtBest = ac[bestLag] + 0.5 * ac[2 * bestLag]
        val confidence = (rawAtBest / meanRaw).toFloat()
        return TrackFeatures(durationMs, loud, bpm, confidence, smoothOnset, smoothLow, period, hopMs)
    }

    // Tries small tempo offsets and keeps the one where the beats line up best over the whole track.
    private fun refinePeriod(
        onset: FloatArray,
        period: Double,
    ): Double {
        var best = period
        var bestScore = -1.0
        for (step in -30..30) {
            val p = period * (1.0 + 0.0005 * step)
            var stepBest = 0.0
            for (phase in 0 until ceil(p).toInt()) {
                var score = 0.0
                var count = 0
                var t = phase.toDouble()
                while (t < onset.size - 1) {
                    score += onset[t.roundToInt()]
                    count++
                    t += p
                }
                if (count > 0) stepBest = max(stepBest, score / count)
            }
            if (stepBest > bestScore) {
                bestScore = stepBest
                best = p
            }
        }
        return best
    }

    private fun removeLocalMean(
        input: FloatArray,
        radius: Int,
    ): FloatArray {
        val n = input.size
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + input[i]
        return FloatArray(n) { i ->
            val a = max(0, i - radius)
            val b = min(n, i + radius + 1)
            val mean = (prefix[b] - prefix[a]) / (b - a)
            max(0f, input[i] - mean.toFloat())
        }
    }

    private fun smooth(input: FloatArray): FloatArray =
        FloatArray(input.size) { i ->
            val a = input[max(0, i - 1)]
            val c = input[min(input.size - 1, i + 1)]
            0.25f * a + 0.5f * input[i] + 0.25f * c
        }
}

/** Where and how the current song hands over to the next one. All times are in milliseconds of track time. */
class MixPlan(
    val startMs: Long,
    val lengthMs: Long,
    val nextStartMs: Long,
    val nextSpeedFactor: Float,
    val tempoMatched: Boolean,
) {
    val endMs: Long get() = startMs + lengthMs
}

object MixPlanner {
    private const val MAX_TEMPO_DELTA = 0.08f
    private const val SMOOTH_HOPS = 20
    private const val LOUD_MARGIN_DB = 6f
    private const val INTRO_MARGIN_DB = 20f
    private const val MAX_NEXT_START_MS = 20_000.0

    /**
     * Picks the mix so that it finishes while the song is still at full level, instead of waiting for a
     * quiet outro. Returns null when nothing sensible can be planned (the caller then keeps a plain crossfade).
     */
    fun plan(
        current: TrackFeatures,
        next: TrackFeatures,
        desiredMixMs: Long,
        earliestStartMs: Long,
    ): MixPlan? {
        val currentDb = smoothDb(current.loudnessDb)
        val referenceDb = percentile(currentDb, 0.75f) ?: return null
        val threshold = referenceDb - LOUD_MARGIN_DB
        val lastLoud = currentDb.indexOfLast { it >= threshold }
        if (lastLoud < 0) return null

        val duration = current.durationMs
        var mixEnd = (lastLoud + 1L) * LOUDNESS_HOP_MS
        if (mixEnd > duration - 3_000L) mixEnd = duration

        val gridCurrent =
            if (current.hasTempo) current.gridNear(mixEnd - desiredMixMs / 2.0) else null

        var length = desiredMixMs
        if (gridCurrent != null) {
            val bars = Math.round(desiredMixMs / gridCurrent.barMs).coerceAtLeast(1L)
            val barLength = (bars * gridCurrent.barMs).toLong()
            if (barLength <= desiredMixMs * 2) length = barLength
        }

        var start = mixEnd - length
        if (gridCurrent != null) {
            start = gridCurrent.nearestBar(start.toDouble()).toLong()
            while (start + length > duration && start > 0) start -= gridCurrent.barMs.toLong().coerceAtLeast(1L)
        } else if (start + length > duration) {
            start = duration - length
        }
        if (start < earliestStartMs) return null

        val nextDb = smoothDb(next.loudnessDb)
        val nextReference = percentile(nextDb, 0.75f) ?: return null
        val firstAudible = nextDb.indexOfFirst { it >= nextReference - INTRO_MARGIN_DB }.coerceAtLeast(0)
        var nextStart = max(0.0, firstAudible.toDouble() * LOUDNESS_HOP_MS - 300.0)
        if (next.hasTempo) {
            val gridNext = next.gridNear(nextStart + 4_000.0)
            if (gridNext != null) {
                val aligned = gridNext.firstBarAtOrAfter(nextStart)
                if (aligned in 0.0..MAX_NEXT_START_MS) nextStart = aligned
            }
        }

        var factor = 1f
        var matched = false
        if (current.hasTempo && next.hasTempo) {
            var ratio = current.bpm / next.bpm
            while (ratio > 1.4142f) ratio /= 2f
            while (ratio < 0.7071f) ratio *= 2f
            if (abs(ratio - 1f) <= MAX_TEMPO_DELTA) {
                factor = ratio
                matched = true
            }
        }

        return MixPlan(start, length, nextStart.toLong(), factor, matched)
    }

    private fun smoothDb(db: FloatArray): FloatArray {
        val n = db.size
        val prefix = DoubleArray(n + 1)
        for (i in 0 until n) prefix[i + 1] = prefix[i] + db[i]
        val radius = SMOOTH_HOPS / 2
        return FloatArray(n) { i ->
            val a = max(0, i - radius)
            val b = min(n, i + radius + 1)
            ((prefix[b] - prefix[a]) / (b - a)).toFloat()
        }
    }

    private fun percentile(
        values: FloatArray,
        fraction: Float,
    ): Float? {
        val audible = values.filter { it > -60f }.sorted()
        if (audible.isEmpty()) return null
        return audible[(audible.size * fraction).toInt().coerceIn(0, audible.size - 1)]
    }
}
