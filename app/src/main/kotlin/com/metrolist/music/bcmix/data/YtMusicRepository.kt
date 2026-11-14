package com.metrolist.music.bcmix.data

/**
 * Track metadata lookup used by the length probe. NOT PORTED: BitChord reads
 * this from its own InnerTube repository. Returns a failure, so the caller
 * treats the length as unknown.
 */
object YtMusicRepository {
    data class TrackLinks(val durationText: String?)

    suspend fun trackLinks(videoId: String): Result<TrackLinks> =
        Result.failure(UnsupportedOperationException("trackLinks not ported"))
}
