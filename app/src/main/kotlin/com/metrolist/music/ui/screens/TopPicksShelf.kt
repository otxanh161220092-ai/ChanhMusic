package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.topPicks.TopPick
import com.metrolist.music.topPicks.TopPickKind
import com.metrolist.music.topPicks.TopPickMood
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.utils.resize

/**
 * Top Picks for You row, laid out like BitChord's TopPicksShelf: 3:4 cards,
 * 60% of the row wide (max 280dp), 14dp apart, a small eyebrow line above the
 * title, and a caption on the card's bottom edge.
 *
 * Simplified from BitChord: no animated canvas art. Stations and moods use a
 * static gradient, and artist cards use circular photos.
 */
private const val TOP_PICK_RATIO = 0.75f
private const val TOP_PICK_FRACTION = 0.60f
private val TOP_PICK_MAX_WIDTH = 280.dp
private val TopPickShape = RoundedCornerShape(12.dp)

private fun topPickWidth(available: Dp): Dp = minOf(available * TOP_PICK_FRACTION, TOP_PICK_MAX_WIDTH)

@Composable
fun TopPicksShelf(
    picks: List<TopPick>,
    listenerName: String?,
    onClick: (TopPick, String) -> Unit,
) {
    Column(Modifier.padding(bottom = 26.dp)) {
        NavigationTitle(title = "Top Picks for You", modifier = Modifier.padding(horizontal = 16.dp))
        BoxWithConstraints {
            val cardWidth = topPickWidth(maxWidth)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                itemsIndexed(picks, key = { index, pick -> "$index:${pick.kind}:${pick.title}" }) { _, pick ->
                    TopPickCard(
                        pick = pick,
                        listenerName = listenerName,
                        onClick = onClick,
                        modifier = Modifier.width(cardWidth),
                    )
                }
            }
        }
    }
}

private class CardText(val eyebrow: String, val title: String, val subtitle: String)

private fun cardText(pick: TopPick, listenerName: String?): CardText {
    val artist = pick.artistName.orEmpty()
    return when (pick.kind) {
        TopPickKind.LATEST_RELEASE -> CardText("New from $artist", pick.title, pick.subtitle)
        TopPickKind.PERSONAL_STATION -> CardText(
            "Made for You",
            listenerName?.substringBefore(' ')?.takeIf { it.isNotBlank() }?.let { "$it’s Station" } ?: "Your Station",
            "",
        )
        TopPickKind.MOOD -> {
            val mood = pick.mood ?: TopPickMood.FEEL_GOOD
            CardText("Mood for You", mood.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }, "")
        }
        TopPickKind.ARTIST_AND_SIMILAR -> CardText("Featuring $artist", "$artist & Similar Artists", "")
        TopPickKind.FEATURED_ARTIST -> CardText("Featuring $artist", pick.title, pick.subtitle)
        TopPickKind.NEW_RELEASE_MIX -> CardText("Made for You", pick.title, pick.subtitle)
        TopPickKind.DISCOVERY_STATION -> CardText("Made for You", "Discovery Station", "")
    }
}

private val StationColors = listOf(Color(0xFF8A1C3A), Color(0xFF2B0B17))
private val DiscoveryColors = listOf(Color(0xFF4B2A9E), Color(0xFF1A0E3A))
private val MoodColors = listOf(Color(0xFF2E5E8C), Color(0xFF0E1F33))
private val CollageColors = listOf(
    listOf(Color(0xFF1F5E4A), Color(0xFF08201A)),
    listOf(Color(0xFF6B3A7A), Color(0xFF221028)),
    listOf(Color(0xFF7A4E1F), Color(0xFF2A1A0A)),
)

@Composable
private fun TopPickCard(
    pick: TopPick,
    listenerName: String?,
    onClick: (TopPick, String) -> Unit,
    modifier: Modifier,
) {
    val text = cardText(pick, listenerName)
    Box(
        modifier = modifier
            .aspectRatio(TOP_PICK_RATIO)
            .clip(TopPickShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable { onClick(pick, text.title) },
    ) {
        val eyebrowColor: Color = when (pick.kind) {
            TopPickKind.PERSONAL_STATION -> Color(0xFFFFD2DA)
            TopPickKind.DISCOVERY_STATION -> Color(0xFFCDBDFF)
            TopPickKind.MOOD -> Color(0xFFBFD8F5)
            else -> Color.White.copy(alpha = 0.62f)
        }
        when (pick.kind) {
            TopPickKind.PERSONAL_STATION -> GradientArt(StationColors)
            TopPickKind.DISCOVERY_STATION -> GradientArt(DiscoveryColors)
            TopPickKind.MOOD -> GradientArt(MoodColors)
            TopPickKind.ARTIST_AND_SIMILAR -> CollageArt(pick.images, CollageColors[Math.floorMod(pick.artistName.orEmpty().hashCode(), CollageColors.size)])
            TopPickKind.LATEST_RELEASE, TopPickKind.NEW_RELEASE_MIX, TopPickKind.FEATURED_ARTIST -> {
                AsyncImage(
                    model = pick.images.firstOrNull()?.resize(400, 400),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0.45f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.45f),
                                1f to Color.Black.copy(alpha = 0.82f),
                            ),
                        ),
                )
            }
        }
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(
                text = text.eyebrow,
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                color = eyebrowColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = text.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontSize = 17.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (text.subtitle.isNotBlank()) {
                Text(
                    text = text.subtitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 16.sp),
                    color = Color.White.copy(alpha = 0.86f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun GradientArt(colors: List<Color>) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors)),
    )
}

@Composable
private fun CollageArt(images: List<String>, colors: List<Color>) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(colors)),
        contentAlignment = Alignment.Center,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((-10).dp), verticalAlignment = Alignment.CenterVertically) {
            images.take(3).forEachIndexed { index, url ->
                AsyncImage(
                    model = url.resize(200, 200),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(if (index == 0) 110.dp else 84.dp)
                        .clip(CircleShape),
                )
            }
        }
    }
}
