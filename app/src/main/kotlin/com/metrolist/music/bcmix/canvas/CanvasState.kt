package com.metrolist.music.bcmix.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * The looping cover clip for a track, or null while there is none (or while
 * the lookup runs). Never throws: a missing canvas just leaves the still art.
 */
@Composable
fun rememberCanvasArtwork(
    mediaId: String?,
    title: String?,
    artist: String?,
    album: String?,
    enabled: Boolean,
): CanvasArtwork? {
    var canvas by remember(mediaId) { mutableStateOf<CanvasArtwork?>(null) }
    LaunchedEffect(mediaId, title, artist, album, enabled) {
        canvas = null
        if (!enabled || mediaId.isNullOrBlank() || title.isNullOrBlank() || artist.isNullOrBlank()) return@LaunchedEffect
        canvas = runCatching {
            CanvasRepository.canvasFor(
                CanvasSong(videoId = mediaId, title = title, artist = artist, albumName = album),
            )
        }.getOrNull()
    }
    return canvas
}
