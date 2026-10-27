/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.ui.utils.resize
import coil3.compose.AsyncImage

/** Page background for Apple style collections: artwork color fading into the page background. */
@Composable
fun rememberAppleCollectionBrush(artworkUrl: String?): Brush {
    val artColor by rememberArtworkColor(artworkUrl?.resize(100, 100))
    val heroColor = artColor ?: MaterialTheme.colorScheme.surfaceVariant
    val pageColor = MaterialTheme.colorScheme.background
    return remember(heroColor, pageColor) {
        Brush.verticalGradient(
            0f to heroColor,
            0.85f to heroColor,
            1f to pageColor,
        )
    }
}

/**
 * Apple Music style collection header (album, playlist): centered artwork,
 * white title and text, a white Play pill and round side buttons.
 */
@Composable
fun AppleCollectionHeader(
    title: String,
    thumbnailUrl: String?,
    artistText: String?,
    metaText: String?,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    onShuffle: (() -> Unit)? = null,
    onArtistClick: (() -> Unit)? = null,
) {
    val onColor = Color.White

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier =
                Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(8.dp)),
            shape = RoundedCornerShape(8.dp),
        ) {
            AsyncImage(
                model = thumbnailUrl?.resize(1080, 1080),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = onColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        if (!artistText.isNullOrBlank()) {
            Text(
                text = artistText,
                style = MaterialTheme.typography.bodyLarge,
                color = onColor.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = if (onArtistClick != null) Modifier.clickable(onClick = onArtistClick) else Modifier,
            )
        }

        if (!metaText.isNullOrBlank()) {
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = onColor.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onShuffle != null) {
                Surface(
                    onClick = onShuffle,
                    shape = CircleShape,
                    color = onColor.copy(alpha = 0.2f),
                    modifier = Modifier.size(52.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = stringResource(R.string.shuffle_home),
                            tint = onColor,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }

            Surface(
                onClick = onPlay,
                shape = CircleShape,
                color = onColor,
                modifier = Modifier.height(52.dp).width(148.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.play),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Surface(
                onClick = onMore,
                shape = CircleShape,
                color = onColor.copy(alpha = 0.2f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        tint = onColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            ExpandableText(
                text = description,
                modifier = Modifier.fillMaxWidth(),
                collapsedMaxLines = 3,
            )
        }
    }
}
