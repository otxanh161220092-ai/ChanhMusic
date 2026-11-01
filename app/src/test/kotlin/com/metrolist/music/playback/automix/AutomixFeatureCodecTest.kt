/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sin

class AutomixFeatureCodecTest {
    private val onset = FloatArray(4_000) { i -> (abs(sin(i * 0.3)) * 2.0).toFloat() }
    private val lowOnset = FloatArray(4_000) { i -> (abs(sin(i * 0.11)) * 1.5).toFloat() }
    private val loudness = FloatArray(600) { i -> -20f - (i % 7) }

    private val features =
        TrackFeatures(
            durationMs = 240_000L,
            loudnessDb = loudness,
            bpm = 120f,
            bpmConfidence = 2.5f,
            onset = onset,
            lowOnset = lowOnset,
            periodHops = 100.0,
            hopMs = 5.0,
        )

    @Test
    fun `round trip keeps tempo, loudness and segments`() {
        val segments = listOf(SongSegment(SegmentType.DROP, 40_000, 60_000))
        val bytes = AutomixFeatureCodec.write(CachedAnalysis(features, segments))
        val restored = AutomixFeatureCodec.read(bytes)!!

        assertEquals(240_000L, restored.features.durationMs)
        assertEquals(120f, restored.features.bpm, 0f)
        assertEquals(2.5f, restored.features.bpmConfidence, 0f)
        assertEquals(loudness.toList(), restored.features.loudnessDb.toList())
        assertEquals(segments, restored.segments)
    }

    @Test
    fun `onset is stored at coarser hop with matching timing`() {
        val restored = AutomixFeatureCodec.read(AutomixFeatureCodec.write(CachedAnalysis(features, emptyList())))!!

        assertEquals(20.0, restored.features.hopMs, 1e-9)
        assertEquals(25.0, restored.features.periodHops, 1e-9)
        assertEquals(onset.size / 4, restored.features.onset.size)
        val peak = onset.maxOrNull()!!
        restored.features.onset.forEachIndexed { i, v ->
            val expected = (0 until 4).map { onset[i * 4 + it] }.average().toFloat()
            assertTrue("sample $i: $v vs $expected", abs(v - expected) <= peak / 16000f + 1e-6f)
        }
    }

    @Test
    fun `corrupt or other-version data is treated as a cache miss`() {
        assertNull(AutomixFeatureCodec.read(byteArrayOf(1, 2, 3)))
        val bytes = AutomixFeatureCodec.write(CachedAnalysis(features, emptyList()))
        bytes[3] = (AutomixFeatureCodec.ANALYSIS_VERSION + 1).toByte()
        assertNull(AutomixFeatureCodec.read(bytes))
    }
}
