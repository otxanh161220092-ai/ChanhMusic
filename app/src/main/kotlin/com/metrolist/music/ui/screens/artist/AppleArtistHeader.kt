/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.artist

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.AlbumItem
import com.metrolist.music.R
import com.metrolist.music.ui.component.LocalAppleCollectionTextColor
import com.metrolist.music.ui.utils.resize

/**
 * Apple Music style artist header: a square portrait fading into the blurred backdrop, with the
 * name and round controls on the fade, followed by the latest album as a card.
 */
@Composable
fun AppleArtistHeader(
    name: String,
    thumbnailUrl: String?,
    description: String?,
    isSubscribed: Boolean,
    latestAlbum: AlbumItem?,
    logoRes: Int?,
    onPlay: (() -> Unit)?,
    onToggleSubscribe: (() -> Unit)?,
    onLatestAlbumClick: (AlbumItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val onColor = LocalAppleCollectionTextColor.current.takeIf { it != Color.Unspecified } ?: Color.White
    val onPlayColor = if (onColor == Color.Black) Color.White else Color.Black
    var showInfo by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            AsyncImage(
                model = thumbnailUrl?.resize(1080, 1080),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(
                                0f to Color.Black,
                                0.5f to Color.Black,
                                1f to Color.Transparent,
                            ),
                            blendMode = BlendMode.DstIn,
                        )
                    },
            )

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .offset(y = 64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (logoRes != null) {
                    Image(
                        painter = painterResource(logoRes),
                        contentDescription = name,
                        colorFilter = ColorFilter.tint(onColor),
                        modifier = Modifier.requiredWidth(if (logoRes == R.drawable.channel_logo_ereus) 520.dp else 300.dp),
                    )
                } else {
                Text(
                    text = name,
                    fontSize = 40.sp,
                    lineHeight = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = onColor,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!description.isNullOrBlank()) {
                        RoundControl(size = 52.dp, onClick = { showInfo = true }) {
                            Icon(
                                painter = painterResource(R.drawable.info),
                                contentDescription = null,
                                tint = onColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }

                    if (onPlay != null) {
                        Surface(
                            onClick = onPlay,
                            shape = CircleShape,
                            color = onColor,
                            modifier = Modifier.size(64.dp),
                        ) {
                            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                                Icon(
                                    painter = painterResource(R.drawable.play),
                                    contentDescription = null,
                                    tint = onPlayColor,
                                    modifier = Modifier.size(28.dp),
                                )
                            }
                        }
                    }

                    if (onToggleSubscribe != null) {
                        RoundControl(size = 52.dp, onClick = onToggleSubscribe) {
                            Icon(
                                painter = painterResource(
                                    if (isSubscribed) R.drawable.star_filled else R.drawable.star_outline,
                                ),
                                contentDescription = null,
                                tint = onColor,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(72.dp))

        if (latestAlbum != null) {
            LatestAlbumCard(
                album = latestAlbum,
                onColor = onColor,
                onClick = { onLatestAlbumClick(latestAlbum) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            )
        }
    }

    if (showInfo && !description.isNullOrBlank()) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) {
                    Text(stringResource(R.string.close))
                }
            },
            title = { Text(name, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = description,
                    modifier = Modifier
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}

@Composable
private fun RoundControl(
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(size).highlight(0.25f),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
private fun LatestAlbumCard(
    album: AlbumItem,
    onColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = onColor.copy(alpha = 0.12f),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AsyncImage(
                model = album.thumbnail.resize(160, 160),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                album.year?.let {
                    Text(
                        text = it.toString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor.copy(alpha = 0.7f),
                    )
                }
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = onColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Surface(
                shape = CircleShape,
                color = onColor.copy(alpha = 0.15f),
                modifier = Modifier.size(36.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.download),
                        contentDescription = null,
                        tint = onColor,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

fun channelLogoFor(artistId: String?, name: String?): Int? {
    val id = artistId?.removePrefix("MPLA")
    return when {
        id == "UC9EnTDbvIsouypHkAhVRVmw" || name.equalsName("Jasmine Lemon") -> R.drawable.channel_logo_jasmine
        id == "UCwHrSL1grR1vatPkF8MW1hQ" || name.equalsName("ereusmixes") -> R.drawable.channel_logo_ereus
        else -> null
    }
}

private fun String?.equalsName(other: String): Boolean = this?.trim()?.equals(other, ignoreCase = true) == true

/** Overlay-blends a white layer over whatever is behind the control, like the Apple highlight. */
private fun Modifier.highlight(alpha: Float): Modifier = this.drawBehind {
    drawRoundRect(
        color = Color.White.copy(alpha = alpha),
        cornerRadius = CornerRadius(size.minDimension / 2f),
        blendMode = BlendMode.Overlay,
    )
}
