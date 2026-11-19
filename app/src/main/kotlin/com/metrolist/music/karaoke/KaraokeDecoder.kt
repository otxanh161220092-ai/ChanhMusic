/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import java.nio.ByteOrder
import kotlin.math.roundToInt

/** Decodes an audio file to planar stereo float PCM at [VocalSeparator.SAMPLE_RATE]. */
object KaraokeDecoder {

    fun decode(context: Context, uri: Uri): Pair<FloatArray, FloatArray>? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            val track = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            } ?: return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val codec = MediaCodec.createDecoderByType(mime)
            try {
                codec.configure(format, null, null, 0)
                codec.start()
                return runDecoder(extractor, codec, format)
            } finally {
                runCatching { codec.stop() }
                codec.release()
            }
        } finally {
            extractor.release()
        }
    }

    private fun runDecoder(
        extractor: MediaExtractor,
        codec: MediaCodec,
        inputFormat: MediaFormat,
    ): Pair<FloatArray, FloatArray> {
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        var floatPcm = false
        val left = GrowableFloats()
        val right = GrowableFloats()
        val info = MediaCodec.BufferInfo()
        var inputDone = false
        var outputDone = false

        while (!outputDone) {
            if (!inputDone) {
                val inIndex = codec.dequeueInputBuffer(10_000)
                if (inIndex >= 0) {
                    val buffer = codec.getInputBuffer(inIndex)!!
                    val size = extractor.readSampleData(buffer, 0)
                    if (size < 0) {
                        codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputDone = true
                    } else {
                        codec.queueInputBuffer(inIndex, 0, size, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val outIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val f = codec.outputFormat
                    sampleRate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    channels = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm = f.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        f.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                }
                outIndex >= 0 -> {
                    val buffer = codec.getOutputBuffer(outIndex)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        appendFrames(buffer.slice().order(ByteOrder.LITTLE_ENDIAN), channels, floatPcm, left, right)
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
        }
        val l = left.toArray()
        val r = right.toArray()
        return if (sampleRate == VocalSeparator.SAMPLE_RATE) {
            l to r
        } else {
            resample(l, sampleRate) to resample(r, sampleRate)
        }
    }

    private fun appendFrames(
        bytes: java.nio.ByteBuffer,
        channels: Int,
        floatPcm: Boolean,
        left: GrowableFloats,
        right: GrowableFloats,
    ) {
        val count = if (floatPcm) bytes.remaining() / 4 else bytes.remaining() / 2
        val frames = count / channels
        for (f in 0 until frames) {
            val base = f * channels
            val l = sampleAt(bytes, base, floatPcm)
            val r = if (channels > 1) sampleAt(bytes, base + 1, floatPcm) else l
            left.add(l)
            right.add(r)
        }
    }

    private fun sampleAt(bytes: java.nio.ByteBuffer, index: Int, floatPcm: Boolean): Float =
        if (floatPcm) {
            bytes.getFloat(index * 4)
        } else {
            bytes.getShort(index * 2) / 32768f
        }

    /** Linear resampling; good enough for a test export, not for the playback path. */
    private fun resample(input: FloatArray, from: Int): FloatArray {
        if (input.isEmpty() || from <= 0) return input
        val ratio = VocalSeparator.SAMPLE_RATE.toDouble() / from
        val outLength = (input.size * ratio).roundToInt()
        return FloatArray(outLength) { i ->
            val pos = i / ratio
            val i0 = pos.toInt().coerceAtMost(input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (pos - i0).toFloat()
            input[i0] * (1 - frac) + input[i1] * frac
        }
    }

    private class GrowableFloats {
        private var data = FloatArray(1 shl 16)
        private var size = 0

        fun add(value: Float) {
            if (size == data.size) data = data.copyOf(data.size * 2)
            data[size++] = value
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
