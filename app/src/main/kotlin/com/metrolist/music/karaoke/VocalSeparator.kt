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
 * Splits a stereo track into a vocal stem and an instrumental stem with open-unmix's "vocals"
 * model (MIT weights). The model estimates the vocal magnitude of each time-frequency bin; the
 * mix's complex value is divided between the two stems by that estimate, and an inverse STFT
 * with overlap-add turns each stem back into audio.
 *
 * The front end matches the model's training: 4096-point FFT, hop 1024, periodic Hann window,
 * reflect padding centred on each frame (torch.stft with center=True), at 44100 Hz.
 *
 * Input must be planar stereo float PCM at [SAMPLE_RATE]; the caller resamples. The output is
 * streamed in order, so only a few chunks of spectra are held at once. The work is CPU-heavy,
 * so run it on a background thread.
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

    /** Receives [count] samples per channel of each stem, in order, from the start of the track. */
    fun interface Sink {
        fun write(
            instrumentalLeft: FloatArray,
            instrumentalRight: FloatArray,
            vocalLeft: FloatArray,
            vocalRight: FloatArray,
            count: Int,
        )
    }

    /**
     * Streams the instrumental and vocal stems of [left] and [right] through [sink], in blocks of
     * up to [BLOCK] samples. Both stems have the same length as the input. [onProgress] gets a
     * value from 0 to 1 after each chunk.
     */
    fun separate(left: ShortArray, right: ShortArray, onProgress: (Float) -> Unit = {}, sink: Sink) {
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

        // Overlap-add buffers. Index r of each holds padded sample index `base` + r; everything
        // before the frames still to come is final and gets emitted.
        val bufLen = (step + 4) * HOP
        val instBuf = Array(CHANNELS) { FloatArray(bufLen) }
        val vocBuf = Array(CHANNELS) { FloatArray(bufLen) }
        val instOut = Array(CHANNELS) { FloatArray(BLOCK) }
        val vocOut = Array(CHANNELS) { FloatArray(BLOCK) }
        var outCount = 0
        var base = 0

        fun emit(from: Int, end: Int) {
            for (r in from until end) {
                val p = base + r
                if (p < pad || p >= pad + length) continue
                val env = envelopeAt(p, frames)
                for (c in 0 until CHANNELS) {
                    instOut[c][outCount] = if (env > 1e-8f) instBuf[c][r] / env else 0f
                    vocOut[c][outCount] = if (env > 1e-8f) vocBuf[c][r] / env else 0f
                }
                outCount++
                if (outCount == BLOCK) {
                    sink.write(instOut[0], instOut[1], vocOut[0], vocOut[1], outCount)
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
                    // Vocal share of each bin, from the model; the instrumental keeps the rest.
                    for (b in 0 until BINS) {
                        val i = t * BINS + b
                        val mask = maskOf(re[i], im[i], vocal[(c * BINS + b) * T + t])
                        setBin(re[i] * (1f - mask), im[i] * (1f - mask), b)
                    }
                    inverseFrame(maskRe, maskIm, instBuf[c], g * HOP - base)
                    for (b in 0 until BINS) {
                        val i = t * BINS + b
                        val mask = maskOf(re[i], im[i], vocal[(c * BINS + b) * T + t])
                        setBin(re[i] * mask, im[i] * mask, b)
                    }
                    inverseFrame(maskRe, maskIm, vocBuf[c], g * HOP - base)
                }
            }

            // Frames up to lastOwned are all added now, so these samples cannot change again.
            val finalCount = (lastOwned + 1) * HOP - base
            emit(0, finalCount)
            for (c in 0 until CHANNELS) {
                for (buf in arrayOf(instBuf[c], vocBuf[c])) {
                    System.arraycopy(buf, finalCount, buf, 0, bufLen - finalCount)
                    buf.fill(0f, bufLen - finalCount, bufLen)
                }
            }
            base += finalCount
            onProgress(min(1f, (lastOwned + 1).toFloat() / frames))
            chunk++
        }
        emit(0, min(bufLen, paddedLength - base))
        if (outCount > 0) sink.write(instOut[0], instOut[1], vocOut[0], vocOut[1], outCount)
        onProgress(1f)
    }

    /** The vocal share of one bin: the model's estimate over the bin's magnitude, clamped to 0..1. */
    private fun maskOf(re: Float, im: Float, estimate: Float): Float {
        val mag = sqrt(re * re + im * im)
        return if (mag > 1e-9f) (estimate / mag).coerceIn(0f, 1f) else 0f
    }

    /** Fills bin [b] of the frame buffers, mirroring it for the real-signal inverse FFT. */
    private fun setBin(re: Float, im: Float, b: Int) {
        maskRe[b] = re.toDouble()
        maskIm[b] = im.toDouble()
        if (b in 1 until FFT / 2) {
            maskRe[FFT - b] = maskRe[b]
            maskIm[FFT - b] = -maskIm[b]
        }
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
        x: ShortArray,
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
    private fun reflected(x: ShortArray, length: Int, index: Int): Double {
        var i = index
        if (i < 0) i = -i
        if (i >= length) i = 2 * (length - 1) - i
        return x[i] / 32768.0
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
