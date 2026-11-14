package com.metrolist.music.bcmix.compat

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Metrolist has no party mode, so the automix never sees one. */
object ListenTogether {
    data class State(val inParty: Boolean = false)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
}

/** Logging shim matching BitChord's TrackLog call shape. */
object TrackLog {
    fun d(tag: String, message: String, vararg extra: Any?) { Log.d(tag, message + extra.joinToString(" ")) }
    fun w(tag: String, message: String, vararg extra: Any?) { Log.w(tag, message + extra.joinToString(" ")) }
    fun i(tag: String, message: String, vararg extra: Any?) { Log.i(tag, message + extra.joinToString(" ")) }
    fun e(tag: String, message: String, vararg extra: Any?) { Log.e(tag, message + extra.joinToString(" ")) }
}

/** Only the fields the automix reads from a track. */
data class Song(
    val videoId: String,
    val title: String,
    val localUri: android.net.Uri? = null,
)

/** Only the fields the transition engine reads from a lyric line. */
data class LyricWord(val startMs: Long, val endMs: Long, val text: String)

data class LyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val sungUntilMs: Long? = null,
    val isGap: Boolean = false,
)

/** Metrolist has no video-origin rows, so no item is one. */
val androidx.media3.common.MediaItem.isVideoOrigin: Boolean get() = false

/** Bit-perfect output is not part of Metrolist, so it is never active. */
object BitPerfectOutput {
    data class Status(val active: Boolean = false)
    val status: kotlinx.coroutines.flow.StateFlow<Status> =
        kotlinx.coroutines.flow.MutableStateFlow(Status())
}

/**
 * Sleep-after-track state the transition engine checks before it blends into
 * the next track. Metrolist's own sleep timer is not connected to it yet.
 */
object SleepTimer {
    val afterTrack: kotlinx.coroutines.flow.StateFlow<Boolean> =
        kotlinx.coroutines.flow.MutableStateFlow(false)

    fun cancel() {}
}
