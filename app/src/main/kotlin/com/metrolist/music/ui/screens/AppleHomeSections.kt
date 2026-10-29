/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.db.entities.LocalItem
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.utils.resize

/**
 * Apple Music home layout: "Lựa chọn hay nè" cards in a row, then a "Recently Played" row of tiles
 * with a chevron that expands the tiles into a grid.
 */
fun LazyListScope.appleHomeSections(
    quickPicks: List<Song>?,
    recentlyPlayed: List<LocalItem>?,
    expandedRecent: Boolean,
    onToggleRecent: () -> Unit,
    onPlayTopPicks: (Int) -> Unit,
    recentlyPlayedItem: @Composable (LocalItem) -> Unit,
) {
    val picks = quickPicks.orEmpty().distinctBy { it.id }
    if (picks.isNotEmpty()) {
        item(key = "apple_top_picks_title") {
            SectionTitle(title = "Lựa chọn hay nè", onClick = null)
        }
        item(key = "apple_top_picks_list") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp)) {
                itemsIndexed(
                    items = picks,
                    key = { _, song -> "apple_top_pick_${song.id}" },
                ) { index, song ->
                    TopPickCard(
                        song = song,
                        colors = if (index % 2 == 0) PickRed else PickBlue,
                        onClick = { onPlayTopPicks(index) },
                        modifier = Modifier.width(200.dp).height(260.dp).padding(end = 12.dp),
                    )
                }
            }
        }
    }

    val recent = recentlyPlayed.orEmpty().distinctBy { it.id }
    if (recent.isNotEmpty()) {
        item(key = "apple_recent_title") {
            SectionTitle(title = "Recently Played", onClick = onToggleRecent, expanded = expandedRecent)
        }
        if (expandedRecent) {
            recent.chunked(2).forEachIndexed { rowIndex, rowItems ->
                item(key = "apple_recent_grid_$rowIndex") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        rowItems.forEach { item ->
                            Box(modifier = Modifier.weight(1f).padding(bottom = 12.dp)) {
                                recentlyPlayedItem(item)
                            }
                        }
                        if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        } else {
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
}

@Composable
private fun SectionTitle(
    title: String,
    onClick: (() -> Unit)?,
    expanded: Boolean = false,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)
                .clickable(enabled = onClick != null) { onClick?.invoke() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        if (onClick != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(if (expanded) R.drawable.expand_less else R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private val PickRed = Brush.linearGradient(listOf(Color(0xFFF0475C), Color(0xFFB3152C)))
private val PickBlue = Brush.linearGradient(listOf(Color(0xFF5B8CF0), Color(0xFF23348F)))

@Composable
private fun TopPickCard(
    song: Song,
    colors: Brush,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(20.dp))
                .background(colors)
                .clickable(onClick = onClick),
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
                fontSize = 22.sp,
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
