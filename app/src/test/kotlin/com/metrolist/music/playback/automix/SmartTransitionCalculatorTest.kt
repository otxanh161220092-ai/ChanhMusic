/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTransitionCalculatorTest {
    private fun seg(
        type: SegmentType,
        start: Long,
        end: Long,
    ) = SongSegment(type, start, end)

    @Test
    fun `drop at end of A mixes into verse of B`() {
        val a =
            listOf(
                seg(SegmentType.INTRO, 0, 10_000),
                seg(SegmentType.VERSE, 10_000, 40_000),
                seg(SegmentType.DROP, 40_000, 60_000),
            )
        val b =
            listOf(
                seg(SegmentType.INTRO, 0, 8_000),
                seg(SegmentType.VERSE, 8_000, 30_000),
                seg(SegmentType.CHORUS, 30_000, 50_000),
            )

        val t = SmartTransitionCalculator.calculate(a, 60_000, b)

        assertEquals(40_000L, t.mixOutPositionA)
        assertEquals(8_000L, t.mixInPositionB)
        assertEquals(16_000L, t.fadeDurationMs)
    }

    @Test
    fun `verse at end of A mixes into chorus of B`() {
        val a =
            listOf(
                seg(SegmentType.INTRO, 0, 10_000),
                seg(SegmentType.VERSE, 10_000, 50_000),
            )
        val b =
            listOf(
                seg(SegmentType.INTRO, 0, 12_000),
                seg(SegmentType.CHORUS, 12_000, 40_000),
            )

        val t = SmartTransitionCalculator.calculate(a, 50_000, b)

        assertEquals(12_000L, t.mixInPositionB)
        assertEquals(50_000L - 8_000L, t.mixOutPositionA)
        assertEquals(8_000L, t.fadeDurationMs)
    }

    @Test
    fun `fade never runs past end of A`() {
        val a = listOf(seg(SegmentType.OUTRO, 0, 3_000))
        val t = SmartTransitionCalculator.calculate(a, 3_000, listOf(seg(SegmentType.DROP, 0, 5_000)))

        assertTrue(t.mixOutPositionA + t.fadeDurationMs <= 3_000L)
    }

    @Test
    fun `missing structure falls back to fixed crossfade at end`() {
        val t = SmartTransitionCalculator.calculate(emptyList(), 200_000, emptyList())

        assertEquals(200_000L - SmartTransitionCalculator.DEFAULT_FADE_MS, t.mixOutPositionA)
        assertEquals(0L, t.mixInPositionB)
        assertEquals(SmartTransitionCalculator.DEFAULT_FADE_MS, t.fadeDurationMs)
    }

    @Test
    fun `song segment rejects empty or negative ranges`() {
        assertThrows(IllegalArgumentException::class.java) { seg(SegmentType.VERSE, 5_000, 5_000) }
        assertThrows(IllegalArgumentException::class.java) { seg(SegmentType.VERSE, -1, 5_000) }
    }
}
