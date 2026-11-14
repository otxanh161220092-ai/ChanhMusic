package com.metrolist.music.bcmix.sources

import com.metrolist.music.bcmix.compat.TrackLog

private const val TAG = "BitChord"

/**
 * JioSaavn as a [MusicSource]. Ported from BitChord's `JioSaavnSource.kt`.
 *
 * Opt-in: its catalogue matching can select the wrong recording, so the
 * caller decides whether it is enabled.
 */
class JioSaavnSource : MusicSource {

    override val configId: String = "jiosaavn"
    override val displayName: String = "JioSaavn"

    override suspend fun health(): SourceHealth = SourceHealth.Ok()

    override suspend fun search(query: String, limit: Int, request: StreamRequest?): List<SourceTrack> {
        TrackLog.d(TAG, "▶ JioSaavn searchSongs() query=\"$query\" limit=$limit")
        val results = prioritizeExplicit(JioSaavnService.searchSongs(query))
        return results.take(limit).map { raw ->
            val primaryArtists = raw.moreInfo.artistMap.primaryArtists.joinToString(", ") { it.name }
            SourceTrack(
                sourceId = raw.id,
                title = raw.title,
                artist = primaryArtists.ifBlank { "Unknown Artist" },
                album = raw.moreInfo.album.ifBlank { null },
                durationSec = raw.moreInfo.duration.toIntOrNull(),
                thumbnailUrl = raw.image
                    .replace(Regex("150x150|50x50"), "500x500")
                    .replace(Regex("^http://"), "https://"),
                isExplicit = raw.isExplicit,
            )
        }
    }

    override suspend fun stream(trackId: String, request: StreamRequest): SourceStream? {
        val stream = JioSaavnService.getStreamUrl(trackId)
        if (stream == null || stream.url.isBlank()) {
            TrackLog.w(TAG, "JioSaavn had no stream URL for $trackId")
            return null
        }
        // A rendition below this is no better than YouTube's Opus, so it is
        // refused here and the resolver moves on.
        if (stream.kbps != null && stream.kbps <= MIN_USABLE_KBPS) {
            TrackLog.w(TAG, "JioSaavn only offered ${stream.kbps}kbps for $trackId; not worth playing")
            return null
        }
        return SourceStream(url = stream.url, codec = "mp4", kbps = stream.kbps)
    }

    private companion object {
        /** JioSaavn files 48, 96, 160 or 320. Below 160 is a downgrade from YouTube. */
        const val MIN_USABLE_KBPS = 96
    }
}
