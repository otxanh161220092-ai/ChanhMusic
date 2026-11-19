/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Removes the lead vocal from a stereo track, leaving the instrumental, with open-unmix's
 * "vocals" model (MIT weights). The model estimates the vocal magnitude of each time-frequency
 * bin; the instrumental keeps the rest of the mix's complex value, and an inverse STFT with
 * overlap-add turns it back into audio.
 *
 * The front end matches the model's training: 4096-point FFT, hop 1024, periodic Hann window,
 * reflect padding centred on each frame (torch.stft with center=True), at 44100 Hz.
 *
 * Input must be planar stereo float PCM at [SAMPLE_RATE]; the caller resamples. The output is
 * streamed in order, so only a few chunks of spectra are held at once. The work is CPU-heavy,
 * so run it on a background thread. It is not used by playback yet.
 */
class VocalSeparator(modelPath: String) : AutoCloseable {

    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment()
    private val session: OrtSession = environment.createSession(modelPath, OrtSession.SessionOptions())
    private val fft = Fft(FFT)
    private val window = DoubleArray(FFT) { 0.5 - 0.5 * cos(2.0 * PI * it / FFT) }
    private val frameRe = DoubleArray(FFT)
    private val frameIm = DoubleArray(FFT)
    private val maskRe = DoubleArray(FFT)
    private val maskIm = DoubleArray(FFT)

    /**
     * Writes the instrumental of [left] and [right] through [sink], in order, as blocks of
     * up to [BLOCK] samples per channel. The output has the same length as the input.
     */
    fun separate(left: FloatArray, right: FloatArray, sink: (FloatArray, FloatArray, Int) -> Unit) {
        require(left.size == right.size) { "channels differ in length" }
        val length = left.size
        if (length == 0) return
        val pad = FFT / 2
        val paddedLength = length + 2 * pad
        val frames = (paddedLength - FFT) / HOP + 1

        // Each chunk feeds the model a window of T frames; only the middle STEP frames are kept,
        // so the edges, where the model sees the cut, never reach the output.
        val step = T - 2 * MARGIN
        val spRe = Array(CHANNELS) { FloatArray(T * BINS) }
        val spIm = Array(CHANNELS) { FloatArray(T * BINS) }
        val mix = FloatArray(CHANNELS * BINS * T)

        // Overlap-add buffer. buf[0] holds padded sample index `base`; everything before the
        // frames still to come is final and gets emitted.
        val bufLen = (step + 4) * HOP
        val buf = Array(CHANNELS) { FloatArray(bufLen) }
        val outL = FloatArray(BLOCK)
        val outR = FloatArray(BLOCK)
        var outCount = 0
        var base = 0

        fun emit(from: Int, end: Int) {
            for (r in from until end) {
                val p = base + r
                if (p < pad || p >= pad + length) continue
                val env = envelopeAt(p, frames)
                if (env > 1e-8f) {
                    outL[outCount] = buf[0][r] / env
                    outR[outCount] = buf[1][r] / env
                } else {
                    outL[outCount] = 0f
                    outR[outCount] = 0f
                }
                outCount++
                if (outCount == BLOCK) {
                    sink(outL, outR, outCount)
                    outCount = 0
                }
            }
        }

        var chunk = 0
        while (chunk * step < frames) {
            val firstOwned = chunk * step
            val start = firstOwned - MARGIN
            for (c in 0 until CHANNELS) {
                val x = if (c == 0) left else right
                for (t in 0 until T) {
                    frameSpectrum(x, pad, length, start + t, frames, spRe[c], spIm[c], t * BINS)
                }
            }
            for (c in 0 until CHANNELS) for (t in 0 until T) for (b in 0 until BINS) {
                val i = t * BINS + b
                mix[(c * BINS + b) * T + t] = sqrt(spRe[c][i] * spRe[c][i] + spIm[c][i] * spIm[c][i])
            }
            val vocal = estimateVocal(mix)

            val lastOwned = min(firstOwned + step, frames) - 1
            for (g in firstOwned..lastOwned) {
                val t = g - start
                for (c in 0 until CHANNELS) {
                    val re = spRe[c]
                    val im = spIm[c]
                    for (b in 0 until BINS) {
                        val i = t * BINS + b
                        val mag = sqrt(re[i] * re[i] + im[i] * im[i])
                        val estimate = vocal[(c * BINS + b) * T + t]
                        val mask = if (mag > 1e-9f) (estimate / mag).coerceIn(0f, 1f) else 0f
                        // Instrumental keeps what is not vocal: the complex mix times (1 - mask).
                        val keep = 1f - mask
                        maskRe[b] = (re[i] * keep).toDouble()
                        maskIm[b] = (im[i] * keep).toDouble()
                        if (b in 1 until FFT / 2) {
                            maskRe[FFT - b] = maskRe[b]
                            maskIm[FFT - b] = -maskIm[b]
                        }
                    }
                    inverseFrame(maskRe, maskIm, buf[c], g * HOP - base)
                }
            }

            // Frames up to lastOwned are all added now, so these samples cannot change again.
            val finalCount = (lastOwned + 1) * HOP - base
            emit(0, finalCount)
            for (c in 0 until CHANNELS) {
                System.arraycopy(buf[c], finalCount, buf[c], 0, bufLen - finalCount)
                buf[c].fill(0f, bufLen - finalCount, bufLen)
            }
            base += finalCount
            chunk++
        }
        emit(0, min(bufLen, paddedLength - base))
        if (outCount > 0) sink(outL, outR, outCount)
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

    /**
     * Writes the one-sided spectrum of the windowed frame [frame] into [re] and [im] at [offset],
     * or silence outside the signal.
     */
    private fun frameSpectrum(
        x: FloatArray,
        pad: Int,
        length: Int,
        frame: Int,
        frames: Int,
        re: FloatArray,
        im: FloatArray,
        offset: Int,
    ) {
        if (frame < 0 || frame >= frames) {
            re.fill(0f, offset, offset + BINS)
            im.fill(0f, offset, offset + BINS)
            return
        }
        for (i in 0 until FFT) {
            frameRe[i] = reflected(x, length, frame * HOP + i - pad) * window[i]
        }
        frameIm.fill(0.0)
        fft.forward(frameRe, frameIm)
        for (b in 0 until BINS) {
            re[offset + b] = frameRe[b].toFloat()
            im[offset + b] = frameIm[b].toFloat()
        }
    }

    /** Sample [index] of the signal, reflected at both ends as torch's reflect padding does. */
    private fun reflected(x: FloatArray, length: Int, index: Int): Double {
        var i = index
        if (i < 0) i = -i
        if (i >= length) i = 2 * (length - 1) - i
        return x[i].toDouble()
    }

    /** Sum of squared windows of all frames covering padded sample [p]: the overlap-add normaliser. */
    private fun envelopeAt(p: Int, frames: Int): Float {
        val from = if (p < FFT) 0 else (p - FFT) / HOP + 1
        val to = min(frames - 1, p / HOP)
        var sum = 0f
        for (g in from..to) {
            val w = window[p - g * HOP]
            sum += (w * w).toFloat()
        }
        return sum
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

        /** Samples per block handed to the sink. */
        const val BLOCK = 8192
    }
}
