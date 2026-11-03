package com.metrolist.music.playback.automix

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class SampleMixDspTest {
    private fun tone(freq: Double, frames: Int, sr: Int, amp: Double = 10000.0): ByteArray {
        val b = ByteArray(frames * 4)
        for (f in 0 until frames) {
            val v = (amp * sin(2 * PI * freq * f / sr)).toInt()
            for (c in 0..1) {
                val o = (f * 2 + c) * 2
                b[o] = (v and 0xFF).toByte(); b[o + 1] = ((v shr 8) and 0xFF).toByte()
            }
        }
        return b
    }

    private fun rms(b: ByteArray, from: Int, to: Int): Double {
        var s = 0.0
        for (f in from until to) {
            val v = ((b[f * 4].toInt() and 0xFF) or (b[f * 4 + 1].toInt() shl 8)).toShort().toDouble()
            s += v * v
        }
        return Math.sqrt(s / (to - from))
    }

    @Test fun samplesPerBeat() = assertEquals(22050.0, SampleMixDsp.samplesPerBeat(120.0, 44100), 1e-9)

    @Test fun snapsToDownbeat() {
        val spb = SampleMixDsp.samplesPerBeat(120.0, 44100) // 22050
        val idx = SampleMixDsp.transitionSampleIndex(500_000, 1000, 120.0, 44100, 4)
        val bars = (idx - 1000) / (spb * 4)
        assertTrue(abs(bars - Math.rint(bars)) < 1e-3)
        assertTrue(abs(idx - 500_000) <= spb * 2)
    }

    @Test fun equalPowerEndpointsAndConstantPower() {
        val sr = 44100; val n = 44100
        val a = tone(1000.0, n, sr); val b = tone(1300.0, n, sr)
        val out = ByteArray(n * 4)
        SampleMixDsp.Mixer(sr).mix(a, 0, b, 0, out, 0, n, 0, n, bassSwap = false)
        val start = rms(out, 0, 500); val end = rms(out, n - 500, n)
        assertTrue(abs(start - rms(a, 0, 500)) < 300)
        assertTrue(abs(end - rms(b, n - 500, n)) < 400)
    }

    @Test fun bassSwapRemovesLowBandOfBFirstHalfAndAAfter() {
        val sr = 44100; val n = 44100
        val low = tone(60.0, n, sr)
        val silence = ByteArray(n * 4)
        // B = bass only: first quarter should be quiet vs last quarter
        val o1 = ByteArray(n * 4)
        SampleMixDsp.Mixer(sr).mix(silence, 0, low, 0, o1, 0, n, 0, n)
        assertTrue(rms(o1, n / 8, n / 4) < rms(o1, n - n / 4, n - 100) * 0.6)
        // A = bass only: tail end quiet
        val o2 = ByteArray(n * 4)
        SampleMixDsp.Mixer(sr).mix(low, 0, silence, 0, o2, 0, n, 0, n)
        assertTrue(rms(o2, n - 4000, n - 100) < 1500)
    }

    @Test fun resamplePreservesDurationAndPitchRatio() {
        val sr = 44100; val n = 44100
        val src = tone(440.0, n, sr)
        val dst = ByteArray(n * 4 + 8)
        val w = SampleMixDsp.resampleLinear(src, n, 1.001, dst, n)
        assertTrue(abs(w - n / 1.001) < 3)
        assertTrue(rms(dst, 100, w - 100) > 6000)
    }
}
