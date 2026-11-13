/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.abs
import kotlin.math.floor

object BeatPhase {
    /**
     * How far B's beat is behind (positive) or ahead of (negative) A's beat, as a fraction of a beat
     * in -0.5..0.5. Zero means the two beats are on the same instant. Positions are track positions.
     */
    fun errorBeats(
        positionA: Double,
        gridA: BeatGrid,
        positionB: Double,
        gridB: BeatGrid,
    ): Double {
        val a = fractional((positionA - gridA.downbeatMs) / gridA.beatMs)
        val b = fractional((positionB - gridB.downbeatMs) / gridB.beatMs)
        var error = a - b
        if (error >= 0.5) error -= 1.0 else if (error < -0.5) error += 1.0
        return error
    }

    private fun fractional(x: Double): Double = x - floor(x)
}

/**
 * Turns a measured phase error into a small tempo nudge for B, like a phase-locked loop: B speeds
 * up when it is behind A's beat and slows down when it is ahead. Time stretching keeps the pitch.
 */
object PhaseController {
    const val MAX_NUDGE = 0.08
    const val LOCK_TOLERANCE_MS = 12.0
    const val TIME_CONSTANT_MS = 800.0

    /** Multiplier for B's playback speed, 1.0 when the beats are already locked. */
    fun speedMultiplier(
        errorBeats: Double,
        beatMs: Double,
    ): Double {
        if (abs(errorBeats) * beatMs <= LOCK_TOLERANCE_MS) return 1.0
        // Changing speed by d moves B's phase by d/beatMs beats per ms; aim to remove the error in one time constant.
        val nudge = (errorBeats * beatMs / TIME_CONSTANT_MS).coerceIn(-MAX_NUDGE, MAX_NUDGE)
        return 1.0 + nudge
    }
}

/**
 * PI loop over the beat phase error. The proportional part pulls B onto A's beat; the integral part
 * learns the constant tempo bias that a proportional nudge alone leaves behind, so the error settles
 * at zero instead of at a small steady offset.
 */
class PhaseLoop(private val beatMs: Double) {
    private var integralMsSec = 0.0

    /** Speed multiplier for B given the current phase error (in beats) and the time since the last update. */
    fun update(errorBeats: Double, dtMs: Double): Double {
        val errorMs = errorBeats * beatMs
        integralMsSec = (integralMsSec + errorMs * dtMs / 1000.0).coerceIn(-INTEGRAL_LIMIT_MS_SEC, INTEGRAL_LIMIT_MS_SEC)
        if (abs(errorMs) <= PhaseController.LOCK_TOLERANCE_MS) return 1.0
        val proportional = errorMs / PhaseController.TIME_CONSTANT_MS
        val integral = integralMsSec / INTEGRAL_TIME_CONSTANT_SEC / PhaseController.TIME_CONSTANT_MS
        val nudge = (proportional + integral).coerceIn(-PhaseController.MAX_NUDGE, PhaseController.MAX_NUDGE)
        return 1.0 + nudge
    }

    companion object {
        const val INTEGRAL_TIME_CONSTANT_SEC = 4.0
        const val INTEGRAL_LIMIT_MS_SEC = 2_000.0
    }
}
