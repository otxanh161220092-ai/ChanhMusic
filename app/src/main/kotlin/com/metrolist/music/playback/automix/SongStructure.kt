/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

/**
 * Everything the transition calculator needs to know about one song.
 *
 * @property gridNear measures the beat grid around a track position, or null when the song has no
 * usable tempo. Positions are milliseconds of track time.
 */
class SongStructure(
    val durationMs: Long,
    val segments: List<SongSegment>,
    val bpm: Float = 0f,
    val hasTempo: Boolean = false,
    val gridNear: (Long) -> BeatGrid? = { null },
    /** Pauses between sung lines. Null when no lyrics are known; empty when the song is sung throughout. */
    val vocalGaps: List<LongRange>? = null,
    /** Kick (low-band onset) curve over [startMs, startMs + lengthMs), or null when it is not known. */
    val onsetSliceAt: (Long, Long) -> OnsetSlice? = { _, _ -> null },
)

/** Builds a structure from analysis results from the loudness curve (falling back to the length-based heuristic when it is flat). */
fun TrackFeatures.toSongStructure(vocalGaps: List<LongRange>? = null): SongStructure =
    SongStructure(
        durationMs = durationMs,
        segments = LoudnessSegmenter.segment(durationMs, loudnessDb) ?: HeuristicSegmenter.segment(durationMs, loudnessDb),
        bpm = bpm,
        hasTempo = hasTempo,
        gridNear = { positionMs -> if (hasTempo) gridNear(positionMs.toDouble()) else null },
        vocalGaps = vocalGaps,
        onsetSliceAt = { startMs, lengthMs ->
            if (!hasTempo || hopMs <= 0.0 || lowOnset.isEmpty()) {
                null
            } else {
                val from = (startMs / hopMs).toInt()
                val count = (lengthMs / hopMs).toInt()
                if (from < 0 || count <= 0 || from + count > lowOnset.size) null else OnsetSlice(lowOnset.copyOfRange(from, from + count), hopMs)
            }
        },
    )
