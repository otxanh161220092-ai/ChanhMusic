/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.PI

/**
 * Removes the lead vocal from a stereo track, leaving the instrumental, with open-unmix's
 * "vocals" model (MIT weights). The model estimates the vocal magnitude of each time-frequency
 * bin; the instrumental keeps the rest of the mix's complex value, and an inverse STFT with
 * overlap-add turns it back into audio.
 *
 * The front end matches the model's training: 4096-point FFT, hop 1024, periodic Hann window,
 * reflect padding centred on each frame (torch.stft with center=True), at 44100 Hz.
 *
 * Input must be planar stereo float PCM at [SAMPLE_RATE]; the caller resamples. The work is
 * CPU-heavy and allocates, so run it on a background thread. It is not used by playback yet.
 */
class VocalSeparator(modelPath: String) : AutoCloseable {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(modelPath, OrtSession.SessionOptions())
    private val fft = Fft(FFT)
    private val window = DoubleArray(FFT) { 0.5 - 0.5 * cos(2.0 * PI * it / FFT) }

    /** Returns the instrumental (vocals removed) as left and right channels of the same length. */
    fun instrumental(left: FloatArray, right: FloatArray): Pair<FloatArray, FloatArray> {
        require(left.size == right.size) { "channels differ in length" }
        val length = left.size
        if (length == 0) return left to right
        val pad = FFT / 2
        val paddedLength = length + 2 * pad
        val frames = (paddedLength - FFT) / HOP + 1

        val outLeft = FloatArray(paddedLength)
        val outRight = FloatArray(paddedLength)
        val envelope = FloatArray(paddedLength)

        // Each chunk feeds the model a window of T frames; only the middle STEP frames are kept,
        // so the edges, where the model sees the cut, never reach the output.
        val step = T - 2 * MARGIN
        var chunk = 0
        while (chunk * step < frames) {
            val start = chunk * step - MARGIN
            val spectra = arrayOf(
                Array(T) { t -> frameSpectrum(left, pad, length, start + t, frames) },
                Array(T) { t -> frameSpectrum(right, pad, length, start + t, frames) },
            )
            val mix = FloatArray(CHANNELS * BINS * T)
            for (c in 0 until CHANNELS) for (t in 0 until T) {
                val s = spectra[c][t]
                for (b in 0 until BINS) {
                    mix[(c * BINS + b) * T + t] = kotlin.math.sqrt(s.re[b] * s.re[b] + s.im[b] * s.im[b])
                }
            }
            val vocal = estimateVocal(mix)

            val firstOwned = chunk * step
            val lastOwned = min(firstOwned + step, frames) - 1
            for (g in firstOwned..lastOwned) {
                val t = g - start
                for (c in 0 until CHANNELS) {
                    val s = spectra[c][t]
                    val re = DoubleArray(FFT)
                    val im = DoubleArray(FFT)
                    for (b in 0 until BINS) {
                        val mag = kotlin.math.sqrt(s.re[b] * s.re[b] + s.im[b] * s.im[b])
                        val estimate = vocal[(c * BINS + b) * T + t]
                        val mask = if (mag > 1e-9f) (estimate / mag).coerceIn(0f, 1f) else 0f
                        // Instrumental keeps what is not vocal: the complex mix times (1 - mask).
                        val keep = 1f - mask
                        re[b] = (s.re[b] * keep).toDouble()
                        im[b] = (s.im[b] * keep).toDouble()
                        if (b in 1 until FFT / 2) {
                            re[FFT - b] = re[b]
                            im[FFT - b] = -im[b]
                        }
                    }
                    inverseFrame(re, im, if (c == 0) outLeft else outRight, g * HOP)
                }
                for (i in 0 until FFT) envelope[g * HOP + i] += (window[i] * window[i]).toFloat()
            }
            chunk++
        }

        for (p in 0 until paddedLength) {
            if (envelope[p] > 1e-8f) {
                outLeft[p] /= envelope[p]
                outRight[p] /= envelope[p]
            }
        }
        return outLeft.copyOfRange(pad, pad + length) to outRight.copyOfRange(pad, pad + length)
    }

    /** Runs the model on one [CHANNELS]x[BINS]x[T] magnitude tensor; returns the vocal magnitude in the same layout. */
    private fun estimateVocal(mix: FloatArray): FloatArray {
        val shape = longArrayOf(1, CHANNELS.toLong(), BINS.toLong(), T.toLong())
        OnnxTensor.createTensor(environment, FloatBuffer.wrap(mix), shape).use { tensor ->
            session.run(mapOf(session.inputNames.first() to tensor)).use { result ->
                val buffer = (result.get(0) as OnnxTensor).floatBuffer
                buffer.rewind()
                val out = FloatArray(buffer.remaining())
                buffer.get(out)
                check(out.size == CHANNELS * BINS * T) { "model output has ${out.size} values" }
                return out
            }
        }
    }

    /** One frame's one-sided spectrum, stored as Float to keep a whole chunk's worth small. */
    private class Spectrum(val re: FloatArray, val im: FloatArray)

    /** The one-sided spectrum of the windowed frame [frame], or silence outside the signal. */
    private fun frameSpectrum(x: FloatArray, pad: Int, length: Int, frame: Int, frames: Int): Spectrum {
        val re = FloatArray(BINS)
        val im = FloatArray(BINS)
        if (frame < 0 || frame >= frames) return Spectrum(re, im)
        val bufRe = DoubleArray(FFT)
        val bufIm = DoubleArray(FFT)
        for (i in 0 until FFT) {
            bufRe[i] = reflected(x, length, frame * HOP + i - pad) * window[i]
        }
        fft.forward(bufRe, bufIm)
        for (b in 0 until BINS) {
            re[b] = bufRe[b].toFloat()
            im[b] = bufIm[b].toFloat()
        }
        return Spectrum(re, im)
    }

    /** Sample [index] of the signal, reflected at both ends as torch's reflect padding does. */
    private fun reflected(x: FloatArray, length: Int, index: Int): Double {
        var i = index
        if (i < 0) i = -i
        if (i >= length) i = 2 * (length - 1) - i
        return x[i].toDouble()
    }

    /** Inverse FFT of one frame, windowed and added into [out] at [offset]. */
    private fun inverseFrame(re: DoubleArray, im: DoubleArray, out: FloatArray, offset: Int) {
        // ifft(x) = conj(fft(conj(x))) / N; only the real part is kept.
        for (i in 0 until FFT) im[i] = -im[i]
        fft.forward(re, im)
        val scale = 1.0 / FFT
        for (i in 0 until FFT) {
            out[offset + i] += (window[i] * re[i] * scale).toFloat()
        }
    }

    override fun close() {
        session.close()
    }

    companion object {
        const val SAMPLE_RATE = 44_100
        const val FFT = 4096
        const val HOP = 1024
        const val BINS = FFT / 2 + 1
        const val CHANNELS = 2

        /** The model's fixed input width in frames, about 22.8 s. */
        const val T = 960

        /** Frames at each edge of a chunk that are only context for the model, not kept. */
        const val MARGIN = 64
    }
}
