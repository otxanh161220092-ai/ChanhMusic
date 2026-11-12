/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.min
import kotlin.math.sin

/** Sample-level helpers: beat grid in samples, downbeat locking, 16-bit stereo mixing with bass swap. */
object SampleMixDsp {
    /** SamplesPerBeat = 60 / BPM * SampleRate */
    fun samplesPerBeat(bpm: Double, sampleRate: Int): Double = if (bpm <= 0.0) 0.0 else 60.0 / bpm * sampleRate

    /**
     * Snaps [targetSample] to the nearest beat-grid sample; when [beatsPerBar] > 0 it snaps to a
     * downbeat (bar line). [gridOriginSample] is the sample index of the first downbeat.
     */
    fun transitionSampleIndex(
        targetSample: Long,
        gridOriginSample: Long,
        bpm: Double,
        sampleRate: Int,
        beatsPerBar: Int = 4,
    ): Long {
        val spb = samplesPerBeat(bpm, sampleRate)
        if (spb <= 0.0) return targetSample
        val step = spb * beatsPerBar.coerceAtLeast(1)
        val n = ((targetSample - gridOriginSample) / step).let { Math.rint(it) }
        return gridOriginSample + (n * step).roundToLong()
    }

    /**
     * Equal-power mix of interleaved 16-bit little-endian stereo. Writes N frames into [out].
     * theta = i/N * pi/2; mixed = A*cos + B*sin. Bass swap: B's lows are removed during the first
     * half (returning to full by mid-point), A's lows removed during the second half.
     */
    class Mixer(
        private val sampleRate: Int,
        private val crossoverHz: Double = 200.0,
    ) {
        private val alpha = 1.0 - exp(-2.0 * PI * crossoverHz / sampleRate)
        private val lpA = DoubleArray(2)
        private val lpB = DoubleArray(2)
        // Vocal band: formants of sung/spoken vowels sit roughly between 300 Hz and 3.4 kHz.
        private val alphaVocalLo = 1.0 - exp(-2.0 * PI * VOCAL_LOW_HZ / sampleRate)
        private val alphaVocalHi = 1.0 - exp(-2.0 * PI * VOCAL_HIGH_HZ / sampleRate)
        private val vLoA = DoubleArray(2)
        private val vHiA = DoubleArray(2)
        private val vLoB = DoubleArray(2)
        private val vHiB = DoubleArray(2)

        fun reset() {
            lpA.fill(0.0); lpB.fill(0.0)
            vLoA.fill(0.0); vHiA.fill(0.0); vLoB.fill(0.0); vHiB.fill(0.0)
        }

        /**
         * @param frameOffset index of the first frame in the transition; [totalFrames] is N.
         * @param bassSwap apply the low-band exchange.
         */
        fun mix(
            a: ByteArray, aOff: Int,
            b: ByteArray, bOff: Int,
            out: ByteArray, outOff: Int,
            frames: Int,
            frameOffset: Int,
            totalFrames: Int,
            bassSwap: Boolean = true,
            /** 1 = A keeps the vocal band and B's is ducked; -1 = the reverse; 0 = no vocal yield. */
            vocalPriority: Int = 0,
        ) {
            val n = totalFrames.coerceAtLeast(1)
            for (f in 0 until frames) {
                val p = ((frameOffset + f).toDouble() / n).coerceIn(0.0, 1.0)
                val theta = p * PI / 2
                val gA = cos(theta)
                // B comes in early (full level by 70% of the overlap) so the blend never dips in the middle.
                val gB = sin(min(1.0, p / 0.7) * PI / 2)
                // Low-band weights: B bass in during second half, A bass out during second half.
                val bassB = if (bassSwap) ((p - 0.5) * 2).coerceIn(0.0, 1.0) else 1.0
                val bassA = if (bassSwap) (1.0 - ((p - 0.5) * 2).coerceIn(0.0, 1.0)) else 1.0
                for (c in 0..1) {
                    val i = (f * 2 + c) * 2
                    val sa = rd(a, aOff + i)
                    val sb = rd(b, bOff + i)
                    var xa = sa
                    var xb = sb
                    if (bassSwap) {
                        lpA[c] += alpha * (sa - lpA[c])
                        lpB[c] += alpha * (sb - lpB[c])
                        xa = (sa - lpA[c]) + lpA[c] * bassA
                        xb = (sb - lpB[c]) + lpB[c] * bassB
                    }
                    if (vocalPriority != 0) {
                        vLoA[c] += alphaVocalLo * (xa - vLoA[c]); vHiA[c] += alphaVocalHi * (xa - vHiA[c])
                        vLoB[c] += alphaVocalLo * (xb - vLoB[c]); vHiB[c] += alphaVocalHi * (xb - vHiB[c])
                        val bandA = vHiA[c] - vLoA[c]
                        val bandB = vHiB[c] - vLoB[c]
                        // The side that is not singing gives up the vocal band, fading in over the transition.
                        val duck = VOCAL_DUCK_DEPTH * ((p - 0.5) * 2).coerceIn(0.0, 1.0)
                        if (vocalPriority > 0) xb -= bandB * duck else xa -= bandA * duck
                    }
                    val m = xa * gA + xb * gB
                    wr(out, outOff + i, m)
                }
            }
        }

        private fun rd(buf: ByteArray, i: Int): Double =
            ((buf[i].toInt() and 0xFF) or (buf[i + 1].toInt() shl 8)).toShort().toDouble()

        private fun wr(buf: ByteArray, i: Int, v: Double) {
            val s = v.roundToLong().coerceIn(-32768L, 32767L).toInt()
            buf[i] = (s and 0xFF).toByte()
            buf[i + 1] = ((s shr 8) and 0xFF).toByte()
        }
    }

    const val VOCAL_LOW_HZ = 300.0
    const val VOCAL_HIGH_HZ = 3400.0
    const val VOCAL_DUCK_DEPTH = 0.8

    /**
     * Linear-interpolation resampler for drift correction (ratio = srcRate/dstRate, near 1.0).
     * Interleaved stereo 16-bit LE. Returns frames written.
     */
    fun resampleLinear(
        src: ByteArray,
        srcFrames: Int,
        ratio: Double,
        dst: ByteArray,
        maxDstFrames: Int,
    ): Int {
        var written = 0
        var pos = 0.0
        while (written < maxDstFrames) {
            val i0 = floor(pos).toInt()
            if (i0 + 1 >= srcFrames) break
            val fr = pos - i0
            for (c in 0..1) {
                val a = s16(src, (i0 * 2 + c) * 2)
                val b = s16(src, ((i0 + 1) * 2 + c) * 2)
                val v = (a + (b - a) * fr).roundToLong().coerceIn(-32768L, 32767L).toInt()
                val o = (written * 2 + c) * 2
                dst[o] = (v and 0xFF).toByte()
                dst[o + 1] = ((v shr 8) and 0xFF).toByte()
            }
            written++
            pos += ratio
        }
        return written
    }

    private fun s16(b: ByteArray, i: Int): Double =
        ((b[i].toInt() and 0xFF) or (b[i + 1].toInt() shl 8)).toShort().toDouble()
}
