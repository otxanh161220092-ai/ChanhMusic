/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import kotlin.math.abs
import kotlin.math.roundToInt

/** A cached analysis: the signal features plus the structure marks, if the segmenter has run. */
class CachedAnalysis(
    val features: TrackFeatures,
    val segments: List<SongSegment>,
)

/**
 * Binary format of one cached analysis.
 *
 * Onset curves are stored at 4x coarser hops (20 ms instead of 5 ms) and quantized to 16 bit, so a
 * 4-minute song takes about 50 KB instead of about 400 KB. Beat-grid lookups use windows of several
 * seconds, so the coarser hop does not move the grid in any way that matters.
 */
object AutomixFeatureCodec {
    /** Bump when the analyzer changes meaningfully; older files are then ignored and re-analyzed. */
    const val ANALYSIS_VERSION = 1

    private const val DECIMATION = 4
    private const val SHORT_SCALE = 32767f

    fun write(analysis: CachedAnalysis): ByteArray {
        val f = analysis.features
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { out ->
            out.writeInt(ANALYSIS_VERSION)
            out.writeLong(f.durationMs)
            out.writeFloat(f.bpm)
            out.writeFloat(f.bpmConfidence)
            out.writeDouble(f.periodHops / DECIMATION)
            out.writeDouble(f.hopMs * DECIMATION)
            writeFloats(out, f.loudnessDb)
            writeQuantized(out, decimate(f.onset))
            writeQuantized(out, decimate(f.lowOnset))
            out.writeUTF(encodeSegments(analysis.segments))
        }
        return bytes.toByteArray()
    }

    /** Returns null when the data was written by a different analyzer version or is corrupt. */
    fun read(data: ByteArray): CachedAnalysis? {
        return try {
            DataInputStream(ByteArrayInputStream(data)).use { input ->
                if (input.readInt() != ANALYSIS_VERSION) return null
                val features =
                    TrackFeatures(
                        durationMs = input.readLong(),
                        bpm = input.readFloat(),
                        bpmConfidence = input.readFloat(),
                        periodHops = input.readDouble(),
                        hopMs = input.readDouble(),
                        loudnessDb = readFloats(input),
                        onset = readQuantized(input),
                        lowOnset = readQuantized(input),
                    )
                CachedAnalysis(features, decodeSegments(input.readUTF()))
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun encodeSegments(segments: List<SongSegment>): String =
        segments.joinToString(";") { "${it.type.name}:${it.startTimeMs}:${it.endTimeMs}" }

    private fun decodeSegments(text: String): List<SongSegment> =
        text
            .split(';')
            .filter { it.isNotBlank() }
            .mapNotNull { part ->
                val fields = part.split(':')
                if (fields.size != 3) return@mapNotNull null
                runCatching {
                    SongSegment(
                        type = SegmentType.valueOf(fields[0]),
                        startTimeMs = fields[1].toLong(),
                        endTimeMs = fields[2].toLong(),
                    )
                }.getOrNull()
            }

    private fun decimate(values: FloatArray): FloatArray {
        val n = values.size / DECIMATION
        return FloatArray(n) { i ->
            var sum = 0f
            for (k in 0 until DECIMATION) sum += values[i * DECIMATION + k]
            sum / DECIMATION
        }
    }

    private fun writeFloats(
        out: DataOutputStream,
        values: FloatArray,
    ) {
        out.writeInt(values.size)
        values.forEach { out.writeFloat(it) }
    }

    private fun readFloats(input: DataInputStream): FloatArray = FloatArray(input.readInt()) { input.readFloat() }

    /** Layout: sample count, 4-byte scale, then one signed 16-bit value per sample. */
    private fun writeQuantized(
        out: DataOutputStream,
        values: FloatArray,
    ) {
        val peak = values.maxOfOrNull { abs(it) } ?: 0f
        val scale = if (peak > 0f) peak else 1f
        out.writeInt(values.size)
        out.writeFloat(scale)
        for (v in values) {
            out.writeShort((v / scale * SHORT_SCALE).roundToInt().coerceIn(-32767, 32767))
        }
    }

    private fun readQuantized(input: DataInputStream): FloatArray {
        val size = input.readInt()
        val scale = input.readFloat()
        return FloatArray(size) { input.readShort() / SHORT_SCALE * scale }
    }
}
