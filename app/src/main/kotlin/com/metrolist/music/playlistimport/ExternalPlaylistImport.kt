/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playlistimport

import com.metrolist.innertube.YouTube
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.topPicks.AppleAlbumCanvas
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber
import java.net.HttpURLConnection
import java.time.LocalDateTime
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

data class ExternalTrack(val title: String, val artists: String, val durationSec: Int?)

data class ExternalPlaylist(val name: String, val tracks: List<ExternalTrack>)

data class ExternalImportResult(
    val playlistId: String,
    val matched: Int,
    val total: Int,
    val unmatched: List<ExternalTrack>,
)

/**
 * Imports a public Spotify or Apple Music playlist link into a local Metrolist playlist.
 * Spotify fetching and language-aware matching follow BitChord's SpotifyImporter.
 * Each source track is searched on YouTube Music; a result is accepted when its duration is
 * within 3 seconds of the source track (Apple only, Spotify embed tracks carry no duration past 100).
 */
object ExternalPlaylistImport {
    private const val TAG = "ExternalPlaylistImport"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    private const val MAX_APPLE_PAGES = 10
    private const val EMBED_TRACK_LIMIT = 100
    private const val PAGE_SIZE = 100
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val FETCH_PLAYLIST_HASH = "19ff1327c29e99c208c86d7a9d8f1929cfdf3d3202a0ff4253c821f1901aa94d"
    private const val SEARCH_CONCURRENCY = 4

    private val KNOWN_LANGUAGES = listOf(
        "Tamil", "Telugu", "Kannada", "Malayalam", "Hindi",
        "English", "Punjabi", "Marathi", "Bengali", "Bhojpuri", "Gujarati",
    )

    private val spotifyRegex = Regex("""open\.spotify\.com/(?:intl-[a-z]{2}/)?playlist/([A-Za-z0-9]+)""")
    private val appleRegex = Regex("""music\.apple\.com/[a-z]{2}/playlist/[^/?#]+/(pl\.[A-Za-z0-9]+)""")
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun isSupported(link: String): Boolean = spotifyRegex.containsMatchIn(link) || appleRegex.containsMatchIn(link)

    /**
     * [language] narrows the YouTube Music search (null or "No Preference"/"Multilingual" means none).
     * [onProgress] receives (done, total) while tracks are matched.
     * Returns null when nothing could be read or matched; no playlist is created then.
     */
    suspend fun importPlaylist(
        database: MusicDatabase,
        link: String,
        language: String? = null,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): ExternalImportResult? {
        val source = withContext(Dispatchers.IO) { fetch(link) } ?: return null
        if (source.tracks.isEmpty()) return null

        val cleanLanguage = language?.trim()?.takeIf {
            it.isNotBlank() && !it.equals("No Preference", ignoreCase = true) && !it.equals("Multilingual", ignoreCase = true)
        }
        val total = source.tracks.size
        val done = AtomicInteger(0)
        val semaphore = Semaphore(SEARCH_CONCURRENCY)
        val found: List<SongItem?> = coroutineScope {
            source.tracks.map { track ->
                async(Dispatchers.IO) {
                    semaphore.withPermit {
                        val result = findOnYouTubeMusic(track, cleanLanguage)
                        onProgress(done.incrementAndGet(), total)
                        result
                    }
                }
            }.awaitAll()
        }

        val songIds = mutableListOf<String>()
        val unmatched = mutableListOf<ExternalTrack>()
        found.forEachIndexed { index, song ->
            if (song == null) {
                unmatched += source.tracks[index]
            } else {
                withContext(Dispatchers.IO) { database.transaction { insert(song.toMediaMetadata()) } }
                songIds += song.id
            }
        }
        if (songIds.isEmpty()) return null

        val entity = PlaylistEntity(
            name = source.name.ifBlank { "Imported playlist" },
            bookmarkedAt = LocalDateTime.now(),
            isEditable = true,
        )
        withContext(Dispatchers.IO) {
            database.query { insert(entity) }
            val playlist = database.playlist(entity.id).firstOrNull()
            if (playlist != null) {
                database.addSongsToPlaylist(playlist, songIds.map { it to null })
            }
        }
        return ExternalImportResult(entity.id, songIds.size, total, unmatched)
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
        val pageProps = json.parseToJsonElement(data).jsonObject["props"]?.jsonObject
            ?.get("pageProps")?.jsonObject ?: return null
        val state = pageProps["state"]?.jsonObject
        val entity = state?.get("data")?.jsonObject?.get("entity")?.jsonObject
            ?: pageProps["entity"]?.jsonObject
            ?: return null
        val tracks = entity["trackList"]?.jsonArray?.mapNotNull { element ->
            val o = element.jsonObject
            val title = o["title"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            ExternalTrack(
                title = title,
                artists = o["subtitle"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty(),
                durationSec = o["duration"]?.jsonPrimitive?.longOrNull?.let { (it / 1000).toInt() },
            )
        }.orEmpty()
        if (tracks.isEmpty()) return null

        // The embed page stops at its first 100 tracks; the web player's query, with the
        // anonymous token the embed hands out, pages through the rest. If it refuses, keep the 100.
        val token = state?.get("settings")?.jsonObject?.get("session")?.jsonObject
            ?.get("accessToken")?.jsonPrimitive?.contentOrNull
        val more = if (tracks.size >= EMBED_TRACK_LIMIT && token != null) {
            runCatching { fetchRemainingSpotifyTracks(id, token, tracks.size) }.getOrNull().orEmpty()
        } else {
            emptyList()
        }
        return ExternalPlaylist(
            name = entity["name"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            tracks = tracks + more,
        )
    }

    /** Tracks from [start] to the end of the playlist, a page at a time. */
    private fun fetchRemainingSpotifyTracks(playlistId: String, token: String, start: Int): List<ExternalTrack> {
        val out = mutableListOf<ExternalTrack>()
        var offset = start
        while (true) {
            val variables = """{"uri":"spotify:playlist:$playlistId","offset":$offset,"limit":$PAGE_SIZE,"enableWatchFeedEntrypoint":false}"""
            val extensions = """{"persistedQuery":{"version":1,"sha256Hash":"$FETCH_PLAYLIST_HASH"}}"""
            val url = "$PATHFINDER_URL?operationName=fetchPlaylist" +
                "&variables=${URLEncoder.encode(variables, "UTF-8")}" +
                "&extensions=${URLEncoder.encode(extensions, "UTF-8")}"
            val body = httpGet(
                url,
                mapOf(
                    "Authorization" to "Bearer $token",
                    "App-platform" to "WebPlayer",
                    "Accept" to "application/json",
                ),
            ) ?: break
            val content = json.parseToJsonElement(body).jsonObject["data"]?.jsonObject
                ?.get("playlistV2")?.jsonObject?.get("content")?.jsonObject ?: break
            val items = content["items"]?.jsonArray ?: break
            for (item in items) {
                val data = item.jsonObject["itemV2"]?.jsonObject?.get("data")?.jsonObject ?: continue
                val title = data["name"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
                if (title.isEmpty()) continue
                val artists = data["artists"]?.jsonObject?.get("items")?.jsonArray
                    ?.mapNotNull { it.jsonObject["profile"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull }
                    ?.joinToString(", ")
                    .orEmpty()
                out += ExternalTrack(title = title, artists = artists, durationSec = null)
            }
            val total = content["totalCount"]?.jsonPrimitive?.intOrNull ?: break
            offset += items.size
            if (items.isEmpty() || offset >= total) break
        }
        return out
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

    /**
     * Best YouTube Music song for [track]: among candidates not tagged with another language
     * (when [language] is set), the one whose duration is within 3 s, else the first one.
     */
    private suspend fun findOnYouTubeMusic(track: ExternalTrack, language: String?): SongItem? {
        val query = listOfNotNull(
            track.title,
            track.artists.takeIf { it.isNotBlank() },
            language,
        ).joinToString(" ")
        val candidates = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG)
            .getOrNull()?.items.orEmpty()
            .filterIsInstance<SongItem>()
            .distinctBy { it.id }
        if (candidates.isEmpty()) return null

        val pool = if (language != null) {
            val conflicting = KNOWN_LANGUAGES.filterNot { it.equals(language, ignoreCase = true) }
            candidates.filterNot { candidate ->
                val meta = "${candidate.title} ${candidate.artists.joinToString(" ") { it.name }} ${candidate.album?.name.orEmpty()}".lowercase()
                conflicting.any { lang ->
                    val l = lang.lowercase()
                    meta.contains("($l)") || meta.contains("$l version") || meta.contains("$l song")
                }
            }.ifEmpty { candidates }
        } else {
            candidates
        }
        val wanted = track.durationSec
        return pool.firstOrNull { wanted != null && it.duration != null && kotlin.math.abs(it.duration!! - wanted) <= 3 }
            ?: pool.first()
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
