package com.metrolist.music.bcmix.data

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineName
import okhttp3.OkHttpClient

/** Monotonic millisecond clock. Ported from BitChord's `MonotonicClock`. */
object MonotonicClock {
    fun nowMs(): Long = System.nanoTime() / 1_000_000L
}

/**
 * Shared OkHttp client for BitChord's data layer. Metrolist's own client is
 * not reused because BitChord's calls carry their own headers and timeouts.
 */
object Http {
    val client: OkHttpClient by lazy { OkHttpClient() }
}

/**
 * Logging facade. Ported from BitChord's `TrackLog`: [about] names the track a
 * line belongs to, so a log can be filed under it. Forwards to the bcmix compat
 * logger.
 */
object TrackLog {
    fun d(tag: String, message: String, extra: Any? = null, about: Any? = null) =
        com.metrolist.music.bcmix.compat.TrackLog.d(tag, message, extra, about)

    fun i(tag: String, message: String, extra: Any? = null, about: Any? = null) =
        com.metrolist.music.bcmix.compat.TrackLog.i(tag, message, extra, about)

    fun w(tag: String, message: String, extra: Any? = null, about: Any? = null) =
        com.metrolist.music.bcmix.compat.TrackLog.w(tag, message, extra, about)

    fun e(tag: String, message: String, extra: Any? = null, about: Any? = null) =
        com.metrolist.music.bcmix.compat.TrackLog.e(tag, message, extra, about)

    /** A coroutine context that names the track the work belongs to. */
    fun about(id: String?): CoroutineContext = CoroutineName("track:${id.orEmpty()}")
}
