/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.abs

class TransientAlignerTest {
    /** Kick curve with a short pulse every [beatMs], the first one at [firstMs], sampled every 10 ms. */
    private fun kicks(
        lengthMs: Int,
        beatMs: Int,
        firstMs: Int,
    ): FloatArray =
        FloatArray(lengthMs / 10) { i ->
            val t = i * 10
            val d = (t - firstMs) % beatMs
            if (t >= firstMs && d in 0..20) 1f else 0f
        }

    @Test
    fun `finds the lag where B's kicks coincide with A's`() {
        val a = OnsetSlice(kicks(8_000, 500, 0), 10.0)
        // B's kicks sit 120 ms later than A's; the slice starts 250 ms (padMs) before B's start position.
        val b = OnsetSlice(kicks(10_000, 500, 120 + 250), 10.0)

        val shift = TransientAligner.alignmentShiftMs(a, b, factor = 1.0, padMs = 250.0, maxLagMs = 250.0)

        assertNotNull(shift)
        assertEquals(120.0, shift!!, 15.0)
    }

    @Test
    fun `negative lag when B's kicks are early`() {
        val a = OnsetSlice(kicks(8_000, 500, 100), 10.0)
        val b = OnsetSlice(kicks(10_000, 500, 250), 10.0)

        val shift = TransientAligner.alignmentShiftMs(a, b, 1.0, 250.0, 250.0)!!

        assertEquals(-100.0, shift, 15.0)
    }

    @Test
    fun `flat or empty curves give nothing`() {
        val flat = OnsetSlice(FloatArray(800), 10.0)
        assertNull(TransientAligner.alignmentShiftMs(flat, flat, 1.0, 0.0, 250.0))
        assertNull(TransientAligner.alignmentShiftMs(OnsetSlice(FloatArray(4), 10.0), flat, 1.0, 0.0, 250.0))
    }

    @Test
    fun `plan shift moves B and its grid together`() {
        val grid = BeatGrid(100.0, 500.0)
        val plan =
            TransitionPlan(
                kind = TransitionKind.DROP_TO_CALM, mixOutPositionA = 100_000, fadeOutDurationA = 1_000,
                mixInPositionB = 20_000, fadeInDurationB = 8_000, speedFactorB = 1f, tempoMatched = true,
                cost = 0.0, gridA = grid, gridB = grid,
            )
        val shifted = plan.withBShift(120)
        assertEquals(20_120L, shifted.mixInPositionB)
        assertEquals(220.0, shifted.gridB!!.downbeatMs, 1e-9)
        assertEquals(120L, shifted.alignmentShiftMs)
        assertEquals(true, abs(plan.phaseErrorMs(plan.startA, plan.secondaryStartPositionMs)!! - 0.0) < 1e-6)
    }
}
