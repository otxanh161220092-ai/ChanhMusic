/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.content.Context
import android.net.Uri
import android.view.TextureView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs

/** Per-song video backgrounds the user picked on this device. Only the file address is stored, never the video. */
object VideoArtworkStore {
    private const val PREFS = "player_video_artwork"

    fun get(
        context: Context,
        songId: String?,
    ): Uri? {
        if (songId == null) return null
        return try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(songId, null)?.let(Uri::parse)
        } catch (e: Exception) {
            null
        }
    }

    fun set(
        context: Context,
        songId: String,
        uri: Uri,
    ) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(songId, uri.toString()).apply()
        } catch (e: Exception) {
            // Storage unavailable: the choice simply is not remembered.
        }
    }
}

/**
 * Muted video that plays in step with the song. [positionProvider] is the song's position; the video
 * loops on its own length, so a clip shorter than the song repeats until the song ends.
 */
@Composable
fun LocalVideoArtwork(
    uri: Uri,
    positionProvider: () -> Long,
    isPlaying: StateFlow<Boolean>,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val playing by isPlaying.collectAsState()
    var aspect by remember { mutableFloatStateOf(16f / 9f) }

    val videoPlayer =
        remember(uri) {
            ExoPlayer.Builder(context).build().apply {
                volume = 0f
                repeatMode = Player.REPEAT_MODE_ALL
                setMediaItem(MediaItem.fromUri(uri))
                prepare()
            }
        }

    DisposableEffect(videoPlayer) {
        val listener =
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    if (videoSize.width > 0 && videoSize.height > 0) {
                        aspect = videoSize.width.toFloat() / videoSize.height.toFloat()
                    }
                }
            }
        videoPlayer.addListener(listener)
        onDispose {
            videoPlayer.removeListener(listener)
            videoPlayer.release()
        }
    }

    LaunchedEffect(playing, videoPlayer) {
        videoPlayer.playWhenReady = playing
    }

    // Keeps the clip on the song's clock: it restarts at the song position and corrects drift after that.
    LaunchedEffect(videoPlayer) {
        while (true) {
            val length = videoPlayer.duration
            if (length > 0) {
                val target = positionProvider() % length
                if (abs(videoPlayer.currentPosition - target) > DRIFT_TOLERANCE_MS) {
                    videoPlayer.seekTo(target)
                }
            }
            delay(SYNC_INTERVAL_MS)
        }
    }

    Box(modifier = modifier.fillMaxWidth().aspectRatio(aspect)) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().aspectRatio(aspect),
            factory = { ctx ->
                TextureView(ctx).also { videoPlayer.setVideoTextureView(it) }
            },
            update = { view -> videoPlayer.setVideoTextureView(view) },
        )
    }
}

private const val SYNC_INTERVAL_MS = 500L
private const val DRIFT_TOLERANCE_MS = 300L
