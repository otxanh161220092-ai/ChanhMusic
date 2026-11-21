package com.metrolist.music.utils.logo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

private const val TAG = "LogoService"
/** Marks a cached URL as a Wikimedia logo; cover art URLs carry no mark. */
private const val LOGO_MARK = "logo:"

/**
 * Finds a logo image for an album or artist.
 *
 * Album: Wikimedia Commons only (a file named like "<Artist> <Album> logo").
 * Artist: MusicBrainz -> Fanart.tv -> TheAudioDB.
 * Returns null when nothing is found, so the UI can show the album title as styled text.
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

    private class CachedUrl(val url: String?)

    /** [url] is a logo (transparent, blended onto the background) or a plain cover image. */
    data class AlbumImage(val url: String, val isLogo: Boolean)

    /**
     * The album's own image: a transparent logo from Wikimedia Commons when one exists,
     * otherwise the front cover from Cover Art Archive. An artist logo is never used.
     */
    suspend fun albumImage(artist: String, album: String): AlbumImage? {
        Log.d(TAG, "albumImage: $artist - $album")
        return albumImageCached(artist, album)
    }

    private suspend fun albumImageCached(artist: String, album: String): AlbumImage? {
        val key = "album|${artist.lowercase()}|${album.lowercase()}"
        cache[key]?.let { hit ->
            return hit.url?.let { u -> AlbumImage(u.removePrefix(LOGO_MARK), isLogo = u.startsWith(LOGO_MARK)) }
        }
        val value = try {
            wikimediaAlbumLogo(artist, album)?.let { LOGO_MARK + it }
                ?: coverArtUrl(artist, album)
        } catch (e: Exception) {
            Log.w(TAG, "album lookup failed for $key", e)
            return null
        }
        cache[key] = CachedUrl(value)
        return value?.let { AlbumImage(it.removePrefix(LOGO_MARK), isLogo = it.startsWith(LOGO_MARK)) }
    }

    private suspend fun coverArtUrl(artist: String, album: String): String? {
        val rgMbid = musicBrainzReleaseGroupId(artist, album) ?: return null
        return "https://coverartarchive.org/release-group/$rgMbid/front-500"
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
            Log.w(TAG, "lookup failed for $key", e)
            return null
        }
        cache[key] = CachedUrl(value)
        return value
    }

    // ---- Wikimedia Commons -------------------------------------------------

    /**
     * Searches file names for "<Artist> <Album> logo" and "<Album> (logo)", keeps only
     * PNG/SVG files whose name contains "logo", and returns the first original file URL.
     */
    private suspend fun wikimediaAlbumLogo(artist: String, album: String): String? {
        val terms = listOf(
            "$artist $album logo",
            "$album logo",
            "$album (logo)",
            "$album (album logo)",
            "$artist $album (logo)",
        )
        for (term in terms) {
            val json = getJson(
                "https://commons.wikimedia.org/w/api.php?action=query&format=json" +
                    "&generator=search&gsrsearch=${encode(term)}&gsrnamespace=6&gsrlimit=20" +
                    "&prop=imageinfo&iiprop=url",
            ) ?: continue
            val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: continue
            val candidates = mutableListOf<Pair<Int, String>>()
            pages.keys().forEach { key ->
                val page = pages.getJSONObject(key)
                val title = page.optString("title")
                val lower = title.lowercase()
                val isLogoFile = lower.contains("logo") && (lower.endsWith(".png") || lower.endsWith(".svg"))
                val url = page.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("url")
                if (isLogoFile && !url.isNullOrEmpty()) {
                    candidates += page.optInt("index", Int.MAX_VALUE) to url
                }
            }
            Log.d(TAG, "wikimedia '$term': ${pages.length()} results, ${candidates.size} logo files")
            candidates.minByOrNull { it.first }?.let { return it.second }
        }
        Log.d(TAG, "wikimedia: no logo for $artist - $album")
        return null
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

    /** Release-group -> the artist credited on it. */
    private suspend fun musicBrainzArtistIdFromRelease(releaseGroupMbid: String): String? {
        val json = getJson("https://musicbrainz.org/ws/2/release-group/$releaseGroupMbid?inc=artist-credits&fmt=json", musicBrainz = true)
        return json?.optJSONArray("artist-credit")?.optJSONObject(0)?.optJSONObject("artist")?.optString("id")?.takeIf { it.isNotEmpty() }
    }

    // ---- Fanart.tv ---------------------------------------------------------

    private suspend fun fanartArtistLogo(mbid: String): String? {
        if (fanartApiKey.isBlank()) return null
        val json = getJson("https://webservice.fanart.tv/v3/music/$mbid", headers = mapOf("api-key" to fanartApiKey)) ?: return null
        // hdmusiclogo is the high-resolution transparent logo; musiclogo is the smaller one.
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
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
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
            if (wait > 0) delay(wait)
            lastMusicBrainzCall = System.currentTimeMillis()
        }
    }

    private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")

    private fun escape(value: String) = value.replace("\"", "\\\"")
}
