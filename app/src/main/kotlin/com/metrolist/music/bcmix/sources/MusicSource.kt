package com.metrolist.music.bcmix.sources

/**
 * A URL the player can open, and what is expected to come back out of it.
 * Ported from BitChord's `MusicSource.kt`; trimmed to the fields the
 * Metrolist port uses.
 */
data class SourceStream(
    val url: String,
    val codec: String = "",
    val kbps: Int? = null,
    val headers: Map<String, String> = emptyMap(),
)

/** How much of the stream the caller is willing to pay for. */
sealed interface StreamRequest {
    /** Bit-exact, whatever it costs. */
    data object Lossless : StreamRequest

    /** A transcode at or below [maxKbps]. */
    data class Capped(val maxKbps: Int) : StreamRequest

    /** Whatever the source considers its best lossy rendition. */
    data object Best : StreamRequest
}

/** Whether a source is reachable and usable. */
sealed interface SourceHealth {
    data class Ok(val detail: String? = null) : SourceHealth
    data class Unreachable(val reason: String) : SourceHealth
    data class Rejected(val reason: String) : SourceHealth

    val isOk: Boolean get() = this is Ok
}

/**
 * One place tracks can come from. Implementations are cheap to build and
 * must not throw for an ordinary miss: a miss is `null`, not an error.
 */
interface MusicSource {
    /** Stable id for this source, unique among configured sources. */
    val configId: String

    /** Display name. */
    val displayName: String

    suspend fun health(): SourceHealth

    /**
     * Tracks matching [query]. The returned ids are this source's own ids,
     * as expected by [stream].
     */
    suspend fun search(query: String, limit: Int = 25, request: StreamRequest? = null): List<SourceTrack>

    /**
     * @return an openable stream, or null when this source does not have the
     *   track after all.
     */
    suspend fun stream(trackId: String, request: StreamRequest): SourceStream?
}

/** A search hit from a [MusicSource], before it is mapped onto a Metrolist song. */
data class SourceTrack(
    val sourceId: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationSec: Int?,
    val thumbnailUrl: String?,
    val isExplicit: Boolean,
)
