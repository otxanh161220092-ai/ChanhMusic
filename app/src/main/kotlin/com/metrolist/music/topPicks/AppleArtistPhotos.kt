package com.metrolist.music.topPicks

import com.metrolist.innertube.models.AlbumItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.innertube.pages.ArtistPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * An artist's Apple Music photo, found from their name, ported from BitChord's
 * AppleArtistPhotos and AppleCatalogue. A photo is used only when an Apple
 * artist with that name shares a song or album title with the YouTube artist
 * page, so a name alone never borrows someone else's face.
 *
 * Plain HttpURLConnection, so nothing new is added to the build. Every failure
 * returns null and the card falls back to YouTube's art.
 */
object AppleArtistPhotos {
    private const val TAG = "AppleArtistPhotos"
    private const val HEAD_LIMIT = 64 * 1024
    private const val MATCHES_TRIED = 2
    private const val TIMEOUT_MS = 8_000
    private const val KEY_PREFIX = "v2|"
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true }
    private val known = ConcurrentHashMap<String, String>()
    private val OG_IMAGE = Regex("""property="og:image"\s+content="([^"]+)"""")

    /** Case, accents and punctuation do not make two names different; a `$` is read as an S. */
    fun matchKey(name: String): String =
        Normalizer.normalize(name.lowercase(Locale.ROOT).replace('$', 's'), Normalizer.Form.NFD)
            .filter { it.isLetterOrDigit() }

    /** A title both services agree on. Titles shorter than four letters prove nothing. */
    fun titleKey(title: String): String? =
        matchKey(
            title.substringBefore(" (").substringBefore(" [")
                .removeSuffix(" - Single").removeSuffix(" - EP"),
        ).takeIf { it.length >= 4 }

    /** Songs and albums of the YouTube artist page, the titles that prove who it is. */
    fun titlesOf(page: ArtistPage): Set<String> =
        page.sections.flatMap { it.items }.mapNotNullTo(HashSet()) { item ->
            when (item) {
                is SongItem -> titleKey(item.title)
                is AlbumItem -> titleKey(item.title)
                else -> null
            }
        }

    /**
     * The Apple photo for [artistName], or null when none provably is them.
     * Cached for the process. Never throws.
     */
    suspend fun photoFor(artistName: String, page: ArtistPage): String? {
        val name = matchKey(artistName)
        if (name.isEmpty()) return null
        val key = KEY_PREFIX + name
        known[key]?.let { return it.ifEmpty { null } }
        val titles = titlesOf(page)
        if (titles.isEmpty()) return null
        val found = withContext(Dispatchers.IO) {
            runCatching { lookup(artistName, name, titles) }
                .onFailure { Timber.tag(TAG).d(it, "lookup failed for %s", artistName) }
                .getOrElse { return@withContext null } // a network miss is not remembered
                ?: ""
        } ?: return null
        known[key] = found
        return found.ifEmpty { null }
    }

    private fun lookup(artistName: String, key: String, titles: Set<String>): String? {
        val url = "https://itunes.apple.com/search?term=${URLEncoder.encode(artistName, "UTF-8")}" +
            "&entity=musicArtist&limit=5"
        val body = fetch(url) ?: return null
        val results = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return null
        return results.asSequence()
            .map { it.jsonObject }
            .filter { matchKey(it.string("artistName").orEmpty()) == key }
            .filter { it.string("artistLinkUrl").orEmpty().startsWith("https://music.apple.com/") }
            .filter { result ->
                val id = result.string("artistId")?.toLongOrNull() ?: return@filter false
                sharesTitles(id, titles)
            }
            .mapNotNull { it.string("artistLinkUrl")?.substringBefore('?') }
            .take(MATCHES_TRIED)
            .firstNotNullOfOrNull(::ogImage)
    }

    private fun sharesTitles(artistId: Long, titles: Set<String>): Boolean {
        val body = fetch("https://itunes.apple.com/lookup?id=$artistId&entity=song&limit=200") ?: return false
        val rows = json.parseToJsonElement(body).jsonObject["results"]?.jsonArray ?: return false
        return rows.any { row ->
            val o = row.jsonObject
            listOf("trackName", "collectionName").any { field ->
                o[field]?.jsonPrimitive?.content?.let(::titleKey) in titles
            }
        }
    }

    /** The page's og:image, read from its head only. */
    private fun ogImage(pageUrl: String): String? {
        val conn = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (conn.responseCode !in 200..299) return null
            val head = StringBuilder()
            val buf = ByteArray(8_192)
            conn.inputStream.use { input ->
                while (head.length < HEAD_LIMIT) {
                    val n = input.read(buf)
                    if (n == -1) break
                    head.append(String(buf, 0, n, Charsets.UTF_8))
                    OG_IMAGE.find(head)?.let { return it.groupValues[1].takeIf(::isArtistPhoto) }
                    if ("</head>" in head) break
                }
            }
            null
        } finally {
            conn.disconnect()
        }
    }

    private fun fetch(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (conn.responseCode in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }

    /** A portrait, not an album cover: Apple's artist photos sit under Features or AMCArtistImages. */
    private fun isArtistPhoto(url: String): Boolean {
        val bucket = url.substringAfter("mzstatic.com/image/thumb/", "").substringBefore('/')
        return bucket.startsWith("Features") || bucket.startsWith("AMCArtistImages")
    }

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.content
}
