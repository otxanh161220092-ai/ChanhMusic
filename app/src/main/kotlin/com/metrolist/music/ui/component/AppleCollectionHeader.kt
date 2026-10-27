/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.utils.resize

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
 * Full-screen container for an Apple style collection: blurred artwork over the collection
 * gradient, with [content] (usually the list) drawn on top in the same offscreen layer so
 * highlight buttons blend with it.
 */
@Composable
fun AppleCollectionContainer(
    thumbnailUrl: String?,
    enabled: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val brush = rememberAppleCollectionBrush(thumbnailUrl)
    Box(modifier = Modifier.fillMaxSize().then(if (enabled) Modifier.appleCollectionBackground(brush) else Modifier)) {
        if (enabled) AppleCollectionBackdrop(thumbnailUrl)
        content()
    }
}

/**
 * Background for an Apple style collection. The offscreen layer makes highlight buttons blend
 * with the artwork and gradient behind them instead of with the empty surface.
 */
fun Modifier.appleCollectionBackground(brush: Brush): Modifier =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.background(brush)

/** Overlay-blends a white layer over whatever is already drawn behind the control. */
private fun Modifier.screenHighlight(alpha: Float): Modifier = this.drawBehind {
    drawRoundRect(
        color = Color.White.copy(alpha = alpha),
        cornerRadius = CornerRadius(size.minDimension / 2f),
        blendMode = BlendMode.Overlay,
    )
}

/** Blurred artwork covering the whole collection screen, fading out toward the bottom. */
@Composable
fun BoxScope.AppleCollectionBackdrop(thumbnailUrl: String?) {
    AsyncImage(
        model = thumbnailUrl?.resize(200, 200),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .clipToBounds()
            .blur(64.dp, BlurredEdgeTreatment.Unbounded)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        0.45f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/**
 * Apple Music style collection header (album, playlist): edge-to-edge artwork over a blurred
 * backdrop, with highlight-style buttons whose icons flip between white and black.
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
    val heroColor by rememberArtworkColor(thumbnailUrl?.resize(100, 100))
    val onColor = Color.White
    val playHighlightAlpha = 0.6f
    val sideHighlightAlpha = 0.25f
    val heroLuminance = heroColor?.luminance() ?: 0f
    val onButton =
        if (heroLuminance + playHighlightAlpha * (1f - heroLuminance) > 0.75f) Color.Black else Color.White

    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AsyncImage(
                model = thumbnailUrl?.resize(1080, 1080),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f),
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
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
                            color = Color.Transparent,
                            modifier = Modifier.size(52.dp).screenHighlight(sideHighlightAlpha),
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
                        color = Color.Transparent,
                        modifier = Modifier.height(52.dp).width(148.dp).screenHighlight(playHighlightAlpha),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.play),
                                contentDescription = null,
                                tint = onButton,
                                modifier = Modifier.size(22.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.play),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = onButton,
                            )
                        }
                    }

                    Surface(
                        onClick = onMore,
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier.size(52.dp).screenHighlight(sideHighlightAlpha),
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
    }
}
