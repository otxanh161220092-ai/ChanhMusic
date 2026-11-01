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

    private val songB =
        listOf(
            seg(SegmentType.INTRO, 0, 8_000),
            seg(SegmentType.VERSE, 8_000, 30_000),
            seg(SegmentType.CHORUS, 30_000, 50_000),
        )

    @Test
    fun `case 1 drop in the middle of A finishes exactly where the drop ends and B enters calm`() {
        val a =
            listOf(
                seg(SegmentType.INTRO, 0, 10_000),
                seg(SegmentType.DROP, 40_000, 60_000),
                seg(SegmentType.VERSE, 60_000, 120_000),
            )

        val t = SmartTransitionCalculator.calculate(a, 120_000, songB, earliestMixMs = 30_000)

        assertEquals(60_000L, t.mixOutPositionA + t.fadeDurationMs)
        assertEquals(16_000L, t.fadeDurationMs)
        assertEquals(44_000L, t.mixOutPositionA)
        assertEquals(0L, t.mixInPositionB)
    }

    @Test
    fun `case 2 calm part of A pre-rolls B so its chorus lands when the fade ends`() {
        val a = listOf(seg(SegmentType.VERSE, 10_000, 50_000))

        val t = SmartTransitionCalculator.calculate(a, 100_000, songB)

        assertEquals(50_000L, t.mixOutPositionA + t.fadeDurationMs)
        assertEquals(30_000L, t.mixInPositionB + t.fadeDurationMs)
        assertEquals(16_000L, t.fadeDurationMs)
    }

    @Test
    fun `case 2 shortens the fade when B chorus comes too soon`() {
        val b = listOf(seg(SegmentType.INTRO, 0, 5_000), seg(SegmentType.CHORUS, 5_000, 30_000))
        val a = listOf(seg(SegmentType.VERSE, 10_000, 50_000))

        val t = SmartTransitionCalculator.calculate(a, 100_000, b)

        assertEquals(0L, t.mixInPositionB)
        assertEquals(5_000L, t.fadeDurationMs)
        assertEquals(50_000L, t.mixOutPositionA + t.fadeDurationMs)
    }

    @Test
    fun `mix point respects earliest allowed position`() {
        val a =
            listOf(
                seg(SegmentType.VERSE, 0, 20_000),
                seg(SegmentType.DROP, 60_000, 80_000),
            )

        val t = SmartTransitionCalculator.calculate(a, 100_000, songB, earliestMixMs = 50_000)

        assertTrue(t.mixOutPositionA >= 50_000L)
        assertEquals(80_000L, t.mixOutPositionA + t.fadeDurationMs)
    }

    @Test
    fun `fade never runs past end of A`() {
        val a = listOf(seg(SegmentType.OUTRO, 0, 5_000))

        val t = SmartTransitionCalculator.calculate(a, 5_000, songB)

        assertTrue(t.mixOutPositionA + t.fadeDurationMs <= 5_000L)
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
