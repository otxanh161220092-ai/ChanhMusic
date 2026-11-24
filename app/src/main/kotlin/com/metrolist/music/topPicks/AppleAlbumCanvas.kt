package com.metrolist.music.topPicks

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.Normalizer
import java.util.Base64
import java.util.Locale

/** Where a clip came from. Only APPLE is looked up in Metrolist for now. */
enum class CanvasSource { SPOTIFY, APPLE, OTHER }

/**
 * What Apple Music says about an album, for the header: its genre and year, whether
 * it has a lossless version, and its editorial description. Every field is optional.
 */
data class AppleAlbumDetails(
    val genre: String?,
    val year: String?,
    val lossless: Boolean,
    val description: String?,
    /** [AppleAlbumCanvas.trackKey] of the artist's top songs that the album may carry. */
    val popular: Set<String> = emptySet(),
    /** Full release date as dd/MM/yyyy, when Apple has one. */
    val releaseDate: String? = null,
    val trackCount: Int? = null,
    val explicit: Boolean = false,
)

/**
 * A short looping clip for a card. [url] is the square rendition; [portraitUrl] is
 * the 3:4 one the phone player runs full screen. Ported from BitChord's CanvasArtwork.
 */
data class CanvasArtwork(
    val url: String,
    val fallbackUrl: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val source: CanvasSource = CanvasSource.OTHER,
    val portraitUrl: String? = null,
)

/**
 * Apple Music motion artwork for a release, ported from BitChord's AppleMusicCanvas
 * (album path only: Top Picks cards are releases, not tracks).
 *
 * It needs the anonymous bearer token the Apple Music web player uses, which is read
 * from the web player's JS bundle, and the search is scored so a wrong album is never
 * returned. Every failure returns null, so the card keeps its still cover.
 */
object AppleAlbumCanvas {
    private const val TAG = "AppleAlbumCanvas"
    private const val AMP = "https://amp-api.music.apple.com/v1/catalog"
    private const val WEB_PLAYER = "https://music.apple.com/us/browse"
    private const val CANVAS_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val TIMEOUT_MS = 8_000
    private const val TOKEN_RETRY_MS = 30L * 60 * 1000
    private const val MIN_SCORE = 12

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val storefront: String by lazy {
        Locale.getDefault().country.takeIf { it.length == 2 }?.lowercase(Locale.ROOT) ?: "us"
    }

    /** Motion artwork for [album] by [artist], or null when no clean match exists. */
    fun searchAlbum(album: String, artist: String): CanvasArtwork? {
        val bearer = token() ?: return null
        val term = if (album.contains(artist, ignoreCase = true)) album else "$artist $album"
        val url = "$AMP/$storefront/search?term=${URLEncoder.encode(term, "UTF-8")}" +
            "&types=albums&limit=10&extend=editorialVideo"
        val body = get(url, bearer) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val hits = root["results"]?.jsonObject?.get("albums")?.jsonObject?.get("data")?.jsonArray ?: return null

        val ranked = hits.mapNotNull { hit ->
            val record = hit as? JsonObject ?: return@mapNotNull null
            val score = score(record, album, artist, album) ?: return@mapNotNull null
            score to record
        }.sortedByDescending { it.first }

        for ((score, record) in ranked) {
            if (score < MIN_SCORE) break
            val attributes = record["attributes"]?.jsonObject ?: continue
            val name = attributes["name"]?.jsonPrimitive?.contentOrNull
            if (name != null && isCompilation(name)) continue
            val video = attributes["editorialVideo"]?.jsonObject ?: continue
            val (primary, alternate) = motionUrls(video) ?: continue
            Timber.tag(TAG).d("motion artwork for album %s", name)
            return CanvasArtwork(
                url = primary,
                fallbackUrl = alternate,
                title = name,
                artist = attributes["artistName"]?.jsonPrimitive?.contentOrNull,
                album = name,
                source = CanvasSource.APPLE,
                portraitUrl = tallUrl(video),
            )
        }
        return null
    }

    /** Genre, year, lossless and description of [album] by [artist] from Apple, or null when no clean match. */
    fun albumDetails(album: String, artist: String): AppleAlbumDetails? {
        val bearer = token() ?: return null
        val term = if (album.contains(artist, ignoreCase = true)) album else "$artist $album"
        val url = "$AMP/$storefront/search?term=${URLEncoder.encode(term, "UTF-8")}" +
            "&types=albums&limit=10&extend=editorialNotes"
        val body = get(url, bearer) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val hits = root["results"]?.jsonObject?.get("albums")?.jsonObject?.get("data")?.jsonArray ?: return null

        val best = hits.mapNotNull { hit ->
            val record = hit as? JsonObject ?: return@mapNotNull null
            val score = score(record, album, artist, album) ?: return@mapNotNull null
            score to record
        }.maxByOrNull { it.first } ?: return null
        if (best.first < MIN_SCORE) return null

        val attributes = best.second["attributes"]?.jsonObject ?: return null
        val genre = attributes["genreNames"]?.jsonArray
            ?.mapNotNull { it.jsonPrimitive.contentOrNull }
            ?.firstOrNull { !it.equals("Music", ignoreCase = true) }
        val year = attributes["releaseDate"]?.jsonPrimitive?.contentOrNull?.take(4)?.takeIf { it.length == 4 }
        val lossless = attributes["audioTraits"]?.jsonArray?.any {
            it.jsonPrimitive.contentOrNull in LOSSLESS_TRAITS
        } == true
        val notes = attributes["editorialNotes"]?.jsonObject
        val description = (notes?.get("standard") ?: notes?.get("short"))?.jsonPrimitive?.contentOrNull
            ?.takeIf { it.isNotBlank() }
        val artistId = best.second["relationships"]?.jsonObject?.get("artists")?.jsonObject
            ?.get("data")?.jsonArray?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
            ?: artistIdByName(splitArtists(artist).firstOrNull().orEmpty(), bearer)
        Timber.tag(TAG).d("popular: artistId=%s for %s", artistId, artist)
        val popular = artistId?.let { topSongKeys(it) }.orEmpty()
        Timber.tag(TAG).d("popular: %d top songs matched for %s", popular.size, album)
        val releaseDate = attributes["releaseDate"]?.jsonPrimitive?.contentOrNull
            ?.let { Regex("""(\d{4})-(\d{2})-(\d{2})""").find(it) }
            ?.let { "${it.groupValues[3]}/${it.groupValues[2]}/${it.groupValues[1]}" }
        val trackCount = attributes["trackCount"]?.jsonPrimitive?.intOrNull
        val explicit = attributes["contentRating"]?.jsonPrimitive?.contentOrNull == "explicit"
        return AppleAlbumDetails(genre, year, lossless, description, popular, releaseDate, trackCount, explicit)
    }

    /** The Apple artist id whose name matches [name], found by search when the album does not carry it. */
    private fun artistIdByName(name: String, bearer: String): String? {
        if (name.isBlank()) return null
        val url = "$AMP/$storefront/search?term=${URLEncoder.encode(name, "UTF-8")}&types=artists&limit=5"
        val body = get(url, bearer) ?: return null
        val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return null
        val rows = root["results"]?.jsonObject?.get("artists")?.jsonObject?.get("data")?.jsonArray ?: return null
        return rows.firstOrNull { row ->
            row.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
                ?.normalizeForMatch() == name.normalizeForMatch()
        }?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull
    }

    /**
     * Titles of the artist's top songs on Apple. This is the closest public stand-in
     * for Apple's popular-track marks on an album: those marks are not in the API.
     */
    private fun topSongKeys(artistId: String): Set<String> {
        val bearer = token() ?: return emptySet()
        val body = get("$AMP/$storefront/artists/$artistId/view/top-songs?limit=10", bearer) ?: return emptySet()
        val rows = runCatching { json.parseToJsonElement(body).jsonObject["data"]?.jsonArray }
            .getOrNull() ?: return emptySet()
        return rows.mapNotNullTo(HashSet()) { row ->
            row.jsonObject["attributes"]?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull?.let(::trackKey)
        }
    }

    /** A track title as both services spell it: without a "(…)" or "[…]" tail, folded for case and accents. */
    fun trackKey(title: String): String =
        title.substringBefore(" (").substringBefore(" [").normalizeForMatch()

    private val LOSSLESS_TRAITS = setOf("lossless", "hi-res-lossless")

    private fun score(record: JsonObject, title: String, artist: String, album: String): Int? {
        val attributes = record["attributes"]?.jsonObject ?: return null
        val hitName = attributes["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val hitArtist = attributes["artistName"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (isCompilation(hitName)) return null

        val wanted = splitArtists(artist)
        val credited = splitArtists(hitArtist)
        if (wanted.isEmpty() || credited.isEmpty()) return null
        if (!wanted.all { want -> credited.any { it == want } }) return null

        var score = 10
        val wantTitle = title.normalizeForMatch()
        val hitTitle = hitName.normalizeForMatch()
        score += when {
            hitTitle == wantTitle -> 15
            hitTitle.contains(wantTitle) || wantTitle.contains(hitTitle) -> 7
            else -> -10
        }
        for (word in EDITION_WORDS) {
            val inWanted = title.contains(word, ignoreCase = true)
            val inHit = hitName.contains(word, ignoreCase = true)
            if (inWanted && inHit) score += 5 else if (inHit) score -= 3
        }
        return score
    }

    private val EDITION_WORDS =
        listOf("deluxe", "expanded", "remastered", "remix", "version", "edit", "mix", "bonus")

    private val COMPILATION_MARKERS = listOf(
        "playlist", "set list", "essentials", "dj mix", "mixed",
        "apple music", "today's hits", "session",
    )

    private fun isCompilation(name: String): Boolean {
        val lower = name.lowercase(Locale.ROOT)
        return COMPILATION_MARKERS.any { lower.contains(it) }
    }

    private fun link(video: JsonObject, key: String): String? = video[key]?.jsonObject?.let { asset ->
        asset["video"]?.jsonPrimitive?.contentOrNull
            ?: asset["videoUrl"]?.jsonPrimitive?.contentOrNull
            ?: asset["hlsUrl"]?.jsonPrimitive?.contentOrNull
            ?: asset["url"]?.jsonPrimitive?.contentOrNull
    }?.takeIf { it.isNotBlank() }

    private fun tallUrl(video: JsonObject): String? =
        link(video, "motionDetailTall") ?: link(video, "motionTallVideo3x4")

    private fun motionUrls(video: JsonObject): Pair<String, String?>? {
        val square = link(video, "motionDetailSquare") ?: link(video, "motionSquareVideo1x1")
        val raw = link(video, "motionDetailRaw")
        val tall = tallUrl(video)
        val primary = square ?: raw ?: tall ?: return null
        val alternate = listOfNotNull(square, raw, tall).firstOrNull { it != primary }
        return primary to alternate
    }

    // ---- Token ----

    private var cachedToken: String? = null
    private var tokenExpiresAtMs = 0L
    private var retryTokenAfterMs = 0L
    private val rejected = mutableSetOf<String>()

    @Synchronized
    private fun token(): String? {
        val now = System.currentTimeMillis()
        cachedToken?.let { if (now < tokenExpiresAtMs - 60_000) return it }
        if (now < retryTokenAfterMs) return null

        val html = plainGet(WEB_PLAYER, mapOf("User-Agent" to CANVAS_UA)).second
        val scripts = html?.let {
            Regex("""/assets/index(?:-legacy)?[~-][A-Za-z0-9_-]+\.js""")
                .findAll(it).map(MatchResult::value).distinct().toList()
        }.orEmpty()

        for (path in scripts) {
            val script = plainGet("https://music.apple.com$path", mapOf("User-Agent" to CANVAS_UA)).second ?: continue
            val candidates = Regex("""ey[A-Za-z0-9_-]+\.ey[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
                .findAll(script)
                .map(MatchResult::value)
                .distinct()
                .filter { it !in rejected }
                .mapNotNull { jwt -> expiry(jwt)?.let { jwt to it } }
                .filter { it.second > now }
                .toList()
            if (candidates.isEmpty()) continue
            val (jwt, expiresAt) = candidates.firstOrNull { isWebPlayerToken(it.first) } ?: candidates.first()
            cachedToken = jwt
            tokenExpiresAtMs = expiresAt
            return jwt
        }
        retryTokenAfterMs = now + TOKEN_RETRY_MS
        return null
    }

    private fun get(url: String, bearer: String): String? {
        val (code, body) = plainGet(
            url,
            mapOf(
                "Authorization" to "Bearer $bearer",
                "Origin" to "https://music.apple.com",
                "Referer" to "https://music.apple.com/",
                "User-Agent" to CANVAS_UA,
            ),
        )
        if (code == 401) {
            synchronized(this) {
                rejected += bearer
                if (cachedToken == bearer) {
                    cachedToken = null
                    tokenExpiresAtMs = 0L
                }
            }
            return null
        }
        return body
    }

    /** Returns the status code and body (body only on 2xx). Never throws. */
    private fun plainGet(url: String, headers: Map<String, String>): Pair<Int, String?> = runCatching {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            headers.forEach { (name, value) -> setRequestProperty(name, value) }
        }
        try {
            val code = conn.responseCode
            code to if (code in 200..299) conn.inputStream.bufferedReader().use { it.readText() } else null
        } finally {
            conn.disconnect()
        }
    }.getOrDefault(-1 to null)

    private fun isWebPlayerToken(jwt: String): Boolean = runCatching {
        val parts = jwt.split(".")
        val header = String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8)
        val payload = String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
        header.contains("WebPlayKid") || payload.contains("AMPWebPlay")
    }.getOrDefault(false)

    private fun expiry(jwt: String): Long? = runCatching {
        val payload = String(Base64.getUrlDecoder().decode(jwt.split(".")[1]), Charsets.UTF_8)
        Regex("\"exp\"\\s*:\\s*(\\d+)").find(payload)?.groupValues?.get(1)?.toLong()?.times(1000)
    }.getOrNull()
}

/** Case, accents and punctuation folded away, as BitChord compares titles. */
fun String.normalizeForMatch(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD)
        .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        .lowercase(Locale.ROOT)
        .replace(Regex("[^a-z0-9\\s]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

/** One credit string split into its individual artists. */
fun splitArtists(raw: String): List<String> =
    raw.split(ARTIST_SEPARATORS).map { it.normalizeForMatch() }.filter { it.isNotBlank() }

private val ARTIST_SEPARATORS = Regex(
    "(?:\\s*,\\s*|\\s*&\\s*|\\s+×\\s+|\\s+x\\s+|\\bfeat\\.?\\b|\\bft\\.?\\b|\\bfeaturing\\b|\\bwith\\b)",
    RegexOption.IGNORE_CASE,
)
