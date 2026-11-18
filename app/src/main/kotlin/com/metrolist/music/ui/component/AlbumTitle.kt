package com.metrolist.music.ui.component

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
 * Shows the album's own logo when one exists, otherwise the title as styled text.
 * Artist logos are never used here.
 */
@Composable
fun AlbumTitle(
    title: String,
    artist: String,
    modifier: Modifier = Modifier,
) {
    val logoUrl by produceState<String?>(initialValue = null, title, artist) {
        value = albumLogos.albumLogo(artist = artist, album = title)
    }
    var logoFailed by remember(logoUrl) { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()

    val url = logoUrl
    if (url != null && !logoFailed) {
        AsyncImage(
            model = url,
            contentDescription = title,
            contentScale = ContentScale.Fit,
            onError = { logoFailed = true },
            // Dark theme: a black background drops out under Screen. Light theme: a white one drops out under Multiply.
            modifier = modifier
                .fillMaxWidth()
                .height(96.dp)
                .padding(horizontal = 32.dp)
                .graphicsLayer { blendMode = if (dark) BlendMode.Screen else BlendMode.Multiply },
        )
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
