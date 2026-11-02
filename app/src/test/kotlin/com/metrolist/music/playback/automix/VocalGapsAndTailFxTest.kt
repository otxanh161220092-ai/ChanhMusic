/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class VocalGapsAndTailFxTest {
    @Test
    fun `gaps appear between lines that are far apart and at the ends`() {
        // Lines start at 1 s, 2 s, then 20 s. Each line lasts up to 5 s.
        val gaps = VocalGaps.fromLineStarts(listOf(1_000L, 2_000L, 20_000L), durationMs = 30_000L)

        assertTrue(gaps.any { 0L in it })
        assertTrue(gaps.any { 7_000L in it && 19_000L in it })
        assertTrue(gaps.any { 25_500L in it })
        assertTrue(gaps.none { 10_000L in it && 21_000L in it })
    }

    @Test
    fun `sung fraction is one with no pauses and zero inside a pause`() {
        val gaps = listOf(10_000L..20_000L)

        assertEquals(1.0, VocalGaps.vocalFraction(gaps, 0L, 10_000L)!!, 1e-9)
        assertEquals(0.0, VocalGaps.vocalFraction(gaps, 10_000L, 20_000L)!!, 1e-9)
        assertEquals(0.5, VocalGaps.vocalFraction(gaps, 0L, 20_000L)!!, 1e-9)
    }

    @Test
    fun `no lyric data is neutral, not fully sung`() {
        assertNull(VocalGaps.vocalFraction(null, 0L, 1_000L))
        // A song sung throughout has no pauses at all, which is different from having no lyrics.
        assertEquals(1.0, VocalGaps.vocalFraction(emptyList(), 0L, 1_000L)!!, 1e-9)
    }

    @Test
    fun `low-pass sweep passes DC and attenuates Nyquist once closed`() {
        val sampleRate = 44_100
        val filter = OnePoleLowPass()
        var dc = 0.0
        repeat(5_000) { dc = filter.process(1.0, 400.0, sampleRate) }
        assertEquals(1.0, dc, 1e-3)

        filter.reset()
        var peak = 0.0
        repeat(5_000) { i ->
            val x = if (i % 2 == 0) 1.0 else -1.0
            val y = filter.process(x, 400.0, sampleRate)
            if (i > 1_000) peak = maxOf(peak, kotlin.math.abs(y))
        }
        assertTrue("nyquist peak $peak", peak < 0.1)
    }

    @Test
    fun `cutoff sweep moves from open to closed across the window`() {
        val start = TailFxSweep.cutoffAt(0.0, 0.0, 1_000.0)
        val mid = TailFxSweep.cutoffAt(500.0, 0.0, 1_000.0)
        val end = TailFxSweep.cutoffAt(1_000.0, 0.0, 1_000.0)

        assertEquals(TailFxSweep.OPEN_HZ, start, 1e-6)
        assertEquals(TailFxSweep.CLOSED_HZ, end, 1e-6)
        assertTrue(mid < start && mid > end)
        assertEquals(cos(0.0 * PI), 1.0, 0.0)
    }

    @Test
    fun `echo repeats decay and ring out after the input stops`() {
        val echo = TailEcho(delayFrames = 4, feedback = 0.5)
        val out = DoubleArray(13) { i -> echo.process(if (i == 0) 1.0 else 0.0) }
        assertEquals(1.0, out[4], 1e-9)
        assertEquals(0.5, out[8], 1e-9)
        assertEquals(0.25, out[12], 1e-9)
        assertEquals(0.0, out[5], 1e-9)
    }

    @Test
    fun `tail dry signal fades with equal power while echo send stops at the end`() {
        assertEquals(1.0, TailFxMix.dryGain(0.0), 1e-9)
        assertEquals(Math.sqrt(0.5), TailFxMix.dryGain(0.5), 1e-9)
        assertEquals(0.0, TailFxMix.dryGain(1.0), 1e-9)
        assertEquals(0.0, TailFxMix.sendLevel(1.0), 1e-9)
        assertTrue(TailFxMix.sendLevel(0.5) > 0.0)
        assertEquals(2_250L, TailFxMix.ringOutMs(375))
    }

    @Test
    fun `reverb rings after an impulse and then dies away`() {
        val reverb = SimpleReverb(sampleRate = 8_000)
        val out = DoubleArray(16_000) { i -> reverb.process(if (i == 0) 1.0 else 0.0) }
        fun energy(from: Int, to: Int) = (from until to).sumOf { out[it] * out[it] }
        assertTrue(energy(400, 4_000) > 0.0)
        assertTrue(energy(12_000, 16_000) < energy(400, 4_000))
    }

    @Test
    fun `head effect opens the filter and fades the wash over the fade-in`() {
        assertEquals(HeadFx.START_HZ, HeadFx.cutoffAt(0.0), 1e-6)
        assertEquals(TailFxSweep.OPEN_HZ, HeadFx.cutoffAt(1.0), 1e-6)
        assertTrue(HeadFx.cutoffAt(0.5) > HeadFx.cutoffAt(0.2))
        assertEquals(HeadFx.WASH_LEVEL, HeadFx.washAt(0.0), 1e-9)
        assertEquals(0.0, HeadFx.washAt(1.0), 1e-9)
    }
}
