/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeuristicSegmenterTest {
    @Test
    fun `layout follows song length and is contiguous`() {
        val segments = HeuristicSegmenter.segment(200_000)

        assertEquals(SegmentType.INTRO, segments.first().type)
        assertEquals(30_000L, segments.first().endTimeMs)
        assertEquals(SegmentType.OUTRO, segments.last().type)
        assertEquals(200_000L, segments.last().endTimeMs)
        val drop = segments.single { it.type == SegmentType.DROP }
        assertEquals(140_000L, drop.startTimeMs)
        assertEquals(170_000L, drop.endTimeMs)
        segments.zipWithNext().forEach { (x, y) -> assertEquals(x.endTimeMs, y.startTimeMs) }
    }

    @Test
    fun `louder middle blocks become choruses`() {
        // 200 s at a 100 ms hop. The four middle blocks cover indexes 300-575, 575-850, 850-1125 and 1125-1400.
        val loudness =
            FloatArray(2_000) { i ->
                when (i) {
                    in 300 until 575 -> -10f
                    in 575 until 850 -> -30f
                    in 850 until 1_125 -> -10f
                    in 1_125 until 1_400 -> -30f
                    else -> -20f
                }
            }

        val middle =
            HeuristicSegmenter
                .segment(200_000, loudness)
                .filter { it.type == SegmentType.VERSE || it.type == SegmentType.CHORUS }

        assertEquals(
            listOf(SegmentType.CHORUS, SegmentType.VERSE, SegmentType.CHORUS, SegmentType.VERSE),
            middle.map { it.type },
        )
    }

    @Test
    fun `measured loudness makes energies differ between two songs of equal length`() {
        val quiet = HeuristicSegmenter.segment(200_000, FloatArray(2_000) { i -> -40f + (i % 50) * 0.2f })
        val loud = HeuristicSegmenter.segment(200_000, FloatArray(2_000) { i -> if (i < 1_000) -40f else -5f })

        assertTrue(quiet.map { it.energy } != loud.map { it.energy })
        assertTrue(loud.all { it.energy in 0f..1f })
    }

    @Test
    fun `very short songs get no structure`() {
        assertTrue(HeuristicSegmenter.segment(10_000).isEmpty())
    }
}
