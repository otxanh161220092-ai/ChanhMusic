/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

/**
 * Where a transition happens.
 *
 * @property mixOutPositionA track-A position where its fade-out starts.
 * @property mixInPositionB track-B position that starts playing when the fade starts.
 * @property fadeDurationMs length of the overlap between the two tracks.
 */
data class SmartTransition(
    val mixOutPositionA: Long,
    val mixInPositionB: Long,
    val fadeDurationMs: Long,
)

/**
 * Chooses the mix points from the structure of two songs.
 *
 * Rules:
 * - A ends on a high-energy part (Drop/Chorus): the mix starts at that part and B enters on a calmer
 *   part (Verse, else Intro) to close the cycle.
 * - A ends on a calm part (Intro/Verse/Outro): A fades out over its last calm part and B jumps straight
 *   into its first high-energy part (Chorus/Drop, else Drop/Chorus of any position), keeping the build.
 * - Missing structure falls back to a plain fixed crossfade at the end of A.
 *
 * Pure Kotlin with no Android types, so it can be unit tested on the JVM.
 */
object SmartTransitionCalculator {
    const val DEFAULT_FADE_MS = 6_000L
    const val MIN_FADE_MS = 4_000L
    const val MAX_HIGH_ENERGY_FADE_MS = 16_000L
    const val MAX_BUILD_FADE_MS = 8_000L

    fun calculate(
        outgoing: List<SongSegment>,
        outgoingDurationMs: Long,
        incoming: List<SongSegment>,
    ): SmartTransition {
        require(outgoingDurationMs > 0) { "outgoingDurationMs must be > 0" }

        val tailA = outgoing.maxByOrNull { it.startTimeMs }
            ?: return fallback(outgoingDurationMs)

        val (mixOut, fade) =
            if (tailA.isHighEnergy) {
                // Start the fade at the drop/chorus and let it run through that section.
                val desired = tailA.durationMs.coerceIn(MIN_FADE_MS, MAX_HIGH_ENERGY_FADE_MS)
                tailA.startTimeMs to clampFade(tailA.startTimeMs, desired, outgoingDurationMs)
            } else {
                // Fade out over the calm section so it finishes right when B's high part arrives.
                val desired = tailA.durationMs.coerceIn(MIN_FADE_MS, MAX_BUILD_FADE_MS)
                val start = (tailA.endTimeMs - desired).coerceAtLeast(tailA.startTimeMs)
                start to clampFade(start, desired, outgoingDurationMs)
            }

        val mixIn =
            if (tailA.isHighEnergy) {
                // Drop/Chorus (A) -> Verse/Intro (B)
                findFirst(incoming, SegmentType.VERSE)?.startTimeMs
                    ?: findFirst(incoming, SegmentType.INTRO)?.startTimeMs
                    ?: 0L
            } else {
                // Verse/Intro/Outro (A) -> Chorus/Drop (B)
                findFirstHighEnergy(incoming)?.startTimeMs ?: 0L
            }

        return SmartTransition(
            mixOutPositionA = mixOut,
            mixInPositionB = mixIn,
            fadeDurationMs = fade,
        )
    }

    private fun fallback(outgoingDurationMs: Long): SmartTransition {
        val fade = clampFade(0L, DEFAULT_FADE_MS, outgoingDurationMs)
        return SmartTransition(
            mixOutPositionA = (outgoingDurationMs - fade).coerceAtLeast(0L),
            mixInPositionB = 0L,
            fadeDurationMs = fade,
        )
    }

    /** Keeps the fade inside the track: it may not run past the end of A. */
    private fun clampFade(
        mixOut: Long,
        desiredMs: Long,
        outgoingDurationMs: Long,
    ): Long = minOf(desiredMs, (outgoingDurationMs - mixOut).coerceAtLeast(0L))

    private fun findFirst(
        segments: List<SongSegment>,
        type: SegmentType,
    ): SongSegment? = segments.filter { it.type == type }.minByOrNull { it.startTimeMs }

    private fun findFirstHighEnergy(segments: List<SongSegment>): SongSegment? =
        segments.filter { it.isHighEnergy }.minByOrNull { it.startTimeMs }
}
