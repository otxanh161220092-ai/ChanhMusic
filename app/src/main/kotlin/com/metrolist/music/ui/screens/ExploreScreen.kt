/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import com.metrolist.music.ui.component.smoothCornerShape

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.playback.queues.ListQueue
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.tabScreenContentPadding
import com.metrolist.music.utils.joinToArtistString
import com.metrolist.music.viewmodels.HomeViewModel

/** Explore tab: mood and genre shortcuts, daily discover and new releases. */
@Composable
fun ExploreScreen(viewModel: HomeViewModel = hiltViewModel()) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val explorePage by viewModel.explorePage.collectAsStateWithLifecycle()
    val dailyDiscover by viewModel.dailyDiscover.collectAsStateWithLifecycle()

    val moods =
        explorePage
            ?.moodAndGenres
            .orEmpty()
            .distinctBy { "${it.title}_${it.endpoint.browseId}_${it.endpoint.params}" }
    val releases = explorePage?.newReleaseAlbums.orEmpty()
    val discover = dailyDiscover.orEmpty().map { it.recommendation }.distinctBy { it.id }
    val discoverSongs = discover.filterIsInstance<SongItem>()
    val discoverTitle = stringResource(R.string.your_daily_discover)

    LazyColumn(contentPadding = tabScreenContentPadding()) {
        item(key = "explore_mood_title") {
            NavigationTitle(
                title = stringResource(R.string.mood_and_genres),
                onClick = { navController.navigate("mood_and_genres") },
            )
        }
        item(key = "explore_mood_grid") {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                moods.take(8).chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { mood ->
                            com.metrolist.music.ui.screens.MoodAndGenresButton(
                                title = mood.title,
                                onClick = {
                                    navController.navigate("youtube_browse/${mood.endpoint.browseId}?params=${mood.endpoint.params}")
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }

        if (discoverSongs.isNotEmpty()) {
            item(key = "explore_discover_header") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.your_daily_discover),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .clip(smoothCornerShape(50))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .clickable {
                                    playerConnection.playQueue(
                                        ListQueue(
                                            title = discoverTitle,
                                            items = discoverSongs.map { it.toMediaItem() },
                                        ),
                                    )
                                }.padding(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        Text(text = stringResource(R.string.play_all), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            items(discoverSongs, key = { "explore_discover_${it.id}" }) { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                playerConnection.playQueue(ListQueue(title = song.title, items = listOf(song.toMediaItem())))
                            }.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    AsyncImage(
                        model = song.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(smoothCornerShape(8.dp)),
                    )
                    Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(text = song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            text = song.artists.joinToArtistString(", ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (releases.isNotEmpty()) {
            item(key = "explore_releases_title") {
                NavigationTitle(
                    title = stringResource(R.string.new_release_albums),
                    onClick = { navController.navigate("new_release") },
                )
            }
            item(key = "explore_releases_row") {
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(releases, key = { "explore_release_${it.id}" }) { album ->
                        Column(
                            modifier =
                                Modifier
                                    .width(160.dp)
                                    .clickable { navController.navigate("album/${album.id}") },
                        ) {
                            AsyncImage(
                                model = album.thumbnail,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(160.dp).clip(smoothCornerShape(16.dp)),
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(text = album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = album.artists.orEmpty().joinToArtistString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
