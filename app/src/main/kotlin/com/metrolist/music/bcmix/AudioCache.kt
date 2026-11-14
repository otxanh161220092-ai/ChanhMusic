/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Adapter that gives the ported Automix analyzer the cache queries it needs,
 * backed by Metrolist's own player cache. BitChord's full AudioCache (its
 * source registry, downloads and origin bookkeeping) is not ported; the
 * analysis only ever reads the bytes already on disk for a track.
 */

package com.metrolist.music.bcmix

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.ContentMetadata
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object AudioCache {
    /** Bytes of a rendition the alignment step needs before it can correlate. */
    const val MIN_ALIGNMENT_PREFIX_BYTES = 800L * 1024

    /** How much of a track the analysis head reads ahead of playback. */
    private const val HEAD_PROBE_BYTES = 8L * 1024 * 1024

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val headInFlight = ConcurrentHashMap.newKeySet<String>()

    @Volatile private var playerCache: Cache? = null
    @Volatile private var upstream: DataSource.Factory? = null

    /**
     * Wires the adapter to Metrolist's player cache. [upstream] is the playback
     * data source chain, used to read a track's head on a cache miss.
     */
    fun init(
        cache: Cache,
        upstream: DataSource.Factory,
    ) {
        playerCache = cache
        this.upstream = upstream
    }

    data class Rendition(val key: String, val contentLength: Long, val cachedPrefix: Long) {
        val isComplete: Boolean get() = contentLength > 0 && cachedPrefix >= contentLength
    }

    /** Metrolist keys its cache by video id, so the key is the id itself. */
    fun cacheKeyOf(uri: Uri): String = uri.getQueryParameter("v") ?: uri.lastPathSegment.orEmpty()

    fun renditionsOf(uri: Uri): List<Rendition> {
        val cache = playerCache ?: return emptyList()
        return listOfNotNull(renditionFor(cache, cacheKeyOf(uri)))
    }

    private fun renditionFor(cache: Cache, key: String): Rendition? {
        if (key.isEmpty()) return null
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(key)).coerceAtLeast(0L)
        val probe = length.takeIf { it > 0 } ?: HEAD_PROBE_BYTES
        val prefix = cache.getCachedLength(key, 0, probe).coerceAtLeast(0L)
        return if (prefix > 0L) Rendition(key, length, prefix) else null
    }

    fun renditionDataSource(uri: Uri, rendition: Rendition): android.media.MediaDataSource {
        val cache = checkNotNull(playerCache) { "AudioCache is not initialised" }
        return CachedRenditionSource(cache, rendition)
    }

    /**
     * Reads the head of a track through the same data source chain playback uses,
     * so the stream resolves exactly as it does for the player and the bytes land
     * under the track's cache key.
     */
    fun requestAnalysisHead(uri: Uri) {
        val fetch = upstream ?: return
        val key = cacheKeyOf(uri)
        if (key.isEmpty() || !headInFlight.add(key)) return
        scope.launch {
            try {
                val source = fetch.createDataSource()
                val spec = DataSpec.Builder()
                    .setUri(Uri.parse("https://music.youtube.com/watch?v=$key"))
                    .setKey(key)
                    .setLength(HEAD_PROBE_BYTES)
                    .build()
                val buffer = ByteArray(64 * 1024)
                source.open(spec)
                var total = 0L
                while (total < HEAD_PROBE_BYTES) {
                    val read = source.read(buffer, 0, buffer.size)
                    if (read == C.RESULT_END_OF_INPUT) break
                    total += read
                }
                source.close()
            } catch (_: IOException) {
                // A failed head read just leaves the track unanalysed this time.
            } finally {
                headInFlight.remove(key)
            }
        }
    }

    /** Drops a rendition that could not be decoded so a fresh copy is fetched. */
    fun discardBadRendition(uri: Uri, key: String): Boolean {
        val cache = playerCache ?: return false
        return runCatching { cache.removeResource(key) }.isSuccess
    }

    /** Reads only bytes already cached; nothing is fetched from the network. */
    private class CachedRenditionSource(
        private val cache: Cache,
        private val rendition: Rendition,
    ) : android.media.MediaDataSource() {
        private val source = CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(object : DataSource.Factory {
                override fun createDataSource(): DataSource = object : DataSource {
                    override fun addTransferListener(transferListener: androidx.media3.datasource.TransferListener) {}
                    override fun open(dataSpec: DataSpec): Long = throw IOException("cached bytes only")
                    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = C.RESULT_END_OF_INPUT
                    override fun getUri(): Uri? = null
                    override fun close() {}
                }
            })
            .setCacheKeyFactory { rendition.key }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
            .createDataSource()

        override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
            if (position >= getSize()) return -1
            val spec = DataSpec.Builder()
                .setUri(Uri.parse(rendition.key))
                .setKey(rendition.key)
                .setPosition(position)
                .setLength(size.toLong())
                .build()
            return try {
                source.open(spec)
                var total = 0
                while (total < size) {
                    val read = source.read(buffer, offset + total, size - total)
                    if (read == C.RESULT_END_OF_INPUT) break
                    total += read
                }
                source.close()
                if (total == 0) -1 else total
            } catch (_: IOException) {
                runCatching { source.close() }
                -1
            }
        }

        override fun getSize(): Long = rendition.contentLength.takeIf { it > 0 } ?: rendition.cachedPrefix

        override fun close() {
            runCatching { source.close() }
        }
    }
}
