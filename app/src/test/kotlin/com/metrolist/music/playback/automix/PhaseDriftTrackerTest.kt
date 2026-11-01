/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseDriftTrackerTest {
    private val grid = BeatGrid(downbeatMs = 0.0, beatMs = 500.0)

    @Test
    fun `no correction when beats coincide, regardless of absolute position`() {
        val tracker = PhaseDriftTracker(grid, grid)
        assertEquals(1f, tracker.trackAndCorrect(1_000L, 1_000L), 0f)
        assertEquals(1f, tracker.trackAndCorrect(123_000L, 123_000L), 0f)
    }

    @Test
    fun `speeds B up when it is behind and slows it down when ahead`() {
        val tracker = PhaseDriftTracker(grid, grid)
        assertTrue(tracker.trackAndCorrect(1_100L, 1_000L) > 1f)
        assertTrue(tracker.trackAndCorrect(1_000L, 1_100L) < 1f)
    }

    @Test
    fun `no grid means no correction`() {
        assertEquals(1f, PhaseDriftTracker(null, grid).trackAndCorrect(1_100L, 1_000L), 0f)
    }
}
