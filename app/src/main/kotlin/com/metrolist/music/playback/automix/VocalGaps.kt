/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.max
import kotlin.math.min

/**
 * Finds the pauses between sung lines from the start times of the lyric lines. A line is assumed to
 * last until the next line starts, but no longer than [MAX_LINE_MS], so a long instrumental after a
 * short line still counts as a pause.
 */
object VocalGaps {
    const val MIN_GAP_MS = 600L
    const val MAX_LINE_MS = 5_000L

    fun fromLineStarts(
        startsMs: List<Long>,
        durationMs: Long,
    ): List<LongRange> {
        val starts = startsMs.filter { it in 0..durationMs }.distinct().sorted()
        if (starts.isEmpty()) return emptyList()
        val gaps = mutableListOf<LongRange>()
        if (starts.first() >= MIN_GAP_MS) gaps += 0L until starts.first()
        for (i in 0 until starts.size - 1) {
            val lineEnd = min(starts[i + 1], starts[i] + MAX_LINE_MS)
            if (starts[i + 1] - lineEnd >= MIN_GAP_MS) gaps += lineEnd until starts[i + 1]
        }
        val lastEnd = min(durationMs, starts.last() + MAX_LINE_MS)
        if (durationMs - lastEnd >= MIN_GAP_MS) gaps += lastEnd..durationMs
        return gaps
    }

    /**
     * Share of [fromMs, toMs) that is sung, 0..1. Null when there is no lyric data (gaps == null), so
     * the caller can treat the song as neutral instead of as fully sung.
     */
    fun vocalFraction(
        gaps: List<LongRange>?,
        fromMs: Long,
        toMs: Long,
    ): Double? {
        if (gaps == null) return null
        val span = toMs - fromMs
        if (span <= 0L) return 0.0
        var silent = 0L
        for (gap in gaps) {
            val overlap = min(toMs, gap.last + 1) - max(fromMs, gap.first)
            if (overlap > 0) silent += overlap
        }
        return 1.0 - silent.toDouble() / span
    }
}
