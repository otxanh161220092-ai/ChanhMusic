/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

/** Structural role of a part of a song, as found by the energy analysis. */
enum class SegmentType { INTRO, VERSE, CHORUS, DROP, OUTRO }

/** A structural part of a song. All times are in milliseconds of track time, end is exclusive. */
data class SongSegment(
    val type: SegmentType,
    val startTimeMs: Long,
    val endTimeMs: Long,
) {
    init {
        require(startTimeMs >= 0) { "startTimeMs must be >= 0, was $startTimeMs" }
        require(endTimeMs > startTimeMs) { "endTimeMs ($endTimeMs) must be > startTimeMs ($startTimeMs)" }
    }

    val durationMs: Long get() = endTimeMs - startTimeMs

    /** Chorus and drop are the high-energy parts; intro, verse and outro are calmer. */
    val isHighEnergy: Boolean get() = type == SegmentType.CHORUS || type == SegmentType.DROP
}
