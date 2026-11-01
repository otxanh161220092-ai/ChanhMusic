/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.File
import java.security.MessageDigest

/** Persistent storage for analysis results, so a song is analyzed once and reused on later plays. */
interface AutomixFeatureStore {
    suspend fun load(mediaId: String): CachedAnalysis?

    suspend fun save(
        mediaId: String,
        analysis: CachedAnalysis,
    )
}

/**
 * One file per song in [directory]. Files are written to a temp file and renamed, so a crash never
 * leaves a half-written entry. When there are more than [MAX_ENTRIES] files, the least recently
 * written are removed. Failures are logged and treated as a cache miss, never as a playback error.
 */
class FileAutomixFeatureStore(
    private val directory: File,
) : AutomixFeatureStore {
    override suspend fun load(mediaId: String): CachedAnalysis? =
        try {
            val file = fileFor(mediaId)
            if (file.exists()) AutomixFeatureCodec.read(file.readBytes()) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to read cached analysis for %s", mediaId)
            null
        }

    override suspend fun save(
        mediaId: String,
        analysis: CachedAnalysis,
    ) {
        try {
            directory.mkdirs()
            val target = fileFor(mediaId)
            val temp = File(directory, target.name + ".tmp")
            temp.writeBytes(AutomixFeatureCodec.write(analysis))
            if (!temp.renameTo(target)) {
                target.delete()
                temp.renameTo(target)
            }
            trim()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Failed to cache analysis for %s", mediaId)
        }
    }

    private fun trim() {
        val files = directory.listFiles { f -> f.name.endsWith(SUFFIX) } ?: return
        if (files.size <= MAX_ENTRIES) return
        files.sortedBy { it.lastModified() }.take(files.size - MAX_ENTRIES).forEach { it.delete() }
    }

    private fun fileFor(mediaId: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(mediaId.toByteArray())
        val name = digest.joinToString("") { "%02x".format(it) }
        return File(directory, name + SUFFIX)
    }

    private companion object {
        const val TAG = "AutomixFeatureStore"
        const val SUFFIX = ".amx"
        const val MAX_ENTRIES = 1_000
    }
}
