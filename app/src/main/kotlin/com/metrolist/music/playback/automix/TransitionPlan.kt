/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class TransitionKind {
    /** A is on a drop/chorus and ends there; B enters on a calm part. A cuts short, B fades in long. */
    DROP_TO_CALM,

    /** A is on a calm part; B's chorus/drop lands exactly when A's long fade ends. B fades in for one beat. */
    CALM_TO_HIGH,

    /** A is on a drop/chorus; B's drop/chorus lands exactly where A's section ends (drop into drop). */
    HIGH_TO_HIGH,

    /** A is on a calm part; B enters on a calm part, with a long symmetric overlap. */
    CALM_TO_CALM,
}

/**
 * A transition between two specific songs. The two fades are independent and both finish at
 * [handoverEndA], the moment A is fully out and B fully in.
 *
 * Durations are in milliseconds at the user's normal speed. Positions are track positions.
 *
 * @property mixOutPositionA A's position where its fade-out starts.
 * @property mixInPositionB B's position where its fade-in starts.
 * @property speedFactorB tempo factor applied to B so its beat lands on A's beat.
 */
class TransitionPlan(
    val kind: TransitionKind,
    val mixOutPositionA: Long,
    val fadeOutDurationA: Long,
    val mixInPositionB: Long,
    val fadeInDurationB: Long,
    val speedFactorB: Float,
    val tempoMatched: Boolean,
    val cost: Double,
    val gridA: BeatGrid?,
    val gridB: BeatGrid?,
    /** Darken A's tail with a low-pass sweep while it is cut. */
    val tailFxA: Boolean = false,
    /** B's own beat shift found by cross-correlation, already applied to [mixInPositionB] and [gridB]. */
    val alignmentShiftMs: Long = 0L,
    /** Echo delay for A's tail in ms (about three quarters of a beat); 0 when A leaves without an echo. */
    val tailEchoMs: Long = 0L,
) {
    /**
     * Equal-power gains at [relMs] after the window opens: A's fade-out and B's fade-in, in that order.
     * Before a fade starts its gain is the resting value; after it ends, the other way round.
     */
    fun gainsAt(relMs: Double): Pair<Float, Float> {
        val outP = ((relMs - (mixOutPositionA - startA)) / fadeOutDurationA).coerceIn(0.0, 1.0)
        val inP = ((relMs - (bFadeInStartA - startA)) / fadeInDurationB).coerceIn(0.0, 1.0)
        return cos(outP * PI / 2).toFloat() to sin(inP * PI / 2).toFloat()
    }

    val handoverEndA: Long get() = mixOutPositionA + fadeOutDurationA

    /** A's position when B's fade-in starts. */
    val bFadeInStartA: Long get() = handoverEndA - fadeInDurationB

    /** A's position when the overlap window opens: whichever fade starts first. */
    val startA: Long get() = minOf(mixOutPositionA, bFadeInStartA)

    val windowMs: Long get() = handoverEndA - startA

    /**
     * B starts playing (silently if its fade is the shorter one) when the window opens, at the
     * position it needs so that its fade-in begins at [mixInPositionB].
     */
    val secondaryStartPositionMs: Long
        get() = mixInPositionB - ((bFadeInStartA - startA) * speedFactorB.toDouble()).toLong()

    /** B's position when the handover completes. */
    val handoverEndB: Long get() = mixInPositionB + (fadeInDurationB * speedFactorB.toDouble()).toLong()

    /** Phase can only be tracked when both beat grids are known and tempos are matched without octave folding. */
    val canTrackPhase: Boolean get() = gridA != null && gridB != null

    /** Beat phase error between the two songs at the given positions, in beats, or null without grids. */
    fun phaseErrorBeats(
        positionA: Long,
        positionB: Long,
    ): Double? {
        val a = gridA ?: return null
        val b = gridB ?: return null
        return BeatPhase.errorBeats(positionA.toDouble(), a, positionB.toDouble(), b)
    }

    fun phaseErrorMs(
        positionA: Long,
        positionB: Long,
    ): Double? = phaseErrorBeats(positionA, positionB)?.let { it * (gridA?.beatMs ?: 0.0) }

    /** Phase error predicted from the analysis at the start and end of the overlap, in milliseconds. */
    fun predictedPhaseErrorMs(): Pair<Double, Double>? {
        val start = phaseErrorMs(startA, secondaryStartPositionMs) ?: return null
        val end = phaseErrorMs(handoverEndA, handoverEndB) ?: return null
        return start to end
    }

    /**
     * The same plan with B started [shiftMs] later (earlier when negative): B's positions move, and so
     * does its beat grid, so the phase tracker keeps the alignment that was found.
     */
    fun withBShift(shiftMs: Long): TransitionPlan =
        TransitionPlan(
            kind = kind,
            mixOutPositionA = mixOutPositionA,
            fadeOutDurationA = fadeOutDurationA,
            mixInPositionB = mixInPositionB + shiftMs,
            fadeInDurationB = fadeInDurationB,
            speedFactorB = speedFactorB,
            tempoMatched = tempoMatched,
            cost = cost,
            gridA = gridA,
            gridB = gridB?.let { BeatGrid(it.downbeatMs + shiftMs, it.beatMs) },
            tailFxA = tailFxA,
            alignmentShiftMs = shiftMs,
            tailEchoMs = tailEchoMs,
        )
}
