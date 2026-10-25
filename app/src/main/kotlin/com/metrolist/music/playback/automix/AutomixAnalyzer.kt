/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaDataSource
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.Process
import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext

/**
 * Decodes a song once (through the same cached data source the player uses, so the bytes are only
 * downloaded once) and extracts tempo, beat grid and a loudness profile for Automix.
 */
@UnstableApi
class AutomixAnalyzer(
    private val dataSourceFactory: DataSource.Factory,
) {
    private val results =
        object : LinkedHashMap<String, TrackFeatures>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TrackFeatures>?): Boolean = size > MAX_CACHED_TRACKS
        }
    private val failed = HashSet<String>()
    private val mutex = Mutex()

    suspend fun analyze(mediaId: String): TrackFeatures? {
        synchronized(results) { results[mediaId] }?.let { return it }
        if (synchronized(failed) { mediaId in failed }) return null

        return mutex.withLock {
            val cached = synchronized(results) { results[mediaId] }
            if (cached != null) {
                cached
            } else {
                val job = coroutineContext[Job]
                val features =
                    withContext(Dispatchers.IO) {
                        Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND)
                        try {
                            decode(mediaId) { job?.isActive != false }
                        } catch (e: Exception) {
                            Timber.tag(TAG).w(e, "Analysis failed for %s", mediaId)
                            null
                        } finally {
                            Process.setThreadPriority(Process.THREAD_PRIORITY_DEFAULT)
                        }
                    }
                if (features != null) {
                    synchronized(results) { results[mediaId] = features }
                } else if (job?.isActive != false) {
                    synchronized(failed) { failed.add(mediaId) }
                }
                features
            }
        }
    }

    private fun decode(
        mediaId: String,
        isActive: () -> Boolean,
    ): TrackFeatures? {
        val source = StreamMediaDataSource(dataSourceFactory, mediaId)
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(source)
            val trackIndex =
                (0 until extractor.trackCount).firstOrNull {
                    extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
                } ?: return null
            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var floatPcm = false

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            var features: FeatureExtractor? = null
            var mono = FloatArray(0)
            var inputDone = false
            var outputDone = false
            val deadline = SystemClock.elapsedRealtime() + TIMEOUT_MS

            while (!outputDone) {
                if (!isActive() || SystemClock.elapsedRealtime() > deadline) return null

                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(DEQUEUE_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)
                        val size = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outFormat = codec.outputFormat
                    if (outFormat.containsKey(MediaFormat.KEY_SAMPLE_RATE)) sampleRate = outFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                    if (outFormat.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) channels = outFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                    floatPcm =
                        outFormat.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                        outFormat.getInteger(MediaFormat.KEY_PCM_ENCODING) == AudioFormat.ENCODING_PCM_FLOAT
                } else if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0 && channels > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        outputBuffer.order(ByteOrder.nativeOrder())

                        val frames: Int
                        if (floatPcm) {
                            val samples = outputBuffer.asFloatBuffer()
                            frames = samples.remaining() / channels
                            if (mono.size < frames) mono = FloatArray(frames)
                            for (f in 0 until frames) {
                                var sum = 0f
                                for (c in 0 until channels) sum += samples.get()
                                mono[f] = sum / channels
                            }
                        } else {
                            val samples = outputBuffer.asShortBuffer()
                            frames = samples.remaining() / channels
                            if (mono.size < frames) mono = FloatArray(frames)
                            for (f in 0 until frames) {
                                var sum = 0
                                for (c in 0 until channels) sum += samples.get()
                                mono[f] = sum / (32768f * channels)
                            }
                        }
                        if (features == null) features = FeatureExtractor(sampleRate)
                        features.feed(mono, frames)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                }
            }
            return features?.finish()
        } finally {
            try {
                codec?.stop()
            } catch (_: Exception) {
            }
            codec?.release()
            extractor.release()
            source.close()
        }
    }

    /** Lets MediaExtractor read a song through a Media3 data source (cache first, network when missing). */
    private class StreamMediaDataSource(
        private val factory: DataSource.Factory,
        private val mediaId: String,
    ) : MediaDataSource() {
        private var source: DataSource? = null
        private var position = -1L
        private var length = -1L

        private fun openAt(target: Long) {
            try {
                source?.close()
            } catch (_: Exception) {
            }
            source = null
            val opened = factory.createDataSource()
            val remaining =
                opened.open(
                    DataSpec
                        .Builder()
                        .setUri(mediaId)
                        .setKey(mediaId)
                        .setPosition(target)
                        .build(),
                )
            if (remaining != C.LENGTH_UNSET.toLong()) length = target + remaining
            source = opened
            position = target
        }

        @Synchronized
        override fun readAt(
            position: Long,
            buffer: ByteArray,
            offset: Int,
            size: Int,
        ): Int {
            if (size == 0) return 0
            if (source == null || position != this.position) openAt(position)
            val read = source?.read(buffer, offset, size) ?: return -1
            if (read == C.RESULT_END_OF_INPUT) return -1
            this.position += read
            return read
        }

        @Synchronized
        override fun getSize(): Long {
            if (length < 0) openAt(0)
            return length
        }

        @Synchronized
        override fun close() {
            try {
                source?.close()
            } catch (_: Exception) {
            }
            source = null
        }
    }

    private companion object {
        const val TAG = "AutomixAnalyzer"
        const val MAX_CACHED_TRACKS = 8
        const val TIMEOUT_MS = 90_000L
        const val DEQUEUE_TIMEOUT_US = 10_000L
    }
}
