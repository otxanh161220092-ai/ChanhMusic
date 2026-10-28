/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.utils.resize

/**
 * Apple Music home layout: two large "Top Picks" cards side by side, then a "Recently Played"
 * row of square tiles with captions.
 */
fun LazyListScope.appleHomeSections(
    quickPicks: List<Song>?,
    recentlyPlayed: List<LocalItem>?,
    recentlyPlayedItem: @Composable (LocalItem) -> Unit,
) {
    val picks = quickPicks.orEmpty().distinctBy { it.id }.take(2)
    if (picks.isNotEmpty()) {
        item(key = "apple_top_picks_title") {
            Text(
                text = "Top Picks for You",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            )
        }
        item(key = "apple_top_picks_list") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(
                    items = picks,
                    key = { "apple_top_pick_${it.id}" },
                ) { song ->
                    TopPickCard(
                        song = song,
                        colors = if (song == picks.first()) PickRed else PickBlue,
                        modifier = Modifier.width(200.dp).height(260.dp).padding(end = 12.dp),
                    )
                }
            }
        }
    }

    val recent = recentlyPlayed.orEmpty().distinctBy { it.id }
    if (recent.isNotEmpty()) {
        item(key = "apple_recent_title") {
            NavigationTitle(title = "Recently Played")
        }
        item(key = "apple_recent_list") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                items(
                    items = recent,
                    key = { "apple_recent_${it.id}" },
                ) { item ->
                    Box(modifier = Modifier.width(150.dp).padding(end = 12.dp)) {
                        recentlyPlayedItem(item)
                    }
                }
            }
        }
    }
}

private val PickRed = Brush.linearGradient(listOf(Color(0xFFF0475C), Color(0xFFB3152C)))
private val PickBlue = Brush.linearGradient(listOf(Color(0xFF5B8CF0), Color(0xFF23348F)))

@Composable
private fun TopPickCard(
    song: Song,
    colors: Brush,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors),
    ) {
        AsyncImage(
            model = song.thumbnailUrl?.resize(400, 400),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Text(
                text = song.title,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = song.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
