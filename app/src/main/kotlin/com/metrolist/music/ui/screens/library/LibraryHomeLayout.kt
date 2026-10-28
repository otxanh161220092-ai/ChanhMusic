/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.LibraryFilter
import com.metrolist.music.constants.PauseListenHistoryKey
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.LibraryMixViewModel
import java.time.LocalDateTime

private data class RecentCollection(
    val title: String,
    val thumbnail: String?,
    val addedAt: LocalDateTime?,
    val route: String,
)

private data class LibraryCategory(
    val label: Int,
    val icon: Int,
    val filter: LibraryFilter,
)

private val libraryCategories =
    listOf(
        LibraryCategory(R.string.filter_playlists, R.drawable.queue_music, LibraryFilter.PLAYLISTS),
        LibraryCategory(R.string.filter_artists, R.drawable.artist, LibraryFilter.ARTISTS),
        LibraryCategory(R.string.filter_albums, R.drawable.album, LibraryFilter.ALBUMS),
        LibraryCategory(R.string.filter_songs, R.drawable.music_note, LibraryFilter.SONGS),
    )

/** Apple Music style library home: title, recent collections grid, category rows and recently added. */
@Composable
fun LibraryHomeLayout(
    navController: NavController,
    onFilterSelected: (LibraryFilter) -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
) {
    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()

    val recent = remember(albums, artists, playlists) {
        buildList {
            albums.forEach {
                add(RecentCollection(it.album.title, it.album.thumbnailUrl, it.album.bookmarkedAt, "album/${it.id}"))
            }
            artists.forEach {
                add(RecentCollection(it.artist.name, it.artist.thumbnailUrl, it.artist.bookmarkedAt, "artist/${it.id}"))
            }
            playlists.forEach {
                val route =
                    if (!it.playlist.isEditable && it.songCount == 0 && it.playlist.browseId != null) {
                        "online_playlist/${it.playlist.browseId}"
                    } else {
                        "local_playlist/${it.id}"
                    }
                val thumbnail = it.playlist.thumbnailUrl ?: it.songThumbnails.firstOrNull { url -> url != null }
                add(RecentCollection(it.playlist.name, thumbnail, it.playlist.createdAt, route))
            }
        }.sortedByDescending { it.addedAt ?: LocalDateTime.MIN }
    }

    var showMenu by remember { mutableStateOf(false) }
    val database = LocalDatabase.current
    val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
    val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
    val showHistoryButton = !(pauseListenHistory && eventCount == 0)

    LazyColumn(contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()) {
        item(key = "header") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.filter_library),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { navController.navigate("account") },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.person),
                        contentDescription = stringResource(R.string.account),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        if (showHistoryButton) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.history)) },
                                leadingIcon = {
                                    Icon(painter = painterResource(R.drawable.history), contentDescription = null)
                                },
                                onClick = {
                                    showMenu = false
                                    navController.navigate("history")
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.stats)) },
                            leadingIcon = {
                                Icon(painter = painterResource(R.drawable.stats), contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                navController.navigate("stats")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.together)) },
                            leadingIcon = {
                                Icon(painter = painterResource(R.drawable.group_outlined), contentDescription = null)
                            },
                            onClick = {
                                showMenu = false
                                navController.navigate("listen_together_from_topbar")
                            },
                        )
                    }
                }
            }
        }

        item(key = "grid") {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                recent.take(6).chunked(3).forEach { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { collection ->
                            RecentTile(
                                collection = collection,
                                modifier = Modifier.weight(1f),
                                onClick = { navController.navigate(collection.route) },
                            )
                        }
                        repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        item(key = "categories") {
            Spacer(Modifier.height(24.dp))
            libraryCategories.forEachIndexed { index, category ->
                LibraryCategoryRow(
                    label = category.label,
                    icon = category.icon,
                    onClick = { onFilterSelected(category.filter) },
                )
                if (index < libraryCategories.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
            }
        }

        item(key = "recent_title") {
            Text(
                text = stringResource(R.string.recently_added),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 12.dp),
            )
        }

        item(key = "recent_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(recent.take(12), key = { it.route }) { collection ->
                    RecentTile(
                        collection = collection,
                        modifier = Modifier.width(150.dp),
                        onClick = { navController.navigate(collection.route) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryCategoryRow(
    label: Int,
    icon: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.arrow_forward),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun RecentTile(
    collection: RecentCollection,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            if (collection.thumbnail != null) {
                AsyncImage(
                    model = collection.thumbnail.resize(400, 400),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center).size(40.dp),
                )
            }
        }
        Text(
            text = collection.title,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        )
    }
}
