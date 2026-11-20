/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.music.archivetune.lyrics

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.metrolist.music.archivetune.constants.EnableMegalobizLyricsKey
import com.metrolist.music.archivetune.megalobiz.MegalobizLyrics
import com.metrolist.music.utils.dataStore
import com.metrolist.music.utils.get

object MegalobizLyricsProvider : LyricsProvider {
    override val name: String = "Megalobiz"

    override fun isEnabled(context: Context): Boolean = context.dataStore[EnableMegalobizLyricsKey] ?: true

    override suspend fun getLyrics(
        id: String,
        title: String,
        artist: String,
        album: String?,
        duration: Int,
    ): Result<String> =
        withContext(Dispatchers.IO) {
            MegalobizLyrics.getLyrics(title = title, artist = artist).mapCatching { lyrics ->
                if (!LyricsUtils.isLineSyncedLrc(lyrics)) throw NoSuchElementException("Lyrics not found on Megalobiz")
                lyrics
            }
        }
}
