/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

/**
 * Temporary stand-in for a real structure analyzer. The layout follows song length:
 * 0-15% intro, 15-70% four equal blocks of verse/chorus, 70-85% drop, 85-100% outro.
 * When a loudness profile is available, the two louder middle blocks become choruses, and every
 * segment's energy is blended with its measured loudness, so two songs of equal length still differ.
 */
object HeuristicSegmenter {
    const val MIN_DURATION_MS = 30_000L

    private const val INTRO_END_PERCENT = 15
    private const val MIDDLE_END_PERCENT = 70
    private const val DROP_END_PERCENT = 85
    private const val MIDDLE_BLOCKS = 4

    fun segment(
        durationMs: Long,
        loudnessDb: FloatArray? = null,
    ): List<SongSegment> {
        if (durationMs < MIN_DURATION_MS) return emptyList()

        val introEnd = durationMs * INTRO_END_PERCENT / 100
        val middleEnd = durationMs * MIDDLE_END_PERCENT / 100
        val dropEnd = durationMs * DROP_END_PERCENT / 100
        val edges = LongArray(MIDDLE_BLOCKS + 1) { introEnd + (middleEnd - introEnd) * it / MIDDLE_BLOCKS }

        val blockTypes = middleBlockTypes(edges, loudnessDb)

        val ranges =
            buildList {
                add(Triple(SegmentType.INTRO, 0L, introEnd))
                for (i in 0 until MIDDLE_BLOCKS) add(Triple(blockTypes[i], edges[i], edges[i + 1]))
                add(Triple(SegmentType.DROP, middleEnd, dropEnd))
                add(Triple(SegmentType.OUTRO, dropEnd, durationMs))
            }

        val scale = loudnessScale(loudnessDb)
        return ranges.map { (type, start, end) ->
            val measured = if (scale != null) meanDb(loudnessDb, start, end)?.let { scale.normalize(it) } else null
            val energy = if (measured != null) 0.5f * type.defaultEnergy + 0.5f * measured else type.defaultEnergy
            SongSegment(type, start, end, energy)
        }
    }

    private fun middleBlockTypes(
        edges: LongArray,
        loudnessDb: FloatArray?,
    ): List<SegmentType> {
        val default = listOf(SegmentType.VERSE, SegmentType.CHORUS, SegmentType.VERSE, SegmentType.CHORUS)
        if (loudnessDb == null) return default
        val means = (0 until MIDDLE_BLOCKS).map { meanDb(loudnessDb, edges[it], edges[it + 1]) ?: return default }
        if (means.distinct().size < 2) return default
        val louderHalf = means.sortedDescending().take(MIDDLE_BLOCKS / 2).toSet()
        return means.map { if (it in louderHalf) SegmentType.CHORUS else SegmentType.VERSE }
    }

    private class LoudnessScale(
        val low: Float,
        val high: Float,
    ) {
        fun normalize(db: Float): Float = ((db - low) / (high - low)).coerceIn(0f, 1f)
    }

    /** Maps the 10th-90th percentile of audible loudness onto 0..1. */
    private fun loudnessScale(loudnessDb: FloatArray?): LoudnessScale? {
        val audible = loudnessDb?.filter { it > SILENCE_DB }?.sorted() ?: return null
        if (audible.size < 10) return null
        val low = audible[(audible.size * 0.1).toInt()]
        val high = audible[(audible.size * 0.9).toInt().coerceAtMost(audible.size - 1)]
        return if (high - low > 0.5f) LoudnessScale(low, high) else null
    }

    private fun meanDb(
        loudnessDb: FloatArray?,
        startMs: Long,
        endMs: Long,
    ): Float? {
        if (loudnessDb == null) return null
        val from = (startMs / LOUDNESS_HOP_MS).toInt().coerceIn(0, loudnessDb.size)
        val to = (endMs / LOUDNESS_HOP_MS).toInt().coerceIn(from, loudnessDb.size)
        if (to <= from) return null
        var sum = 0f
        for (i in from until to) sum += loudnessDb[i].coerceAtLeast(SILENCE_DB)
        return sum / (to - from)
    }

    private const val SILENCE_DB = -60f
}
