/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import timber.log.Timber

/**
 * Measures the beat offset between deck A and deck B during the overlap and returns a tempo
 * multiplier for B. The offset is measured on each song's beat grid, not on the absolute playback
 * clock, so the result does not depend on where a song starts. The multiplier is applied to B's
 * tempo as a whole, and the caller must multiply B's base speed by it, not replace the speed.
 */
class PhaseDriftTracker(
    private val gridA: BeatGrid?,
    private val gridB: BeatGrid?,
) {
    /** Returns 1f when either song has no beat grid, so the transition runs without correction. */
    fun trackAndCorrect(
        positionA: Long,
        positionB: Long,
    ): Float {
        val a = gridA ?: return 1f
        val b = gridB ?: return 1f
        val errorBeats = BeatPhase.errorBeats(positionA.toDouble(), a, positionB.toDouble(), b)
        val errorMs = errorBeats * a.beatMs
        val multiplier = PhaseController.speedMultiplier(errorBeats, a.beatMs)
        Timber.tag(TAG).d(
            "posA=%d posB=%d phaseError=%.1f ms speedMultiplier=%.4f",
            positionA,
            positionB,
            errorMs,
            multiplier,
        )
        return multiplier.toFloat()
    }

    private companion object {
        const val TAG = "AutomixPhaseTracker"
    }
}
