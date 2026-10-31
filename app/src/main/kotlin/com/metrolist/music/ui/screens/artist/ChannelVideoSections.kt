/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.artist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.ui.utils.resize

private const val ChannelVideosPerPage = 5
private const val ChannelVideoPages = 3

/** Popular videos of a channel, split into three swipeable pages of five rows each. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelVideoPager(
    videos: List<SongItem>,
    onPlay: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = videos.take(ChannelVideosPerPage * ChannelVideoPages).chunked(ChannelVideosPerPage)
    if (pages.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { pages.size })
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxWidth().height((ChannelVideosPerPage * 64).dp),
    ) { page ->
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            pages[page].forEach { video ->
                ChannelVideoRow(video = video, onClick = { onPlay(video) })
            }
        }
    }
}

/** Videos from newest to oldest in a horizontal row, used when the channel has no playlists. */
@Composable
fun ChannelVideoCarousel(
    videos: List<SongItem>,
    onPlay: (SongItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        items(videos, key = { "channel_video_${it.id}" }) { video ->
            Column(modifier = Modifier.width(160.dp).padding(end = 12.dp).clickable { onPlay(video) }) {
                AsyncImage(
                    model = video.thumbnail.resize(320, 320),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(160.dp).clip(RoundedCornerShape(12.dp)),
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ChannelVideoRow(
    video: SongItem,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = video.thumbnail.resize(160, 160),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(56.dp).clip(RoundedCornerShape(6.dp)),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = video.artists.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
