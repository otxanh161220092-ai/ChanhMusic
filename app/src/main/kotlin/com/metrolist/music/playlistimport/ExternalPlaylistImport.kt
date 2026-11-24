/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.topPicks.AppleAlbumCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

data class ExternalTrack(val title: String, val artists: String, val durationSec: Int?)

data class ExternalPlaylist(val name: String, val tracks: List<ExternalTrack>)

data class ExternalImportResult(val playlistId: String, val matched: Int, val total: Int)

/**
 * Imports a public Spotify or Apple Music playlist link into a local Metrolist playlist.
 * Each source track is searched on YouTube Music; a result is accepted when its duration is
 * within 3 seconds of the source track, otherwise the first search result is used.
 */
object ExternalPlaylistImport {
    private const val TAG = "ExternalPlaylistImport"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"
    private const val MAX_APPLE_PAGES = 10

    private val spotifyRegex = Regex("""open\.spotify\.com/(?:intl-[a-z]{2}/)?playlist/([A-Za-z0-9]+)""")
    private val appleRegex = Regex("""music\.apple\.com/[a-z]{2}/playlist/[^/?#]+/(pl\.[A-Za-z0-9]+)""")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isSupported(link: String): Boolean = spotifyRegex.containsMatchIn(link) || appleRegex.containsMatchIn(link)

    suspend fun importPlaylist(database: MusicDatabase, link: String): ExternalImportResult? {
        val source = withContext(Dispatchers.IO) { fetch(link) } ?: return null
        if (source.tracks.isEmpty()) return null

        val songIds = mutableListOf<String>()
        var matched = 0
        for (track in source.tracks) {
            val found = withContext(Dispatchers.IO) { findOnYouTubeMusic(track) } ?: continue
            withContext(Dispatchers.IO) {
                database.transaction { insert(found.toMediaMetadata()) }
            }
            songIds += found.id
            matched++
        }

        val entity = PlaylistEntity(name = source.name.ifBlank { "Imported playlist" })
        withContext(Dispatchers.IO) {
            database.query { insert(entity) }
            val playlist = database.playlist(entity.id).firstOrNull()
            if (playlist != null && songIds.isNotEmpty()) {
                database.addSongsToPlaylist(playlist, songIds.map { it to null })
            }
        }
        return ExternalImportResult(entity.id, matched, source.tracks.size)
    }

    private fun fetch(link: String): ExternalPlaylist? {
        spotifyRegex.find(link)?.let { return fetchSpotify(it.groupValues[1]) }
        appleRegex.find(link)?.let { return fetchApple(it.groupValues[1]) }
        return null
    }

    private fun fetchSpotify(id: String): ExternalPlaylist? {
        val html = httpGet("https://open.spotify.com/embed/playlist/$id") ?: return null
        val data = Regex("""<script id="__NEXT_DATA__" type="application/json">(.*?)</script>""", RegexOption.DOT_MATCHES_ALL)
            .find(html)?.groupValues?.get(1) ?: return null
        val entity = json.parseToJsonElement(data).jsonObject["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject?.get("state")?.jsonObject
            ?.get("data")?.jsonObject?.get("entity")?.jsonObject ?: return null
        val tracks = entity["trackList"]?.jsonArray?.mapNotNull { element ->
            val o = element.jsonObject
            val title = o["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            ExternalTrack(
                title = title,
                artists = o["subtitle"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                durationSec = o["duration"]?.jsonPrimitive?.longOrNull?.let { (it / 1000).toInt() },
            )
        }.orEmpty()
        return ExternalPlaylist(
            name = entity["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            tracks = tracks,
        )
    }

    private fun fetchApple(id: String): ExternalPlaylist? {
        val bearer = AppleAlbumCanvas.token() ?: return null
        var url: String? = "${AppleAlbumCanvas.AMP}/${AppleAlbumCanvas.storefront}/playlists/$id?include=tracks"
        var name = ""
        val tracks = mutableListOf<ExternalTrack>()
        var pages = 0
        while (url != null && pages < MAX_APPLE_PAGES) {
            val body = httpGet(url, mapOf(
                "Authorization" to "Bearer $bearer",
                "Origin" to "https://music.apple.com",
                "Referer" to "https://music.apple.com/",
            )) ?: break
            val root = json.parseToJsonElement(body).jsonObject
            val playlist = root["data"]?.jsonArray?.firstOrNull()?.jsonObject ?: break
            if (name.isEmpty()) {
                name = playlist["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty()
            }
            val trackList = playlist["relationships"]?.jsonObject?.get("tracks")?.jsonObject ?: break
            trackList["data"]?.jsonArray?.forEach { element ->
                val attrs = element.jsonObject["attributes"]?.jsonObject ?: return@forEach
                val title = attrs["name"]?.jsonPrimitive?.contentOrNull ?: return@forEach
                tracks += ExternalTrack(
                    title = title,
                    artists = attrs["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    durationSec = attrs["durationInMillis"]?.jsonPrimitive?.longOrNull?.let { (it / 1000).toInt() },
                )
            }
            val next = trackList["next"]?.jsonPrimitive?.contentOrNull
            url = next?.let { "https://amp-api.music.apple.com$it" }
            pages++
        }
        return if (tracks.isEmpty()) null else ExternalPlaylist(name = name, tracks = tracks)
    }

    private suspend fun findOnYouTubeMusic(track: ExternalTrack): SongItem? {
        val query = listOf(track.title, track.artists).filter { it.isNotBlank() }.joinToString(" ")
        val candidates = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()?.items.orEmpty()
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }
        val wanted = track.durationSec
        return candidates.firstOrNull { wanted != null && it.duration != null && kotlin.math.abs(it.duration!! - wanted) <= 3 }
            ?: candidates.firstOrNull()
    }

    private fun httpGet(url: String, headers: Map<String, String> = emptyMap()): String? = try {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
        }
        if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            Timber.tag(TAG).w("HTTP ${connection.responseCode} for $url")
            null
        }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "Request failed: $url")
        null
    }
}
