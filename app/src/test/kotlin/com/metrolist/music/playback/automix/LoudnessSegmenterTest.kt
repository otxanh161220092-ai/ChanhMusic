/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessSegmenterTest {
    /** Level in dB per 100 ms hop, built from (seconds, dB) pieces. */
    private fun curve(vararg pieces: Pair<Int, Float>): FloatArray =
        pieces.flatMap { (sec, db) -> List(sec * 10) { db } }.toFloatArray()

    @Test
    fun `boundaries follow the music, not the song length`() {
        // quiet 20 s, loud 40 s, quiet 60 s, loud 50 s, quiet 30 s  => 200 s
        val loudness = curve(20 to -30f, 40 to -8f, 60 to -24f, 50 to -8f, 30 to -32f)
        val segments = LoudnessSegmenter.segment(200_000, loudness)!!

        segments.zipWithNext().forEach { (a, b) -> assertEquals(a.endTimeMs, b.startTimeMs) }
        assertEquals(0L, segments.first().startTimeMs)
        assertEquals(200_000L, segments.last().endTimeMs)
        assertEquals(SegmentType.INTRO, segments.first().type)
        assertEquals(SegmentType.OUTRO, segments.last().type)
        val loud = segments.filter { it.isHighEnergy }
        assertEquals(2, loud.size)
        // Section edges sit near the real changes (60 s and 120 s ... 170 s), not at 15/70/85 percent.
        assertTrue(kotlin.math.abs(loud[0].startTimeMs - 20_000L) < 3_000L)
        assertTrue(kotlin.math.abs(loud[0].endTimeMs - 60_000L) < 3_000L)
        assertTrue(kotlin.math.abs(loud[1].startTimeMs - 120_000L) < 3_000L)
        assertTrue(kotlin.math.abs(loud[1].endTimeMs - 170_000L) < 3_000L)
    }

    @Test
    fun `different songs of equal length give different sections`() {
        val a = LoudnessSegmenter.segment(200_000, curve(20 to -30f, 40 to -8f, 140 to -24f))!!
        val b = LoudnessSegmenter.segment(200_000, curve(100 to -24f, 60 to -8f, 40 to -30f))!!
        assertTrue(a.map { it.startTimeMs } != b.map { it.startTimeMs })
    }

    @Test
    fun `flat or short curves are rejected`() {
        assertNull(LoudnessSegmenter.segment(200_000, FloatArray(2_000) { -20f }))
        assertNull(LoudnessSegmenter.segment(10_000, FloatArray(100) { -20f }))
        assertNotNull(LoudnessSegmenter.segment(200_000, curve(100 to -30f, 100 to -8f)))
    }
}
