/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseDriftTest {
    private val grid = BeatGrid(downbeatMs = 0.0, beatMs = 500.0)

    @Test
    fun `zero error when beats coincide`() {
        assertEquals(0.0, BeatPhase.errorBeats(1_000.0, grid, 3_500.0, grid), 1e-9)
    }

    @Test
    fun `positive when B is behind A and wraps across the beat boundary`() {
        assertEquals(0.2, BeatPhase.errorBeats(1_100.0, grid, 1_000.0, grid), 1e-9)
        // A is on a beat, B is 50 ms before the next one: B is 0.1 beat behind.
        assertEquals(0.1, BeatPhase.errorBeats(1_000.0, grid, 1_450.0, grid), 1e-9)
        assertEquals(-0.1, BeatPhase.errorBeats(1_450.0, grid, 1_000.0, grid), 1e-9)
    }

    @Test
    fun `grids with different offsets compare actual beat instants`() {
        val shifted = BeatGrid(downbeatMs = 120.0, beatMs = 500.0)
        assertEquals(0.0, BeatPhase.errorBeats(1_000.0, grid, 1_120.0, shifted), 1e-9)
    }

    @Test
    fun `controller speeds B up when it is behind and slows it down when ahead`() {
        assertTrue(PhaseController.speedMultiplier(0.1, 500.0) > 1.0)
        assertTrue(PhaseController.speedMultiplier(-0.1, 500.0) < 1.0)
    }

    @Test
    fun `controller is clamped and holds still once locked`() {
        assertEquals(1.0 + PhaseController.MAX_NUDGE, PhaseController.speedMultiplier(0.5, 500.0), 1e-9)
        assertEquals(1.0 - PhaseController.MAX_NUDGE, PhaseController.speedMultiplier(-0.5, 500.0), 1e-9)
        // 0.02 beat of 500 ms is 10 ms, inside the lock tolerance.
        assertEquals(1.0, PhaseController.speedMultiplier(0.02, 500.0), 0.0)
    }
}

class TempoMeetingTest {
    @Test
    fun factorsMeetInTheMiddleAndKeepTheRatio() {
        val plan = TransitionPlan(TransitionKind.HIGH_TO_HIGH, 10_000, 8_000, 5_000, 8_000, 1.10f, true, 0.0, null, null)
        val a = plan.outgoingTempoFactor
        val b = plan.incomingTempoFactor
        assertEquals(1.10, (b / a).toDouble(), 1e-4)
        assertTrue(a < 1f && b > 1f)
    }
}
