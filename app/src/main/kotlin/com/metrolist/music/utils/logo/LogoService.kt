package com.metrolist.music.utils.logo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a transparent PNG logo for an artist or album.
 *
 * Flow: MusicBrainz (name -> MBID) -> Fanart.tv (MBID -> logo) -> TheAudioDB (MBID -> logo).
 * Returns null when nothing is found, so the UI can show the plain text title.
 *
 * Results, including misses, are cached in memory for the life of the process.
 */
class LogoService(
    private val fanartApiKey: String,
    private val theAudioDbKey: String = "2",
    private val userAgent: String = "Metrolist/1.0 (https://github.com/MetrolistGroup/Metrolist)",
) {
    private val cache = ConcurrentHashMap<String, CachedUrl>()
    private val musicBrainzLock = Mutex()
    private var lastMusicBrainzCall = 0L

    /** Wraps a cached value so that "not found" is also remembered. */
    private class CachedUrl(val url: String?)

    suspend fun albumLogo(artist: String, album: String): String? =
        cached("album|${artist.lowercase()}|${album.lowercase()}") {
            val mbid = musicBrainzReleaseGroupId(artist, album) ?: return@cached null
            // Fanart.tv has no album-level transparent logos; the artist's logo is the one that exists.
            val artistMbid = musicBrainzArtistIdFromRelease(mbid) ?: return@cached null
            artistLogoFromMbid(artistMbid)
        }

    suspend fun artistLogo(artist: String): String? =
        cached("artist|${artist.lowercase()}") {
            val mbid = musicBrainzArtistId(artist) ?: return@cached null
            artistLogoFromMbid(mbid)
        }

    private suspend fun artistLogoFromMbid(mbid: String): String? =
        fanartArtistLogo(mbid) ?: theAudioDbArtistLogo(mbid)

    private suspend fun cached(key: String, load: suspend () -> String?): String? {
        cache[key]?.let { return it.url }
        val value = try {
            load()
        } catch (e: Exception) {
            // Network errors are not cached, so a later call can retry.
            return null
        }
        cache[key] = CachedUrl(value)
        return value
    }

    // ---- MusicBrainz -------------------------------------------------------

    private suspend fun musicBrainzArtistId(artist: String): String? {
        val query = "artist:\"${escape(artist)}\""
        val json = getJson("https://musicbrainz.org/ws/2/artist/?query=${encode(query)}&fmt=json&limit=1", musicBrainz = true)
        return json?.optJSONArray("artists")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }
    }

    private suspend fun musicBrainzReleaseGroupId(artist: String, album: String): String? {
        val query = "releasegroup:\"${escape(album)}\" AND artist:\"${escape(artist)}\""
        val json = getJson("https://musicbrainz.org/ws/2/release-group/?query=${encode(query)}&fmt=json&limit=1", musicBrainz = true)
        return json?.optJSONArray("release-groups")?.optJSONObject(0)?.optString("id")?.takeIf { it.isNotEmpty() }
    }

    /** Release-group -> the artist credited on it, which is what Fanart.tv and TheAudioDB key on. */
    private suspend fun musicBrainzArtistIdFromRelease(releaseGroupMbid: String): String? {
        val json = getJson("https://musicbrainz.org/ws/2/release-group/$releaseGroupMbid?inc=artist-credits&fmt=json", musicBrainz = true)
        return json?.optJSONArray("artist-credit")?.optJSONObject(0)?.optJSONObject("artist")?.optString("id")?.takeIf { it.isNotEmpty() }
    }

    // ---- Fanart.tv ---------------------------------------------------------

    private suspend fun fanartArtistLogo(mbid: String): String? {
        if (fanartApiKey.isBlank()) return null
        val json = getJson("https://webservice.fanart.tv/v3/music/$mbid", headers = mapOf("api-key" to fanartApiKey)) ?: return null
        // hdmusiclogo is the high-resolution transparent logo; musiclogo is the fallback size.
        for (key in listOf("hdmusiclogo", "musiclogo")) {
            json.optJSONArray(key)?.optJSONObject(0)?.optString("url")?.takeIf { it.isNotEmpty() }?.let { return it }
        }
        return null
    }

    // ---- TheAudioDB --------------------------------------------------------

    private suspend fun theAudioDbArtistLogo(mbid: String): String? {
        val json = getJson("https://www.theaudiodb.com/api/v1/json/$theAudioDbKey/artist-mb.php?i=$mbid") ?: return null
        return json.optJSONArray("artists")?.optJSONObject(0)?.optString("strArtistLogo")?.takeIf { it.isNotEmpty() && it != "null" }
    }

    // ---- HTTP --------------------------------------------------------------

    private suspend fun getJson(
        url: String,
        headers: Map<String, String> = emptyMap(),
        musicBrainz: Boolean = false,
    ): JSONObject? {
        if (musicBrainz) throttleMusicBrainz()
        return withContext(Dispatchers.IO) {
            val connection = (java.net.URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "application/json")
                headers.forEach { (k, v) -> setRequestProperty(k, v) }
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext null
                JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } finally {
                connection.disconnect()
            }
        }
    }

    /** MusicBrainz asks clients to stay at one request per second. */
    private suspend fun throttleMusicBrainz() {
        musicBrainzLock.withLock {
            val wait = 1_100L - (System.currentTimeMillis() - lastMusicBrainzCall)
            if (wait > 0) kotlinx.coroutines.delay(wait)
            lastMusicBrainzCall = System.currentTimeMillis()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    /** Escapes Lucene special characters used in MusicBrainz queries. */
    private fun escape(value: String) = value.replace("\"", "\\\"")
}
