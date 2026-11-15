package com.metrolist.music.bcmix.canvas

import kotlinx.coroutines.flow.MutableStateFlow
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/** Shared HTTP client for canvas lookups (replaces BitChord's Http.client). */
object CanvasHttpClient {
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}

/** The track fields canvas lookups need. Built from Metrolist's MediaMetadata at the call site. */
data class CanvasSong(
    val videoId: String,
    val title: String,
    val artist: String,
    val albumName: String? = null,
    val localUri: String? = null,
    val localPath: String? = null,
)

/** Canvas preferences (replaces BitChord's AppSettings canvas keys). */
object CanvasSettings {
    val prioritizeSpotifyCanvas = MutableStateFlow(false)
    val canvasOverCellular = MutableStateFlow(true)
    val canvasEnabled = MutableStateFlow(true)
    val spotifySpdcToken = MutableStateFlow<String?>(null)
}

/** Same easing BitChord uses for canvas fades. */
fun smootherStep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * x * (x * (x * 6f - 15f) + 10f)
}

const val FADE_CURVE_STOPS = 16

/** True while the host lifecycle is RESUMED. */
@androidx.compose.runtime.Composable
fun rememberIsForeground(): Boolean {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    var foreground by androidx.compose.runtime.remember(lifecycle) {
        androidx.compose.runtime.mutableStateOf(
            lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED),
        )
    }
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, _ ->
            foreground = lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    return foreground
}
