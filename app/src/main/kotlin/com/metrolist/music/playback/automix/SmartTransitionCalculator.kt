/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import timber.log.Timber
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
 * - A's section is calm and B has a high-energy part (Chorus/Drop): A fades out through its calm
 *   section while B rises over the same stretch, so that its chorus is fully in exactly when the handover
 *   completes. B is pre-rolled, so it starts wherever its structure requires, not at 0:00.
 *
 * Each candidate is scored on how well the energies contrast, how far the tempos are apart, and how
 * much of each song would be skipped, so the best mix point in A changes with every B.
 *
 * Pure Kotlin with no Android types, so it can be unit tested on the JVM.
 */
object SmartTransitionCalculator {
    const val MIN_FADE_MS = 3_000L
    const val DEFAULT_LONG_FADE_MS = 16_000L

    /** Fade lengths in bars, longest first; the longest that fits the sections involved is used. */
    private val BARS_LONG = intArrayOf(16, 8, 6, 4, 2)
    private val BARS_MEDIUM = intArrayOf(8, 6, 4, 2)

    /**
     * Auto-mix length in beats, longest first, and its overall ceiling. Counting in beats keeps the mix
     * on the phrase the ear follows; the 12 s ceiling stops slow songs from blending for too long.
     */
    private val BEATS_AUTO = intArrayOf(32, 24, 16, 8)
    private const val AUTO_MAX_FADE_MS = 16_000L
    private val BARS_SHORT = intArrayOf(4, 2)

    // Folded ratios sit in [0.707, 1.414], so every pair is within this delta and gets matched.
    private const val MAX_TEMPO_DELTA = 0.42
    private const val SHORT_FADE_BEATS = 2.0
    private const val QUICK_FADE_BEATS = 1.0
    private const val QUICK_MIN_FADE_MS = 1_000L
    private const val QUICK_CALM_BEATS = 4.0
    private const val FALLBACK_BEAT_MS = 500.0

    private const val WEIGHT_ENERGY = 2.0
    private const val WEIGHT_TEMPO = 1.5
    private const val WEIGHT_SKIP_A = 0.8
    private const val WEIGHT_B_ENTRY = 2.0
    private const val CALM_ENTRY_FREE_UNTIL = 0.2
    private const val HIGH_ENTRY_FREE_UNTIL = 0.4
    private const val MIN_B_REMAINING_MS = 30_000L
    private const val MIN_B_REMAINING_FRACTION = 0.2
    private const val ALIGN_WINDOW_MS = 8_000.0
    private const val NO_TEMPO_COST = 1.0
    private const val UNMATCHABLE_TEMPO_COST = 3.0
    private const val WEIGHT_VOCAL = 6.0

    /** B should not start from 0:00 unless nothing else fits. */
    /** The overlap may not open before this share of A has played, so a song is never cut off at its start. */
    const val DEFAULT_MIN_PLAYED_FRACTION = 0.25

    private const val ECHO_BEATS = 0.75
    private const val LATE_MIX_WEIGHT = 3.0
    private const val BEATLESS_MIX_COST = 2.0
    private const val END_MARGIN_MS = 300L
    private const val FALLBACK_COST = 99.0
    private const val RELAXED_MIN_FADE_MS = 1_500L
    private const val RELAXED_MIN_B_REMAINING_MS = 8_000L
    private const val START_AT_ZERO_COST = 0.5
    private const val OUTRO_SEGUE_COST = 3.0

    /**
     * @param earliestMixMs the overlap may not open before this position in A.
     * @param minPlayedFraction the overlap also may not open before this share of A's length.
     * @param longFadeMs the long fade, shortened to fit the sections involved.
     * @param quickCut when true, the transition is a single-beat cut: A's fade is one beat on a drop,
     * and four beats on a calm section instead of the long fade.
     * @return the best plan. Every pair of songs gets one (a structure-free overlap when no sections fit);
     * null only when A has about a second left.
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
        val originalEarliest = earliestMixMs
        val earliestMixMs = maxOf(earliestMixMs, (songA.durationMs * minPlayedFraction).toLong())
        val tempo = matchTempo(songA, songB)

        // Strict pass: the energy rules, enough of A played and room for B to play on.
        var best = bestCandidate(songA, songB, tempo, earliestMixMs, longFadeMs, quickCut, relaxed = false)
        var pass = "strict"
        // Relaxed pass: any section pair, no minimum share of A, shorter fades. Every song pair must be mixable.
        if (best == null) {
            best = bestCandidate(songA, songB, tempo, originalEarliest, longFadeMs, quickCut, relaxed = true)
            pass = "relaxed"
        }
        // Last resort when no sections are known or none fit: overlap A's ending with B's beginning.
        if (best == null) pass = "structure-free"
        val plan = best ?: structureFreePlan(songA, songB, tempo, originalEarliest, longFadeMs, quickCut)
        Timber.tag("MusicService").d("Mix plan: %s, mixOutA=%d ms of %d ms", pass, plan?.mixOutPositionA ?: -1L, songA.durationMs)
        return plan?.let { refineWithCrossCorrelation(it, songA, songB) }
    }

    private fun bestCandidate(
        songA: SongStructure,
        songB: SongStructure,
        tempo: TempoMatch,
        earliestMixMs: Long,
        longFadeMs: Long,
        quickCut: Boolean,
        relaxed: Boolean,
    ): TransitionPlan? {
        var best: TransitionPlan? = null
        var bestScore = Double.MAX_VALUE
        for (a in songA.segments) {
            for (b in songB.segments) {
                val plan = candidate(a, b, songA, songB, tempo, earliestMixMs, longFadeMs, quickCut, relaxed) ?: continue
                val score = plan.cost + lateMixCost(plan, songA)
                if (score < bestScore) {
                    bestScore = score
                    best = plan
                }
            }
        }
        return best
    }

    /**
     * Prefers mixing out near the end of A: the earlier the mix starts, the higher the cost. A mix that
     * runs past A's last beat also costs extra, so it moves earlier until it stays on the beat.
     */
    private fun lateMixCost(
        plan: TransitionPlan,
        songA: SongStructure,
    ): Double {
        val duration = songA.durationMs.coerceAtLeast(1L).toDouble()
        val early = 1.0 - (plan.mixOutPositionA + plan.fadeOutDurationA).toDouble() / duration
        var cost = LATE_MIX_WEIGHT * early.coerceAtLeast(0.0)
        val lastBeat = songA.beatEndMs
        if (lastBeat != null && plan.mixOutPositionA + plan.fadeOutDurationA > lastBeat) cost += BEATLESS_MIX_COST
        return cost
    }

    /**
     * A plan that needs no section analysis: A's last bars overlap B's first bars with an equal-power
     * crossfade. Used when the songs could not be analyzed or no section pair fits, so every song still
     * gets a mix. Null only when A has less than about a second left.
     */
    private fun structureFreePlan(
        songA: SongStructure,
        songB: SongStructure,
        tempo: TempoMatch,
        earliestMixMs: Long,
        longFadeMs: Long,
        quickCut: Boolean,
    ): TransitionPlan? {
        val endA = songA.durationMs - END_MARGIN_MS
        val room = endA - earliestMixMs
        if (songA.durationMs < 1_500L || songB.durationMs < 1_500L) return null
        val gridA = songA.gridNear(endA)
        val gridB = songB.gridNear(0L)
        val beatA = gridA?.beatMs ?: if (songA.hasTempo && songA.bpm > 0f) 60_000.0 / songA.bpm else FALLBACK_BEAT_MS
        val barA = gridA?.barMs ?: 0.0

        val cap = minOf(longFadeMs, maxOf(room, 500L), songA.durationMs / 2, songB.durationMs / 2).coerceAtLeast(500L)
        val fade =
            if (quickCut) {
                minOf((QUICK_CALM_BEATS * beatA).toLong(), cap)
            } else if (beatA > 0.0) {
                pickBeats(minOf(cap, AUTO_MAX_FADE_MS), beatA).coerceAtLeast(minOf(cap, 1_000L))
            } else {
                pickBars(cap, barA, BARS_MEDIUM).coerceAtLeast(minOf(cap, 1_000L))
            }
        val handover = floorToBar(endA, gridA).let { if (it - fade >= earliestMixMs) it else endA }
        // B starts where its beat starts, so an ambient or sound-effect intro is skipped.
        val entryB = songB.beatEntryMs ?: gridB?.firstBarAtOrAfter(0.0)?.toLong()?.takeIf { it < 4_000L } ?: 0L
        val trackPhase = tempo.matched
        Timber.tag("MusicService").d("Phase v2: matched=%b folded=%b gridA=%b gridB=%b", tempo.matched, tempo.octaveFolded, gridA != null, gridB != null)
        return TransitionPlan(
            kind = TransitionKind.CALM_TO_CALM,
            mixOutPositionA = handover - fade,
            fadeOutDurationA = fade,
            mixInPositionB = entryB,
            fadeInDurationB = fade,
            speedFactorB = tempo.factor,
            tempoMatched = tempo.matched,
            cost = FALLBACK_COST,
            gridA = if (trackPhase) gridA else null,
            gridB = if (trackPhase) gridB else null,
            tailFxA = true,
            tailEchoMs = (ECHO_BEATS * beatA).roundToLong().coerceIn(150L, 1_000L),
        )
    }

    /**
     * Fine-tunes B's start so its kicks land on A's kicks: cross-correlates the two kick curves around
     * the overlap and moves B by the lag of the correlation peak, instead of trusting the beat grid alone.
     */
    private fun refineWithCrossCorrelation(
        plan: TransitionPlan,
        songA: SongStructure,
        songB: SongStructure,
    ): TransitionPlan {
        if (!plan.tempoMatched || !plan.canTrackPhase) return plan
        val factor = plan.speedFactorB.toDouble()
        val beatMs = plan.gridA?.beatMs ?: return plan
        val maxLagMs = beatMs / 2.0
        val lengthMs = minOf(ALIGN_WINDOW_MS, plan.windowMs.toDouble()).toLong()
        val padMs = maxLagMs * factor
        val a = songA.onsetSliceAt(plan.startA, lengthMs) ?: return plan
        val bStart = (plan.secondaryStartPositionMs - padMs).toLong()
        val b = songB.onsetSliceAt(bStart, ((lengthMs + maxLagMs) * factor + 2 * padMs).toLong()) ?: return plan
        val shift = TransientAligner.alignmentShiftMs(a, b, factor, padMs, maxLagMs) ?: return plan
        val rounded = shift.roundToLong()
        return if (rounded == 0L || plan.secondaryStartPositionMs + rounded < 0) plan else plan.withBShift(rounded)
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
        relaxed: Boolean = false,
    ): TransitionPlan? {
        // An outro is written as a hand-off to the next track on the album: the normal pass never uses it,
        // and the relaxed pass uses it only when no other section pair fits.
        // A's outro is allowed, so the mix can run to the end of A; B's outro is still a hand-off only.
        if (!relaxed && b.type == SegmentType.OUTRO) return null
        val outroCost = if (b.type == SegmentType.OUTRO) OUTRO_SEGUE_COST else 0.0
        val minFade = if (relaxed) RELAXED_MIN_FADE_MS else MIN_FADE_MS
        val bCalm = b.isCalmEntry || (relaxed && b.type == SegmentType.OUTRO)
        // The four cases: where A is (high energy or calm) and where B has to land (calm entry or high energy).
        val kind =
            when {
                a.isHighEnergy && bCalm -> TransitionKind.DROP_TO_CALM
                !a.isHighEnergy && b.isHighEnergy -> TransitionKind.CALM_TO_HIGH
                a.isHighEnergy && b.isHighEnergy -> TransitionKind.HIGH_TO_HIGH
                bCalm -> TransitionKind.CALM_TO_CALM
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
        if (sectionA < minFade) return null

        val fadeOut: Long
        val fadeIn: Long
        val handoverB: Long
        val entryB: Long
        when (kind) {
            TransitionKind.DROP_TO_CALM, TransitionKind.CALM_TO_CALM -> {
                val symmetric = kind == TransitionKind.CALM_TO_CALM
                entryB = beatEntry(b, songB, gridB)
                val cap = if (symmetric) longFadeMs * 2 else longFadeMs
                val room = minOf(cap, sectionA, ((b.endTimeMs - entryB) / factor).toLong())
                val bars = if (symmetric) BARS_LONG else BARS_MEDIUM
                fadeIn = pickBars(room, barA, bars)
                if (fadeIn < minFade) return null
                // A leaves quickly from a drop; from a calm part it leaves as slowly as B arrives.
                fadeOut =
                    if (symmetric) fadeIn else ((if (quickCut) QUICK_FADE_BEATS else SHORT_FADE_BEATS) * beatA).roundToLong().coerceAtLeast(1L)
                handoverB = entryB + (fadeIn * factor).toLong()
            }
            TransitionKind.CALM_TO_HIGH, TransitionKind.HIGH_TO_HIGH -> {
                handoverB = gridB?.nearestBar(b.startTimeMs.toDouble())?.toLong()?.coerceAtLeast(0L) ?: b.startTimeMs
                entryB = handoverB
                // B is pre-rolled before its chorus, so a chorus that comes early limits how long A can fade.
                val highToHigh = kind == TransitionKind.HIGH_TO_HIGH
                val longCap = if (quickCut) (QUICK_CALM_BEATS * beatA).toLong() else longFadeMs
                val room = minOf(longCap, sectionA, (handoverB / factor).toLong())
                fadeOut = pickBars(room, barA, if (highToHigh) BARS_SHORT else BARS_MEDIUM)
                if (fadeOut < (if (quickCut) QUICK_MIN_FADE_MS else minFade)) return null
                // B rises through the whole overlap, so it is clearly audible, and already building, while A
                // fades; it is at full level exactly where its chorus lands. Only the quick cut is a one-beat entry.
                fadeIn = if (quickCut) (QUICK_FADE_BEATS * beatA).roundToLong().coerceAtLeast(1L) else fadeOut
            }
        }

        // B has to keep playing after the handover, so it never enters near its own end.
        val minRemaining = if (relaxed) RELAXED_MIN_B_REMAINING_MS else maxOf(MIN_B_REMAINING_MS, (songB.durationMs * MIN_B_REMAINING_FRACTION).toLong())
        if (songB.durationMs - handoverB < minRemaining) return null

        val mixOutA = handoverA - fadeOut
        val mixInB = handoverB - (fadeIn * factor).toLong()
        val bFadeInStartA = handoverA - fadeIn
        val startA = minOf(mixOutA, bFadeInStartA)
        // B starts when the window opens; if its fade is shorter it plays silently until the fade begins.
        val secondaryStartB = mixInB - ((bFadeInStartA - startA) * factor).toLong()

        if (startA < earliestMixMs || startA < 0) return null
        if (secondaryStartB < 0 || handoverB > songB.durationMs) return null

        // Different-energy pairs want contrast; same-energy pairs want the two sections to match.
        val contrast =
            when (kind) {
                TransitionKind.DROP_TO_CALM -> a.energy - b.energy
                TransitionKind.CALM_TO_HIGH -> b.energy - a.energy
                else -> 1.0f - 2.0f * abs(a.energy - b.energy)
            }
        val skipA = (songA.durationMs - handoverA).toDouble() / songA.durationMs
        // B should come in at a fitting place: not at 0:00 for no reason, and not deep into the song.
        val posB = entryB.toDouble() / songB.durationMs
        val freeUntil = if (b.isHighEnergy) HIGH_ENTRY_FREE_UNTIL else CALM_ENTRY_FREE_UNTIL
        val lateness = (posB - freeUntil).coerceAtLeast(0.0) / (1.0 - freeUntil)
        // Share of the overlap where a sung line would sit on top of the other song's vocal.
        val sungA = VocalGaps.vocalFraction(songA.vocalGaps, mixOutA, handoverA)
        val sungB = VocalGaps.vocalFraction(songB.vocalGaps, mixInB, handoverB)
        val known = listOfNotNull(sungA, sungB)
        // Worst side counts: a sung line over the other song's vocal is what makes the mix sound messy.
        val vocalCost = known.maxOrNull() ?: 0.0

        val cost =
            WEIGHT_ENERGY * (1.0 - contrast) / 2.0 +
                WEIGHT_TEMPO * tempo.cost +
                WEIGHT_SKIP_A * skipA +
                WEIGHT_B_ENTRY * lateness +
                WEIGHT_VOCAL * vocalCost +
                (if (secondaryStartB < 1_000L) START_AT_ZERO_COST else 0.0) +
                outroCost

        // Half/double tempos still share one pulse (their ratio is a power of two), so beat phase is tracked for them too.
        val trackPhase = tempo.matched
        Timber.tag("MusicService").d("Phase v2: matched=%b folded=%b gridA=%b gridB=%b", tempo.matched, tempo.octaveFolded, gridA != null, gridB != null)
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
        // The delta gate below rejects mismatched tempi, so a low-confidence BPM is still safe to compare.
        if (songA.bpm <= 0f || songB.bpm <= 0f) {
            Timber.tag("MusicService").d("Tempo unavailable: A bpm=%.1f hasTempo=%b, B bpm=%.1f hasTempo=%b", songA.bpm, songA.hasTempo, songB.bpm, songB.hasTempo)
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

    /**
     * Where B enters a segment: the bar line inside it, but never before B's beat starts, so an intro with
     * no beat (ambience, sound effects) is skipped. Falls back to the bar-aligned start when the beat
     * starts before the segment or too close to its end.
     */
    private fun beatEntry(
        segment: SongSegment,
        songB: SongStructure,
        grid: BeatGrid?,
    ): Long {
        val base = barAlignedStart(segment, grid)
        val beatStart = songB.beatEntryMs ?: return base
        return if (beatStart > base && beatStart < segment.endTimeMs - MIN_FADE_MS) beatStart else base
    }

    /** A calm section starts on the first bar line inside it, so B does not enter mid-bar. */
    private fun barAlignedStart(
        segment: SongSegment,
        grid: BeatGrid?,
    ): Long {
        val aligned = grid?.firstBarAtOrAfter(segment.startTimeMs.toDouble())?.toLong() ?: return segment.startTimeMs
        return if (aligned < segment.endTimeMs - MIN_FADE_MS) aligned else segment.startTimeMs
    }

    /** Longest of [choices] (in bars) that fits in [roomMs]; without a beat grid the room is used as is. */
    private fun pickBars(
        roomMs: Long,
        barMs: Double,
        choices: IntArray,
    ): Long {
        if (barMs <= 0.0) return roomMs
        for (bars in choices) {
            val length = (bars * barMs).toLong()
            if (length <= roomMs) return length
        }
        return roundDownToBars(roomMs, barMs)
    }

    /** The longest whole number of beats in [BEATS_AUTO] that fits in [roomMs]; otherwise the room itself. */
    private fun pickBeats(
        roomMs: Long,
        beatMs: Double,
    ): Long {
        for (beats in BEATS_AUTO) {
            val length = (beats * beatMs).toLong()
            if (length <= roomMs) return length
        }
        return roomMs
    }

    private fun roundDownToBars(
        durationMs: Long,
        barMs: Double,
    ): Long = if (barMs <= 0.0) durationMs else (floor(durationMs / barMs) * barMs).toLong()
}
