/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.abs

/**
 * Finds the sections of a song from its measured loudness curve instead of from its length.
 *
 * The curve is smoothed, then the points where the average level of the next few seconds differs most
 * from the previous few seconds become section boundaries. Each section is then classed by its level
 * relative to the rest of the same song: the loudest become Drop/Chorus, the quiet ones Verse, and a
 * quiet start/end become Intro/Outro. Boundaries land wherever the music changes, so the positions
 * differ for every song and can be minutes into it.
 *
 * Pure Kotlin so it can be unit tested on the JVM. Returns null when the curve is too short or flat to
 * say anything, so the caller can fall back to [HeuristicSegmenter].
 */
object LoudnessSegmenter {
    private const val WINDOW_HOPS = 80 // 8 s each side when looking for a level change
    private const val SMOOTH_HOPS = 20 // 2 s moving average
    private const val MIN_SEGMENT_MS = 10_000L
    private const val MIN_STEP = 0.12f // level change (0..1 scale) that counts as a boundary
    private const val CHORUS_LEVEL = 0.55f
    private const val DROP_LEVEL = 0.8f
    private const val SILENCE_DB = -60f

    fun segment(
        durationMs: Long,
        loudnessDb: FloatArray,
    ): List<SongSegment>? {
        if (durationMs < HeuristicSegmenter.MIN_DURATION_MS || loudnessDb.size < WINDOW_HOPS * 2) return null
        val audible = loudnessDb.filter { it > SILENCE_DB }.sorted()
        if (audible.size < 100) return null
        val low = audible[(audible.size * 0.1).toInt()]
        val high = audible[(audible.size * 0.9).toInt().coerceAtMost(audible.size - 1)]
        if (high - low < 1.5f) return null

        val level = smooth(FloatArray(loudnessDb.size) { ((loudnessDb[it].coerceAtLeast(SILENCE_DB) - low) / (high - low)).coerceIn(0f, 1f) })
        val boundaries = findBoundaries(level)
        val hopMs = LOUDNESS_HOP_MS.toLong()
        val edges = listOf(0L) + boundaries.map { it * hopMs } + listOf(durationMs)
        val cuts = mergeShort(edges.distinct().sorted(), durationMs)

        val means =
            cuts.zipWithNext().map { (s, e) ->
                val from = (s / hopMs).toInt().coerceIn(0, level.size)
                val to = (e / hopMs).toInt().coerceIn(from, level.size)
                if (to > from) (from until to).sumOf { level[it].toDouble() }.toFloat() / (to - from) else 0f
            }
        val loudest = means.maxOrNull() ?: return null
        return cuts.zipWithNext().mapIndexed { i, (s, e) ->
            val m = means[i]
            val type =
                when {
                    i == 0 && m < CHORUS_LEVEL && cuts.size > 2 -> SegmentType.INTRO
                    i == means.lastIndex && m < CHORUS_LEVEL && cuts.size > 2 -> SegmentType.OUTRO
                    m >= DROP_LEVEL && m >= loudest - 0.05f -> SegmentType.DROP
                    m >= CHORUS_LEVEL -> SegmentType.CHORUS
                    else -> SegmentType.VERSE
                }
            SongSegment(type, s, e, (0.4f * type.defaultEnergy + 0.6f * m).coerceIn(0f, 1f))
        }
    }

    private fun findBoundaries(level: FloatArray): List<Int> {
        val n = level.size
        val novelty = FloatArray(n)
        for (i in WINDOW_HOPS until n - WINDOW_HOPS) {
            var before = 0f
            var after = 0f
            for (k in 1..WINDOW_HOPS) {
                before += level[i - k]
                after += level[i + k - 1]
            }
            novelty[i] = (after - before) / WINDOW_HOPS
        }
        val minGap = (MIN_SEGMENT_MS / LOUDNESS_HOP_MS).toInt()
        val peaks = ArrayList<Int>()
        for (i in WINDOW_HOPS until n - WINDOW_HOPS) {
            val v = abs(novelty[i])
            if (v < MIN_STEP) continue
            val lo = maxOf(0, i - minGap)
            val hi = minOf(n - 1, i + minGap)
            var isPeak = true
            for (j in lo..hi) if (abs(novelty[j]) > v || (abs(novelty[j]) == v && j < i)) { isPeak = false; break }
            if (isPeak) peaks.add(i - SMOOTH_HOPS / 2) // undo the lag of the causal moving average
        }
        return peaks
    }

    private fun mergeShort(
        edges: List<Long>,
        durationMs: Long,
    ): List<Long> {
        val out = edges.toMutableList()
        var i = 1
        while (i < out.size - 1) {
            if (out[i] - out[i - 1] < MIN_SEGMENT_MS || out[i + 1] - out[i] < MIN_SEGMENT_MS) out.removeAt(i) else i++
        }
        if (out.last() != durationMs) out[out.lastIndex] = durationMs
        return out
    }

    private fun smooth(values: FloatArray): FloatArray {
        val out = FloatArray(values.size)
        var sum = 0f
        for (i in values.indices) {
            sum += values[i]
            if (i >= SMOOTH_HOPS) sum -= values[i - SMOOTH_HOPS]
            out[i] = sum / minOf(i + 1, SMOOTH_HOPS)
        }
        return out
    }
}
