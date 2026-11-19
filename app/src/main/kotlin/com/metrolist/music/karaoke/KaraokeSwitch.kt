/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.metrolist.music.constants.AudioQuality
import com.metrolist.music.utils.InnerTubeXPlayer
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Swaps the current track between its stream and its instrumental stem, on the one player the
 * app already has. It only reads Automix's state: when Automix is on, the stream is always
 * restored, so Automix's own playback is never given a file.
 */
object KaraokeSwitch {

    /** Separation progress from 0 to 1 while a track is being split, -1 when idle. */
    val progress = MutableStateFlow(-1f)

    /** The stream item of each track currently playing its instrumental, to restore later. */
    private val streamItems = HashMap<String, MediaItem>()

    fun sync(player: Player, context: Context, karaokeOn: Boolean, automixOn: Boolean) {
        val index = player.currentMediaItemIndex
        if (index < 0) return
        val item = player.currentMediaItem ?: return
        val mediaId = item.mediaId
        val swapped = item.localConfiguration?.uri?.scheme == "file"
        val wantInstrumental = karaokeOn && !automixOn && KaraokeCache.isReady(context, mediaId)
        when {
            wantInstrumental && !swapped -> {
                streamItems[mediaId] = item
                val file = KaraokeCache.instrumentalFile(context, mediaId)
                replace(player, index, item.buildUpon().setUri(Uri.fromFile(file)).build())
            }
            !wantInstrumental && swapped -> {
                streamItems.remove(mediaId)?.let { replace(player, index, it) }
            }
        }
    }

    /** Fetches the track's stream, then separates it into the cache. Returns false if that fails. */
    suspend fun prepare(
        context: Context,
        mediaId: String,
        connectivityManager: ConnectivityManager,
        onProgress: (Float) -> Unit,
    ): Boolean {
        if (KaraokeCache.isReady(context, mediaId)) return true
        val playback = withContext(Dispatchers.IO) {
            InnerTubeXPlayer.playerResponseForPlayback(
                mediaId,
                audioQuality = AudioQuality.AUTO,
                connectivityManager = connectivityManager,
            ).getOrNull()
        } ?: return false
        val source = KaraokeCache.sourceFile(context, mediaId)
        val downloaded = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder().url(playback.streamUrl).apply {
                    playback.streamHeaders.forEach { (name, value) -> header(name, value) }
                }.build()
                com.metrolist.music.bcmix.canvas.CanvasHttpClient.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use false
                    val body = response.body ?: return@use false
                    source.outputStream().use { out -> body.byteStream().copyTo(out) }
                    true
                }
            }.getOrDefault(false)
        }
        if (!downloaded) return false
        return try {
            withContext(Dispatchers.Default) {
                KaraokeCache.prepare(context, mediaId, Uri.fromFile(source), onProgress)
            }
        } finally {
            source.delete()
        }
    }

    private fun replace(player: Player, index: Int, item: MediaItem) {
        val position = player.currentPosition
        player.replaceMediaItem(index, item)
        player.seekTo(index, position)
    }
}
