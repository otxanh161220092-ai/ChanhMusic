package com.metrolist.music.bcmix.sources

import com.metrolist.music.bcmix.compat.TrackLog

private const val TAG = "BitChord"

/**
 * Chooses a substitute stream for a track, following BitChord's rules:
 * a candidate only replaces the playing stream when it clears the gain
 * bar ([worthSwapping]), and a match is accepted only when the runtime
 * agrees ([sameRecordingAs]).
 *
 * Ported subset: JioSaavn is the only substitute source for now. The
 * YouTube path in BitChord goes through NewPipe Extractor, which Metrolist
 * does not bundle; it is not reproduced here.
 */
object SourceResolver {

    /** A stream that is better than YouTube's Opus must clear this gain. */
    const val UPGRADE_MIN_GAIN_KBPS = 96

    /** Runtimes within this many seconds are the same recording. */
    const val SAME_RECORDING_SEC = 3

    /** YouTube's best AAC rendition, used as the comparison point. */
    const val YOUTUBE_BEST_AAC_KBPS = 256

    /** How many search rows are read before matching. */
    const val MATCH_CANDIDATES = 10

    /**
     * Whether [candidate] is enough better than [playing] to justify
     * swapping mid-play. Mirrors BitChord's `worthSwapping`.
     */
    internal fun worthSwapping(candidate: StreamFormat, playing: StreamFormat?): Boolean {
        if (playing?.isDolbyAtmos == true && !candidate.isDolbyAtmos) return false
        if (candidate.isLossless == true || candidate.isDolbyAtmos) return true
        val gain = (candidate.kbps ?: return false) - (playing?.kbps ?: return false)
        return gain >= UPGRADE_MIN_GAIN_KBPS
    }

    /** Runtimes agree to within [slackSec] seconds. Unknown on either side is not agreement. */
    internal fun sameRecordingAs(candidateSec: Int?, playingSec: Int?, slackSec: Int = SAME_RECORDING_SEC): Boolean {
        if (candidateSec == null || playingSec == null) return false
        return kotlin.math.abs(candidateSec - playingSec) <= slackSec
    }

    /**
     * Finds the JioSaavn stream for a track when it is a clear upgrade over
     * [playing]. Returns null on a miss, a wrong-length match, or a stream
     * that does not clear [worthSwapping].
     */
    suspend fun substituteFromJioSaavn(
        target: TrackMatcher.Target,
        playing: StreamFormat?,
        request: StreamRequest = StreamRequest.Best,
    ): SourceStream? {
        val source = JioSaavnSource()
        for (query in TrackMatcher.queries(target)) {
            val tracks = source.search(query, MATCH_CANDIDATES, request)
            if (tracks.isEmpty()) continue
            val songs = tracks.map {
                MatchSong(
                    title = it.title,
                    artist = it.artist,
                    durationText = it.durationSec?.let { s -> "${s / 60}:${"%02d".format(s % 60)}" },
                    albumName = it.album,
                    isExplicit = it.isExplicit,
                )
            }
            val ranked = TrackMatcher.ranked(songs, target)
            val best = ranked.firstOrNull() ?: continue
            val track = tracks[songs.indexOf(best)]
            if (!sameRecordingAs(track.durationSec, target.durationSec)) {
                TrackLog.d(TAG, "JioSaavn '${track.title}' is a different cut; refused")
                continue
            }
            val stream = source.stream(track.sourceId, request) ?: continue
            val candidate = StreamFormat(codec = stream.codec, kbps = stream.kbps)
            if (!worthSwapping(candidate, playing)) {
                TrackLog.d(TAG, "JioSaavn ${stream.kbps}kbps not worth swapping in")
                continue
            }
            return stream
        }
        return null
    }
}
