/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import timber.log.Timber
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Beat and downbeat times in milliseconds of track time. Downbeats are a subset of beats. */
class NeuralBeatGrid(
    val beatsMs: DoubleArray,
    val downbeatsMs: DoubleArray,
) {
    /** Median spacing between beats, or 0 when there are too few beats to tell. */
    val beatMs: Double
        get() {
            if (beatsMs.size < 2) return 0.0
            val gaps = DoubleArray(beatsMs.size - 1) { beatsMs[it + 1] - beatsMs[it] }
            gaps.sort()
            return gaps[gaps.size / 2]
        }

    /** The downbeat closest to [timeMs], or null when the track has none. */
    fun downbeatNear(timeMs: Double): Double? = downbeatsMs.minByOrNull { abs(it - timeMs) }
}

/**
 * Runs the Beat This! model (CPJKU, ISMIR 2024, MIT) through ONNX Runtime to find beats and
 * downbeats. Input is a log-mel spectrogram at 22,050 Hz with a 1024-point window and a hop of 441
 * samples, so the model sees 50 frames per second. Audio is processed in overlapping chunks so long
 * tracks never need one huge tensor.
 */
object BeatThisTracker {
    private const val TAG = "BeatThisTracker"
    const val MODEL_ASSET = "beat_this_final0_q8.onnx"

    const val SAMPLE_RATE = 22_050
    const val HOP = 441
    const val N_FFT = 1024
    const val N_MELS = 128
    private const val MEL_MIN_HZ = 30.0
    private const val MEL_MAX_HZ = 11_000.0
    private const val CHUNK_FRAMES = 1500
    private const val BORDER_FRAMES = 6
    private const val PEAK_HALF_WINDOW = 3
    private const val MIN_BEATS = 8

    private val lock = Any()
    private var session: OrtSession? = null

    private val window: FloatArray by lazy {
        FloatArray(N_FFT) { n -> (0.5 - 0.5 * cos(2.0 * PI * n / N_FFT)).toFloat() }
    }

    /** Slaney-scaled triangular mel filters, shape [N_MELS][N_FFT / 2 + 1]. */
    private val melFilters: Array<FloatArray> by lazy { buildMelFilters() }

    /**
     * Finds the beat grid of [pcm22050], mono float samples at [SAMPLE_RATE]. Returns null when the
     * model cannot be loaded or the track is too short to tell beats apart.
     */
    fun track(context: Context, pcm22050: FloatArray): NeuralBeatGrid? {
        val mel = logMel(pcm22050)
        val frames = mel.size / N_MELS
        if (frames < CHUNK_FRAMES / 4) return null

        val active = session(context) ?: return null
        val beatLogits = FloatArray(frames)
        val downbeatLogits = FloatArray(frames)
        if (!infer(active, mel, frames, beatLogits, downbeatLogits)) return null

        val beatFrames = peaks(beatLogits)
        if (beatFrames.size < MIN_BEATS) return null
        val beats = beatFrames.map { it * HOP.toDouble() * 1000.0 / SAMPLE_RATE }.toDoubleArray()

        // A downbeat is always a beat: snap each downbeat peak to its nearest beat so the bar grid is a
        // subset of the beat grid.
        val downbeats = peaks(downbeatLogits)
            .map { it * HOP.toDouble() * 1000.0 / SAMPLE_RATE }
            .map { t -> beats.minByOrNull { abs(it - t) } ?: t }
            .distinct()
            .sorted()
            .toDoubleArray()

        Timber.tag(TAG).d("beats=%d downbeats=%d frames=%d", beats.size, downbeats.size, frames)
        return NeuralBeatGrid(beats, downbeats)
    }

    /** Releases the ONNX session. Call when automix analysis is no longer needed. */
    fun release() {
        synchronized(lock) {
            runCatching { session?.close() }
            session = null
        }
    }

    private fun session(context: Context): OrtSession? {
        session?.let { return it }
        synchronized(lock) {
            session?.let { return it }
            return runCatching {
                val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
                val options = OrtSession.SessionOptions().apply {
                    setIntraOpNumThreads(2)
                    setCPUArenaAllocator(false)
                    setMemoryPatternOptimization(false)
                }
                OrtEnvironment.getEnvironment().createSession(bytes, options).also { session = it }
            }.onFailure { Timber.tag(TAG).w(it, "Beat model unavailable") }.getOrNull()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun infer(
        active: OrtSession,
        mel: FloatArray,
        frames: Int,
        beatLogits: FloatArray,
        downbeatLogits: FloatArray,
    ): Boolean = runCatching {
        val environment = OrtEnvironment.getEnvironment()
        val inputName = active.inputNames.first()
        val stride = CHUNK_FRAMES - 2 * BORDER_FRAMES

        var start = 0
        while (start < frames) {
            val length = min(CHUNK_FRAMES, frames - start)
            if (length <= 2 * BORDER_FRAMES && start > 0) break

            val chunk = mel.copyOfRange(start * N_MELS, (start + length) * N_MELS)
            val shape = longArrayOf(1, length.toLong(), N_MELS.toLong())
            OnnxTensor.createTensor(environment, FloatBuffer.wrap(chunk), shape).use { tensor ->
                active.run(mapOf(inputName to tensor)).use { outputs ->
                    val beat = (outputs.get(0).value as Array<FloatArray>)[0]
                    val downbeat = (outputs.get(1).value as Array<FloatArray>)[0]
                    val keepFrom = if (start == 0) 0 else BORDER_FRAMES
                    val keepTo = if (start + length >= frames) length else length - BORDER_FRAMES
                    for (index in keepFrom until keepTo) {
                        val target = start + index
                        if (target >= frames) break
                        beatLogits[target] = beat[index]
                        downbeatLogits[target] = downbeat[index]
                    }
                }
            }
            if (start + length >= frames) break
            start += stride
        }
        true
    }.onFailure { Timber.tag(TAG).w(it, "Beat inference failed") }.getOrDefault(false)

    /** Frames whose logit is positive and the largest of their neighbourhood, refined to sub-frame. */
    private fun peaks(logits: FloatArray): List<Double> {
        val found = ArrayList<Int>()
        for (i in logits.indices) {
            if (logits[i] <= 0f) continue
            var isMax = true
            for (k in max(0, i - PEAK_HALF_WINDOW)..min(logits.size - 1, i + PEAK_HALF_WINDOW)) {
                if (logits[k] > logits[i]) {
                    isMax = false
                    break
                }
            }
            if (isMax) found += i
        }
        // Adjacent frames that tie for a peak are one peak: keep their centre.
        val merged = ArrayList<Double>()
        var i = 0
        while (i < found.size) {
            var sum = found[i].toDouble()
            var count = 1
            while (i + 1 < found.size && found[i + 1] - found[i] <= 1) {
                i += 1
                sum += found[i]
                count += 1
            }
            merged += sum / count
            i += 1
        }
        return merged
    }

    /** Log-mel spectrogram, flattened as [frames * N_MELS], using centred frames like torchaudio. */
    private fun logMel(pcm: FloatArray): FloatArray {
        val padded = reflectPad(pcm, N_FFT / 2)
        val frames = 1 + (pcm.size / HOP)
        val out = FloatArray(frames * N_MELS)
        val re = DoubleArray(N_FFT)
        val im = DoubleArray(N_FFT)
        val bins = N_FFT / 2 + 1
        val magnitude = FloatArray(bins)
        for (f in 0 until frames) {
            val origin = f * HOP
            for (n in 0 until N_FFT) {
                re[n] = (padded.getOrElse(origin + n) { 0f } * window[n]).toDouble()
                im[n] = 0.0
            }
            fft(re, im)
            for (k in 0 until bins) magnitude[k] = sqrt(re[k] * re[k] + im[k] * im[k]).toFloat()
            for (m in 0 until N_MELS) {
                val filter = melFilters[m]
                var energy = 0.0
                for (k in 0 until bins) energy += filter[k] * magnitude[k]
                out[f * N_MELS + m] = ln(1.0 + 1000.0 * energy).toFloat()
            }
        }
        return out
    }

    private fun reflectPad(signal: FloatArray, pad: Int): FloatArray {
        val out = FloatArray(signal.size + 2 * pad)
        for (i in out.indices) {
            var j = i - pad
            if (j < 0) j = -j
            if (j >= signal.size) j = 2 * (signal.size - 1) - j
            out[i] = if (signal.isEmpty()) 0f else signal[j.coerceIn(0, signal.size - 1)]
        }
        return out
    }

    /** In-place iterative radix-2 FFT. Length must be a power of two. */
    private fun fft(re: DoubleArray, im: DoubleArray) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                re[i] = re[j].also { re[j] = re[i] }
                im[i] = im[j].also { im[j] = im[i] }
            }
        }
        var size = 2
        while (size <= n) {
            val angle = -2.0 * PI / size
            for (start in 0 until n step size) {
                for (k in 0 until size / 2) {
                    val wr = cos(angle * k)
                    val wi = sin(angle * k)
                    val a = start + k
                    val b = a + size / 2
                    val tr = re[b] * wr - im[b] * wi
                    val ti = re[b] * wi + im[b] * wr
                    re[b] = re[a] - tr
                    im[b] = im[a] - ti
                    re[a] += tr
                    im[a] += ti
                }
            }
            size = size shl 1
        }
    }

    private fun buildMelFilters(): Array<FloatArray> {
        fun hzToMel(hz: Double): Double =
            if (hz >= 1000.0) 15.0 + ln(hz / 1000.0) / (ln(6.4) / 27.0) else hz / (200.0 / 3.0)
        fun melToHz(mel: Double): Double =
            if (mel >= 15.0) 1000.0 * exp((ln(6.4) / 27.0) * (mel - 15.0)) else mel * (200.0 / 3.0)

        val bins = N_FFT / 2 + 1
        val melLo = hzToMel(MEL_MIN_HZ)
        val melHi = hzToMel(MEL_MAX_HZ)
        val edges = DoubleArray(N_MELS + 2) { melToHz(melLo + (melHi - melLo) * it / (N_MELS + 1)) }
        return Array(N_MELS) { m ->
            val left = edges[m]
            val centre = edges[m + 1]
            val right = edges[m + 2]
            val norm = 2.0 / (right - left)
            FloatArray(bins) { k ->
                val hz = k * SAMPLE_RATE.toDouble() / N_FFT
                val rising = (hz - left) / (centre - left)
                val falling = (right - hz) / (right - centre)
                (max(0.0, min(rising, falling)) * norm).toFloat()
            }
        }
    }
}
