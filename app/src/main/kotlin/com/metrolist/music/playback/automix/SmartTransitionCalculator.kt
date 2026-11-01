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
 * Chooses the mix points from the structure of two songs. The cut can land anywhere in A, not only
 * at its end: the first usable section of A (after [calculate]'s `earliestMixMs`) decides the case.
 *
 * - Case 1, A section is high energy (Drop/Chorus): the fade runs inside that section and finishes
 *   exactly where the section ends, so the listener hears the whole drop. B enters on a calm part
 *   (Intro/Verse).
 * - Case 2, A section is calm (Intro/Verse/Outro): the fade finishes exactly where that section ends,
 *   and B is pre-rolled so that its Chorus/Drop lands at that moment.
 *
 * Without usable structure it falls back to a fixed crossfade at the end of A.
 * Pure Kotlin with no Android types, so it can be unit tested on the JVM.
 */
object SmartTransitionCalculator {
    const val DEFAULT_FADE_MS = 6_000L
    const val MIN_FADE_MS = 4_000L
    const val MAX_FADE_MS = 16_000L

    fun calculate(
        outgoing: List<SongSegment>,
        outgoingDurationMs: Long,
        incoming: List<SongSegment>,
        earliestMixMs: Long = 0L,
    ): SmartTransition {
        require(outgoingDurationMs > 0) { "outgoingDurationMs must be > 0" }

        val calmEntryB = incoming.filter { it.isCalmEntry }.minByOrNull { it.startTimeMs }
        val highEntryB = incoming.filter { it.isHighEnergy }.minByOrNull { it.startTimeMs }

        val anchors =
            outgoing
                .filter { it.durationMs >= MIN_FADE_MS && it.endTimeMs <= outgoingDurationMs }
                .sortedBy { it.startTimeMs }

        for (anchor in anchors) {
            var fade = minOf(anchor.durationMs, MAX_FADE_MS)
            if (anchor.endTimeMs - fade < earliestMixMs) continue

            if (anchor.isHighEnergy) {
                if (calmEntryB == null) continue
                return SmartTransition(anchor.endTimeMs - fade, calmEntryB.startTimeMs, fade)
            }

            if (highEntryB == null) continue
            // Pre-roll B so that its high-energy section starts exactly when the fade ends.
            var mixIn = highEntryB.startTimeMs - fade
            if (mixIn < 0) {
                // B's high section comes sooner than a full fade: shorten the fade if still long enough.
                if (highEntryB.startTimeMs >= MIN_FADE_MS) fade = highEntryB.startTimeMs
                mixIn = 0L
            }
            val mixOut = anchor.endTimeMs - fade
            if (mixOut < earliestMixMs) continue
            return SmartTransition(mixOut, mixIn, fade)
        }

        return fallback(outgoingDurationMs)
    }

    private val SongSegment.isCalmEntry: Boolean
        get() = type == SegmentType.INTRO || type == SegmentType.VERSE

    private fun fallback(outgoingDurationMs: Long): SmartTransition {
        val fade = minOf(DEFAULT_FADE_MS, outgoingDurationMs)
        return SmartTransition(outgoingDurationMs - fade, 0L, fade)
    }
}
