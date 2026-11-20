/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.music.archivetune.lyrics

import android.content.Context
import android.util.Log
import com.metrolist.music.archivetune.constants.EnableMusixmatchLyricsKey
import com.metrolist.music.archivetune.musixmatch.Musixmatch
import com.metrolist.music.archivetune.utils.GlobalLog
import com.metrolist.music.archivetune.utils.dataStore
import com.metrolist.music.archivetune.utils.get

object MusixmatchLyricsProvider : LyricsProvider {
    init {
        Musixmatch.logger = { message ->
            GlobalLog.append(Log.INFO, "Musixmatch", message)
        }
    }

    override val name = "Musixmatch"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableMusixmatchLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        Musixmatch.getLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
        )

    override suspend fun getAllLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
        callback: (String) -> Unit,
    ) {
        Musixmatch.getAllLyrics(
            title = title,
            artist = artist,
            album = album,
            durationSeconds = duration,
            callback = callback,
        )
    }
}
