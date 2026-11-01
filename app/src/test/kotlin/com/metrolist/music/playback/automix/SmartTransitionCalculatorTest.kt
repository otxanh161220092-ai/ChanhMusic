/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTransitionCalculatorTest {
    private fun seg(
        type: SegmentType,
        start: Long,
        end: Long,
    ) = SongSegment(type, start, end)

    private fun song(
        durationMs: Long,
        vararg segments: SongSegment,
        bpm: Float = 0f,
        grid: BeatGrid? = null,
        vocal: List<LongRange>? = null,
    ) = SongStructure(
        durationMs = durationMs,
        segments = segments.toList(),
        bpm = bpm,
        hasTempo = bpm > 0f,
        gridNear = { grid },
        vocalGaps = vocal?.let { sung -> complement(sung, durationMs) },
    )

    /** Turns the sung ranges into the pauses between them, which is what [SongStructure.vocalGaps] holds. */
    private fun complement(
        sung: List<LongRange>,
        durationMs: Long,
    ): List<LongRange> {
        val gaps = mutableListOf<LongRange>()
        var cursor = 0L
        for (r in sung.sortedBy { it.first }) {
            if (r.first > cursor) gaps += cursor until r.first
            cursor = maxOf(cursor, r.last + 1)
        }
        if (cursor < durationMs) gaps += cursor..durationMs
        return gaps
    }

    private fun plan(
        a: SongStructure,
        b: SongStructure,
        earliest: Long = 0L,
    ) = SmartTransitionCalculator.calculateTransitionPlan(a, b, earliest, longFadeMs = 12_000L)!!

    @Test
    fun `drop in A hands over exactly where the drop ends and B enters calm with a long fade`() {
        val a = song(200_000, seg(SegmentType.DROP, 100_000, 130_000))
        val b = song(200_000, seg(SegmentType.INTRO, 0, 20_000), seg(SegmentType.VERSE, 20_000, 60_000))

        val p = plan(a, b)

        assertEquals(TransitionKind.DROP_TO_CALM, p.kind)
        assertEquals(130_000L, p.handoverEndA)
        // B does not restart from 0:00 when it has a later calm part to enter on.
        assertEquals(20_000L, p.mixInPositionB)
        // A is cut short (2 beats at the 500 ms fallback beat), B fades in for much longer.
        assertEquals(1_000L, p.fadeOutDurationA)
        assertEquals(12_000L, p.fadeInDurationB)
        assertTrue(p.fadeInDurationB > p.fadeOutDurationA)
        // B starts first and A keeps playing at full level until its short cut.
        assertEquals(118_000L, p.startA)
        assertEquals(129_000L, p.mixOutPositionA)
    }

    @Test
    fun `calm A pre-rolls B so its chorus lands when the long fade ends and B fades in for one beat`() {
        val a = song(200_000, seg(SegmentType.VERSE, 40_000, 80_000))
        val b = song(200_000, seg(SegmentType.INTRO, 0, 20_000), seg(SegmentType.CHORUS, 60_000, 90_000))

        val p = plan(a, b)

        assertEquals(TransitionKind.CALM_TO_HIGH, p.kind)
        assertEquals(80_000L, p.handoverEndA)
        assertEquals(12_000L, p.fadeOutDurationA)
        assertEquals(500L, p.fadeInDurationB)
        assertEquals(60_000L, p.handoverEndB)
        // B does not start at 0:00: it starts where the pre-roll needs it.
        assertEquals(48_000L, p.secondaryStartPositionMs)
        assertTrue(p.fadeOutDurationA > p.fadeInDurationB)
    }

    @Test
    fun `the mix point in A depends on which B follows it`() {
        val a = song(200_000, seg(SegmentType.VERSE, 40_000, 80_000))
        val laterChorus = song(200_000, seg(SegmentType.CHORUS, 60_000, 90_000))
        val earlyChorus = song(200_000, seg(SegmentType.CHORUS, 10_000, 40_000))

        val withLate = plan(a, laterChorus)
        val withEarly = plan(a, earlyChorus)

        // A early chorus leaves less room to pre-roll, so A has to fade for less time and starts later.
        assertEquals(12_000L, withLate.fadeOutDurationA)
        assertEquals(10_000L, withEarly.fadeOutDurationA)
        assertNotEquals(withLate.mixOutPositionA, withEarly.mixOutPositionA)
        assertNotEquals(withLate.mixInPositionB, withEarly.mixInPositionB)
    }

    @Test
    fun `chooses the pair with the best fit among several candidates`() {
        val a =
            song(
                200_000,
                seg(SegmentType.VERSE, 20_000, 60_000),
                seg(SegmentType.DROP, 100_000, 130_000),
                seg(SegmentType.OUTRO, 170_000, 200_000),
            )
        val b =
            song(
                200_000,
                seg(SegmentType.INTRO, 0, 30_000),
                seg(SegmentType.CHORUS, 90_000, 120_000),
            )

        val p = plan(a, b)

        // Any chosen plan must follow the energy rule and stay inside both songs.
        assertTrue(p.handoverEndA <= 200_000L)
        assertTrue(p.secondaryStartPositionMs >= 0)
        assertTrue(p.handoverEndB <= 200_000L)
    }

    @Test
    fun `respects the earliest allowed mix position`() {
        val a =
            song(
                200_000,
                seg(SegmentType.VERSE, 0, 40_000),
                seg(SegmentType.DROP, 100_000, 130_000),
            )
        val b = song(200_000, seg(SegmentType.INTRO, 0, 30_000), seg(SegmentType.CHORUS, 60_000, 90_000))

        val p = plan(a, b, earliest = 50_000)

        assertTrue(p.startA >= 50_000L)
    }

    @Test
    fun `no pair that follows the energy rule gives no plan`() {
        val a = song(200_000, seg(SegmentType.DROP, 100_000, 130_000))
        val b = song(200_000, seg(SegmentType.CHORUS, 60_000, 90_000))

        assertNull(SmartTransitionCalculator.calculateTransitionPlan(a, b))
        assertNull(SmartTransitionCalculator.calculateTransitionPlan(song(200_000), b))
    }

    @Test
    fun `a chorus that comes too early to pre-roll gives no plan`() {
        val a = song(200_000, seg(SegmentType.VERSE, 40_000, 80_000))
        val b = song(200_000, seg(SegmentType.CHORUS, 2_000, 30_000))

        assertNull(SmartTransitionCalculator.calculateTransitionPlan(a, b))
    }

    @Test
    fun `with a beat grid the handover lands on a bar and the quick fade is one beat`() {
        val grid = BeatGrid(downbeatMs = 0.0, beatMs = 500.0)
        val a = song(200_000, seg(SegmentType.VERSE, 40_300, 79_700), bpm = 120f, grid = grid)
        val b = song(200_000, seg(SegmentType.CHORUS, 60_000, 90_000), bpm = 120f, grid = grid)

        val p = plan(a, b)

        assertEquals(0L, p.handoverEndA % 2_000L)
        assertTrue(p.handoverEndA <= 79_700L)
        assertEquals(500L, p.fadeInDurationB)
        assertEquals(0L, p.fadeOutDurationA % 2_000L)
        assertTrue(p.tempoMatched)
        assertNotNull(p.gridA)
    }

    @Test
    fun `tempo factor stretches B toward A within eight percent and folds half or double tempo`() {
        val grid = BeatGrid(0.0, 500.0)
        val a = song(200_000, seg(SegmentType.VERSE, 40_000, 80_000), bpm = 120f, grid = grid)
        val closeB = song(200_000, seg(SegmentType.CHORUS, 60_000, 90_000), bpm = 124f, grid = grid)
        val farB = song(200_000, seg(SegmentType.CHORUS, 60_000, 90_000), bpm = 150f, grid = grid)
        val halfB = song(200_000, seg(SegmentType.CHORUS, 60_000, 90_000), bpm = 60f, grid = grid)

        val close = plan(a, closeB)
        assertTrue(close.tempoMatched)
        assertEquals(120f / 124f, close.speedFactorB, 1e-4f)

        val far = plan(a, farB)
        assertEquals(1f, far.speedFactorB, 0f)
        assertTrue(!far.tempoMatched)

        val half = plan(a, halfB)
        assertEquals(1f, half.speedFactorB, 1e-4f)
        // Folded tempos do not share a beat, so phase tracking is off.
        assertNull(half.gridA)
    }

    @Test
    fun `quick cut shortens A's fade to one beat on a drop and tails A with a low-pass`() {
        val grid = BeatGrid(0.0, 500.0)
        val a = song(200_000, seg(SegmentType.DROP, 100_000, 130_000), bpm = 120f, grid = grid)
        val b = song(200_000, seg(SegmentType.INTRO, 0, 20_000), seg(SegmentType.VERSE, 20_000, 60_000), bpm = 120f, grid = grid)

        val normal = SmartTransitionCalculator.calculateTransitionPlan(a, b)!!
        val quick = SmartTransitionCalculator.calculateTransitionPlan(a, b, quickCut = true)!!

        assertEquals(500L, quick.fadeOutDurationA)
        assertTrue(normal.fadeOutDurationA > quick.fadeOutDurationA)
        assertTrue(quick.tailFxA)
    }

    @Test
    fun `a transition whose overlap sits on sung lines costs more than one in the pauses`() {
        val a = song(200_000, seg(SegmentType.VERSE, 40_000, 80_000))
        val b = song(200_000, seg(SegmentType.CHORUS, 60_000, 90_000))
        val sungEverywhere = song(200_000, seg(SegmentType.VERSE, 40_000, 80_000), vocal = listOf(0L..200_000L))
        val pausesInA = song(200_000, seg(SegmentType.VERSE, 40_000, 80_000), vocal = listOf(0L..30_000L, 100_000L..200_000L))

        val sung = SmartTransitionCalculator.calculateTransitionPlan(sungEverywhere, b)!!
        val paused = SmartTransitionCalculator.calculateTransitionPlan(pausesInA, b)!!

        assertTrue(paused.cost < sung.cost)
    }

    @Test
    fun `B may still start at zero when its intro is the only calm part`() {
        val a = song(200_000, seg(SegmentType.DROP, 100_000, 130_000))
        val b = song(200_000, seg(SegmentType.INTRO, 0, 30_000), seg(SegmentType.CHORUS, 30_000, 90_000))

        assertEquals(0L, plan(a, b).mixInPositionB)
    }

    @Test
    fun `fade lengths are whole bars chosen from 16 8 6 4 2`() {
        val grid = BeatGrid(0.0, 500.0) // 2 s bars
        val a = song(400_000, seg(SegmentType.VERSE, 40_000, 200_000), bpm = 120f, grid = grid)
        val b = song(400_000, seg(SegmentType.CHORUS, 150_000, 250_000), bpm = 120f, grid = grid)

        val p = SmartTransitionCalculator.calculateTransitionPlan(a, b)!!
        assertEquals(32_000L, p.fadeOutDurationA) // 16 bars of 2 s
        val tight = SmartTransitionCalculator.calculateTransitionPlan(a, b, longFadeMs = 13_000L)!!
        assertEquals(12_000L, tight.fadeOutDurationA) // 6 bars
    }
}
