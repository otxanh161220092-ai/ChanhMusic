/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.artist

import com.metrolist.music.bcmix.canvas.CanvasHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

/**
 * Apple Music's artist art: a hero photograph, usually the artist's title logo
 * drawn over it, and for some artists a looping clip that stands in for the
 * photograph. Ported from BitChord's AppleArtistArt.
 *
 * [keyColor] is the colour Apple fills the artist's Play button with (ARGB), or null.
 */
data class AppleArtistArt(
    val heroUrl: String,
    val logoUrl: String?,
    val logoAspect: Float,
    val videoUrl: String?,
    val keyColor: Int?,
)

/**
 * Finds [AppleArtistArt] from an artist's name. Public, unauthenticated requests
 * only: the iTunes search turns the name into an Apple artist id, and the
 * artist's music.apple.com page embeds its header art as JSON. Every surprise is
 * treated as "no art" — the YouTube header always stays underneath.
 *
 * A name is not an identity, so a same-named Apple artist is only accepted when
 * their catalogue shares a song or album title with the page's own ([titles]).
 * Results are cached per YouTube browse id, misses included.
 */
object AppleArtistArtRepository {

    private const val TAG = "AppleArtistArt"
    private const val HERO_WIDTH = 1200
    private const val LOGO_WIDTH = 1000
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0 Safari/537.36"

    private class Entry(val art: AppleArtistArt?)

    private val cache = HashMap<String, Entry>()

    /** What is already known for [browseId], without a request. */
    fun cached(browseId: String): AppleArtistArt? = synchronized(cache) { cache[browseId] }?.art

    /**
     * The header art for the YouTube artist [browseId] billed as [artistName], or
     * null when Apple has none, or none that provably is this artist. [titles] are
     * the page's own song and album titles, used as the identity check. Never throws.
     */
    suspend fun artFor(
        browseId: String,
        artistName: String,
        titles: Collection<String>,
    ): AppleArtistArt? {
        val key = artistName.norm()
        if (key.isEmpty()) return null
        synchronized(cache) { cache[browseId] }?.let { return it.art }
        val pageTitles = titles.map { it.norm() }.filter { it.isNotEmpty() }.toSet()
        if (pageTitles.isEmpty()) return null

        // A failed request is not an answer, so it is not cached.
        val entry = withContext(Dispatchers.IO) {
            runCatching { lookup(artistName, key, pageTitles) }.getOrNull()
        } ?: return null
        synchronized(cache) { cache[browseId] = entry }
        return entry.art
    }

    private fun lookup(artistName: String, key: String, titles: Set<String>): Entry? {
        val search = get(
            "https://itunes.apple.com/search?term=${URLEncoder.encode(artistName, "UTF-8")}" +
                "&entity=musicArtist&limit=5",
        ) ?: return null
        val results = JSONObject(search).optJSONArray("results") ?: JSONArray()
        var page: String? = null
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optString("artistName").norm() != key) continue
            val id = r.optLong("artistId").takeIf { it > 0 } ?: continue
            if (!sharesTitles(id, titles)) continue
            page = r.optString("artistLinkUrl").substringBefore('?').takeIf { it.isNotBlank() }
                ?: "https://music.apple.com/us/artist/$id"
            break
        }
        if (page == null) return Entry(null)
        val html = get(page) ?: return null
        return Entry(parse(html))
    }

    /** Whether Apple's catalogue for [artistId] shares a song or album title with [titles]. */
    private fun sharesTitles(artistId: Long, titles: Set<String>): Boolean {
        val body = get(
            "https://itunes.apple.com/lookup?id=$artistId&entity=song&limit=200",
        ) ?: return false
        val results = JSONObject(body).optJSONArray("results") ?: return false
        for (i in 0 until results.length()) {
            val r = results.getJSONObject(i)
            if (r.optString("trackName").norm() in titles) return true
            if (r.optString("collectionName").norm() in titles) return true
        }
        return false
    }

    private fun parse(html: String): AppleArtistArt? {
        val marker = html.indexOf("id=\"serialized-server-data\"")
        if (marker < 0) return null
        val start = html.indexOf('>', marker) + 1
        val end = html.indexOf("</script>", start)
        if (start <= 0 || end < 0) return null
        val root = JSONObject("{\"d\":${html.substring(start, end)}}").get("d")
        val header = findHeader(root) ?: return null

        val logo = header.optJSONObject("artistLogo")?.optJSONObject("dictionary")
        val hero = (header.optJSONObject("artwork") ?: header.optJSONObject("colorBackdropArtwork"))
            ?.optJSONObject("dictionary") ?: return null

        val logoW = logo?.optInt("width") ?: 0
        val logoH = logo?.optInt("height") ?: 0
        val heroW = hero.optInt("width").takeIf { it > 0 } ?: return null
        val heroH = hero.optInt("height").takeIf { it > 0 } ?: return null

        val heroUrl = hero.optString("url").fill(HERO_WIDTH, HERO_WIDTH * heroH / heroW, "jpg") ?: return null
        val logoUrl = if (logo != null && logoW > 0 && logoH > 0) {
            logo.optString("url").fill(LOGO_WIDTH, LOGO_WIDTH * logoH / logoW, "png")
        } else {
            null
        }
        val clip = header.optJSONObject("videoArtwork")?.optJSONObject("dictionary")
            ?.optJSONObject("motionArtistSquare1x1")
        val video = clip?.optString("video")?.takeIf { it.startsWith("http") }

        return AppleArtistArt(
            heroUrl = heroUrl,
            logoUrl = logoUrl,
            logoAspect = if (logoUrl != null) logoW.toFloat() / logoH else 1f,
            videoUrl = video,
            keyColor = header.optString("keyColor").toArgb(),
        )
    }

    /** The first object anywhere in [node] that has an `artistLogo` key. */
    private fun findHeader(node: Any?): JSONObject? {
        when (node) {
            is JSONObject -> {
                if (node.has("artistLogo")) return node
                val keys = node.keys()
                while (keys.hasNext()) findHeader(node.opt(keys.next()))?.let { return it }
            }
            is JSONArray -> for (i in 0 until node.length()) findHeader(node.opt(i))?.let { return it }
        }
        return null
    }

    /** Apple's colours are six hex digits with no prefix. */
    private fun String.toArgb(): Int? =
        takeIf { it.length == 6 }?.toIntOrNull(16)?.let { 0xFF000000.toInt() or it }

    private fun String.fill(w: Int, h: Int, format: String): String? {
        if (isBlank() || !contains("{w}")) return null
        return replace("{w}", w.toString())
            .replace("{h}", h.toString())
            .replace("{c}", "bb")
            .replace("{f}", format)
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        return runCatching {
            CanvasHttpClient.client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private fun String.norm(): String = lowercase().filter { it.isLetterOrDigit() }
}
