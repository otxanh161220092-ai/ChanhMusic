/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * Plans a transition from song A to song B by looking at both songs together.
 *
 * Every pair (section of A, section of B) that follows the energy rule is a candidate:
 * - A's section is high energy (Drop/Chorus) and B enters on a calm part (Intro/Verse): A keeps its
 *   energy up to the end of the section and is cut short, while B fades in over a long time, starting
 *   before the section ends. The handover completes exactly where A's section ends.
 * - A's section is calm and B has a high-energy part (Chorus/Drop): A fades out slowly through its
 *   calm section, and B comes in for a single beat so that its chorus lands exactly when the handover
 *   completes. B is pre-rolled, so it starts wherever its structure requires, not at 0:00.
 *
 * Each candidate is scored on how well the energies contrast, how far the tempos are apart, and how
 * much of each song would be skipped, so the best mix point in A changes with every B.
 *
 * Pure Kotlin with no Android types, so it can be unit tested on the JVM.
 */
object SmartTransitionCalculator {
    const val MIN_FADE_MS = 4_000L
    const val DEFAULT_LONG_FADE_MS = 16_000L

    /** Fade lengths in bars, longest first; the longest that fits the sections involved is used. */
    private val BAR_CHOICES = intArrayOf(8, 6, 4, 2)

    private const val MAX_TEMPO_DELTA = 0.08
    private const val SHORT_FADE_BEATS = 2.0
    private const val QUICK_FADE_BEATS = 1.0
    private const val QUICK_CALM_BEATS = 4.0
    private const val FALLBACK_BEAT_MS = 500.0

    private const val WEIGHT_ENERGY = 2.0
    private const val WEIGHT_TEMPO = 1.5
    private const val WEIGHT_SKIP_A = 0.8
    private const val WEIGHT_SKIP_B = 1.5
    private const val NO_TEMPO_COST = 1.0
    private const val UNMATCHABLE_TEMPO_COST = 3.0
    private const val WEIGHT_VOCAL = 2.0

    /** B should not start from 0:00 unless nothing else fits. */
    /** The overlap may not open before this share of A has played, so a song is never cut off at its start. */
    const val DEFAULT_MIN_PLAYED_FRACTION = 0.25

    private const val ECHO_BEATS = 0.75
    private const val START_AT_ZERO_COST = 0.3

    /**
     * @param earliestMixMs the overlap may not open before this position in A.
     * @param minPlayedFraction the overlap also may not open before this share of A's length.
     * @param longFadeMs the long fade, shortened to fit the sections involved.
     * @param quickCut when true, the transition is a single-beat cut: A's fade is one beat on a drop,
     * and four beats on a calm section instead of the long fade.
     * @return the best plan, or null when no pair of sections fits (the caller keeps a plain crossfade).
     */
    fun calculateTransitionPlan(
        songA: SongStructure,
        songB: SongStructure,
        earliestMixMs: Long = 0L,
        longFadeMs: Long = DEFAULT_LONG_FADE_MS,
        quickCut: Boolean = false,
        minPlayedFraction: Double = DEFAULT_MIN_PLAYED_FRACTION,
    ): TransitionPlan? {
        if (songA.durationMs <= 0 || songB.durationMs <= 0) return null
        val earliestMixMs = maxOf(earliestMixMs, (songA.durationMs * minPlayedFraction).toLong())
        val tempo = matchTempo(songA, songB)

        var best: TransitionPlan? = null
        for (a in songA.segments) {
            for (b in songB.segments) {
                val plan = candidate(a, b, songA, songB, tempo, earliestMixMs, longFadeMs, quickCut) ?: continue
                if (best == null || plan.cost < best.cost) best = plan
            }
        }
        return best
    }

    private fun candidate(
        a: SongSegment,
        b: SongSegment,
        songA: SongStructure,
        songB: SongStructure,
        tempo: TempoMatch,
        earliestMixMs: Long,
        longFadeMs: Long,
        quickCut: Boolean,
    ): TransitionPlan? {
        val kind =
            when {
                a.isHighEnergy && b.isCalmEntry -> TransitionKind.DROP_TO_CALM
                !a.isHighEnergy && b.isHighEnergy -> TransitionKind.CALM_TO_HIGH
                else -> return null
            }

        val factor = tempo.factor.toDouble()
        val gridA = songA.gridNear(a.endTimeMs)
        val gridB = songB.gridNear(b.startTimeMs)
        val barA = gridA?.barMs ?: 0.0
        val beatA =
            gridA?.beatMs ?: if (songA.hasTempo && songA.bpm > 0f) 60_000.0 / songA.bpm else FALLBACK_BEAT_MS

        // The handover completes at a bar line at or before the end of A's section.
        val handoverA = minOf(floorToBar(a.endTimeMs, gridA), songA.durationMs)
        val sectionA = handoverA - a.startTimeMs
        if (sectionA < MIN_FADE_MS) return null

        val fadeOut: Long
        val fadeIn: Long
        val handoverB: Long
        when (kind) {
            TransitionKind.DROP_TO_CALM -> {
                fadeOut = ((if (quickCut) QUICK_FADE_BEATS else SHORT_FADE_BEATS) * beatA).roundToLong().coerceAtLeast(1L)
                val entry = barAlignedStart(b, gridB)
                val room = minOf(longFadeMs, sectionA, ((b.endTimeMs - entry) / factor).toLong())
                fadeIn = pickBars(room, barA)
                if (fadeIn < MIN_FADE_MS) return null
                handoverB = entry + (fadeIn * factor).toLong()
            }
            TransitionKind.CALM_TO_HIGH -> {
                fadeIn = (QUICK_FADE_BEATS * beatA).roundToLong().coerceAtLeast(1L)
                handoverB = gridB?.nearestBar(b.startTimeMs.toDouble())?.toLong()?.coerceAtLeast(0L) ?: b.startTimeMs
                // B is pre-rolled before its chorus, so a chorus that comes early limits how long A can fade.
                val longCap = if (quickCut) (QUICK_CALM_BEATS * beatA).toLong() else longFadeMs
                val room = minOf(longCap, sectionA, (handoverB / factor).toLong())
                fadeOut = pickBars(room, barA)
                if (fadeOut < MIN_FADE_MS) return null
            }
        }

        val mixOutA = handoverA - fadeOut
        val mixInB = handoverB - (fadeIn * factor).toLong()
        val bFadeInStartA = handoverA - fadeIn
        val startA = minOf(mixOutA, bFadeInStartA)
        // B starts when the window opens; if its fade is shorter it plays silently until the fade begins.
        val secondaryStartB = mixInB - ((bFadeInStartA - startA) * factor).toLong()

        if (startA < earliestMixMs || startA < 0) return null
        if (secondaryStartB < 0 || handoverB > songB.durationMs) return null

        val contrast = if (kind == TransitionKind.DROP_TO_CALM) a.energy - b.energy else b.energy - a.energy
        val skipA = (songA.durationMs - handoverA).toDouble() / songA.durationMs
        val skipB = secondaryStartB.toDouble() / songB.durationMs
        // Share of the overlap where a sung line would sit on top of the other song's vocal.
        val sungA = VocalGaps.vocalFraction(songA.vocalGaps, mixOutA, handoverA)
        val sungB = VocalGaps.vocalFraction(songB.vocalGaps, mixInB, handoverB)
        val known = listOfNotNull(sungA, sungB)
        val vocalCost = if (known.isEmpty()) 0.0 else known.average()

        val cost =
            WEIGHT_ENERGY * (1.0 - contrast) / 2.0 +
                WEIGHT_TEMPO * tempo.cost +
                WEIGHT_SKIP_A * skipA +
                WEIGHT_SKIP_B * skipB +
                WEIGHT_VOCAL * vocalCost +
                (if (secondaryStartB < 1_000L) START_AT_ZERO_COST else 0.0)

        // Beat phase is only meaningful when the two beats are the same musical beat, not a folded half/double.
        val trackPhase = tempo.matched && !tempo.octaveFolded
        return TransitionPlan(
            kind = kind,
            mixOutPositionA = mixOutA,
            fadeOutDurationA = fadeOut,
            mixInPositionB = mixInB,
            fadeInDurationB = fadeIn,
            speedFactorB = tempo.factor,
            tempoMatched = tempo.matched,
            cost = cost,
            gridA = if (trackPhase) gridA else null,
            gridB = if (trackPhase) gridB else null,
            tailFxA = true,
            tailEchoMs = (ECHO_BEATS * beatA).roundToLong().coerceIn(150L, 1_000L),
        )
    }

    private val SongSegment.isCalmEntry: Boolean
        get() = type == SegmentType.INTRO || type == SegmentType.VERSE

    private class TempoMatch(
        val factor: Float,
        val matched: Boolean,
        val octaveFolded: Boolean,
        val cost: Double,
    )

    /** Factor to apply to B so its tempo equals A's, folding half/double tempo errors first. */
    private fun matchTempo(
        songA: SongStructure,
        songB: SongStructure,
    ): TempoMatch {
        if (!songA.hasTempo || !songB.hasTempo || songA.bpm <= 0f || songB.bpm <= 0f) {
            return TempoMatch(1f, false, false, NO_TEMPO_COST)
        }
        val raw = songA.bpm.toDouble() / songB.bpm
        var ratio = raw
        while (ratio > 1.4142) ratio /= 2.0
        while (ratio < 0.7071) ratio *= 2.0
        val folded = abs(raw - ratio) > 1e-6
        val delta = abs(ratio - 1.0)
        return if (delta <= MAX_TEMPO_DELTA) {
            TempoMatch(ratio.toFloat(), true, folded, delta / MAX_TEMPO_DELTA)
        } else {
            TempoMatch(1f, false, false, UNMATCHABLE_TEMPO_COST)
        }
    }

    private fun floorToBar(
        timeMs: Long,
        grid: BeatGrid?,
    ): Long {
        if (grid == null) return timeMs
        val bars = floor((timeMs - grid.downbeatMs) / grid.barMs)
        return (grid.downbeatMs + bars * grid.barMs).toLong()
    }

    /** A calm section starts on the first bar line inside it, so B does not enter mid-bar. */
    private fun barAlignedStart(
        segment: SongSegment,
        grid: BeatGrid?,
    ): Long {
        val aligned = grid?.firstBarAtOrAfter(segment.startTimeMs.toDouble())?.toLong() ?: return segment.startTimeMs
        return if (aligned < segment.endTimeMs - MIN_FADE_MS) aligned else segment.startTimeMs
    }

    /** Longest of 8/6/4/2 bars that fits in [roomMs]; without a beat grid the room is used as is. */
    private fun pickBars(
        roomMs: Long,
        barMs: Double,
    ): Long {
        if (barMs <= 0.0) return roomMs
        for (bars in BAR_CHOICES) {
            val length = (bars * barMs).toLong()
            if (length <= roomMs) return length
        }
        return roundDownToBars(roomMs, barMs)
    }

    private fun roundDownToBars(
        durationMs: Long,
        barMs: Double,
    ): Long = if (barMs <= 0.0) durationMs else (floor(durationMs / barMs) * barMs).toLong()
}
