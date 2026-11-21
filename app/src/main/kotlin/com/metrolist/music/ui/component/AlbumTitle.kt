package com.metrolist.music.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.utils.logo.LogoService

/** Shared across the app so each album is looked up at most once per process. */
private val albumLogos by lazy { LogoService(fanartApiKey = "") }

/**
 * Shows the album's own logo, or its cover art, when one exists; otherwise [fallback]
 * (or the title as styled text). Artist logos are never used here.
 *
 * [allowCover] = false keeps only logos, for layouts that already show the cover.
 * [blendLogo] = false draws the logo as is, for layouts with a dark or photo background.
 */
@Composable
fun AlbumTitle(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
    allowCover: Boolean = true,
    blendLogo: Boolean = true,
    fallback: (@Composable () -> Unit)? = null,
) {
    val image by produceState<LogoService.AlbumImage?>(initialValue = null, title, artist) {
        value = albumLogos.albumImage(artist = artist, album = title)
    }
    var imageFailed by remember(image) { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()

    val current = image
    if (current != null && !imageFailed && (current.isLogo || allowCover)) {
        if (current.isLogo) {
            AsyncImage(
                model = current.url,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                onError = { imageFailed = true },
                // Dark theme: a black background drops out under Screen. Light theme: a white one drops out under Multiply.
                modifier = modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .padding(horizontal = 32.dp)
                    .then(
                        if (blendLogo) {
                            Modifier.graphicsLayer { blendMode = if (dark) BlendMode.Screen else BlendMode.Multiply }
                        } else {
                            Modifier
                        },
                    ),
            )
        } else {
            // Cover art is an opaque picture: show it as is, no blend.
            AsyncImage(
                model = current.url,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                onError = { imageFailed = true },
                modifier = modifier
                    .size(180.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
        }
    } else if (fallback != null) {
        fallback()
    } else {
        Text(
            text = title,
            style =
                MaterialTheme.typography.headlineSmall.copy(
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    shadow = Shadow(
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        blurRadius = 28f,
                    ),
                ),
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.padding(horizontal = 32.dp),
        )
    }
}
