/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.metrolist.innertube.models.PlaylistItem
import com.metrolist.innertube.models.SongItem
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.LocalSyncUtils
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.db.entities.Playlist
import com.metrolist.music.db.entities.PlaylistEntity
import com.metrolist.music.db.entities.PlaylistSongMap
import com.metrolist.music.models.toMediaMetadata
import com.metrolist.music.playback.queues.YouTubePlaylistQueue
import com.metrolist.music.ui.component.ExpandableText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.rememberArtworkColor
import com.metrolist.music.ui.component.YouTubeListItem
import com.metrolist.music.ui.menu.YouTubePlaylistMenu
import com.metrolist.music.ui.menu.YouTubeSelectionSongMenu
import com.metrolist.music.ui.menu.YouTubeSongMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.OnlinePlaylistViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.background
import com.metrolist.music.constants.UseAppleStyleLayoutKey

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val coroutineScope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isPodcastPlaylist = viewModel.isPodcastPlaylist

    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val lazyListState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs.mapIndexed { i, s -> i to s }
            } else {
                songs.mapIndexed { i, s -> i to s }.filter {
                    it.second.title.contains(query.text, true) ||
                        it.second.artists.fastAny { a -> a.name.contains(query.text, true) }
                }
            }
        }

    var inSelectMode by remember { mutableStateOf(false) }
    val selection =
        remember {
            mutableStateListOf<String>()
        }
    var selectionAnchorSongId by remember { mutableStateOf<String?>(null) }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
        selectionAnchorSongId = null
    }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) { if (isSearching) focusRequester.requestFocus() }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.second.id == songId } == null) {
                selection.remove(songId)
            }
        }

        if (selectionAnchorSongId != null && filteredSongs.none { it.second.id == selectionAnchorSongId }) {
            selectionAnchorSongId = filteredSongs.firstOrNull { it.second.id in selection }?.second?.id
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
        ) {
            if (playlist == null || songs.isEmpty()) {
                if (isLoading) {
                    item(key = "loading_placeholder") {
                        Box(
                            modifier =
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ContainedLoadingIndicator()
                        }
                    }
                } else if (error != null) {
                    item(key = "error_placeholder") {
                        Column(
                            modifier =
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = error ?: stringResource(R.string.error_unknown),
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            androidx.compose.material3.TextButton(onClick = { viewModel.retry() }) {
                                Text(stringResource(R.string.retry))
                            }
                        }
                    }
                } else if (!isLoading && songs.isEmpty()) {
                    item(key = "empty_placeholder") {
                        Box(
                            modifier =
                                Modifier
                                    .fillParentMaxSize()
                                    .padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = stringResource(R.string.playlist_is_empty),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                    }
                }
            } else {
                playlist?.let { playlist ->
                    if (!isSearching) {
                        item(key = "playlist_header") {
                            OnlinePlaylistHeaderWithStyle(
                                playlist = playlist,
                                songs = songs,
                                dbPlaylist = dbPlaylist,
                                coroutineScope = coroutineScope,
                                continuation = viewModel.continuation,
                                isPodcastPlaylist = isPodcastPlaylist,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    itemsIndexed(filteredSongs) { index, (_, songItem) ->
                        val onCheckedChange: (Boolean) -> Unit = {
                            if (it) {
                                selection.add(songItem.id)
                            } else {
                                selection.remove(songItem.id)
                            }
                        }

                        YouTubeListItem(
                            item = songItem,
                            isActive = mediaMetadata?.id == songItem.id,
                            isPlaying = isPlaying,
                            isSelected = inSelectMode && songItem.id in selection,
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        enabled = !hideExplicit || !songItem.explicit,
                                        onClick = {
                                            if (inSelectMode) {
                                                onCheckedChange(songItem.id !in selection)
                                            } else if (songItem.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    YouTubePlaylistQueue(
                                                        playlistId = playlist.id,
                                                        playlistTitle = playlist.title,
                                                        initialSongs = filteredSongs.map { it.second },
                                                        initialContinuation = viewModel.continuation,
                                                        startIndex = index,
                                                    ),
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            if (!inSelectMode) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                inSelectMode = true
                                                onCheckedChange(true)
                                                selectionAnchorSongId = songItem.id
                                            } else {
                                                val anchorIndex =
                                                    selectionAnchorSongId?.let { anchorSongId ->
                                                        filteredSongs.indexOfFirst { it.second.id == anchorSongId }
                                                    } ?: -1

                                                if (anchorIndex == -1) {
                                                    onCheckedChange(true)
                                                    selectionAnchorSongId = songItem.id
                                                } else {
                                                    val range = if (anchorIndex <= index) anchorIndex..index else index..anchorIndex
                                                    for (rangeIndex in range) {
                                                        val rangeSongId = filteredSongs[rangeIndex].second.id
                                                        if (rangeSongId !in selection) {
                                                            selection.add(rangeSongId)
                                                        }
                                                    }
                                                }
                                            }
                                        },
                                    ).animateItem(),
                            trailingContent = {
                                if (inSelectMode) {
                                    Checkbox(
                                        checked = songItem.id in selection,
                                        onCheckedChange = onCheckedChange,
                                    )
                                } else {
                                    IconButton(onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(songItem, menuState::dismiss)
                                        }
                                    }) {
                                        Icon(painterResource(R.drawable.more_vert), null)
                                    }
                                }
                            },
                        )
                    }

                    if (isLoadingMore) {
                        item(key = "loading_more") {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                ContainedLoadingIndicator()
                            }
                        }
                    }
                }
            }
        }

        TopAppBar(
            title = {
                if (inSelectMode) {
                    Text(
                        text =
                            if (isPodcastPlaylist) {
                                pluralStringResource(R.plurals.n_episode, selection.size, selection.size)
                            } else {
                                pluralStringResource(R.plurals.n_song, selection.size, selection.size)
                            },
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (lazyListState.firstVisibleItemIndex > 0) {
                    Text(playlist?.title ?: "")
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else if (inSelectMode) {
                            onExitSelectionMode()
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching && !inSelectMode) {
                            navController.backToMain()
                        }
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (inSelectMode) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (inSelectMode) {
                    Checkbox(
                        checked = selection.size == filteredSongs.size && selection.isNotEmpty(),
                        onCheckedChange = {
                            if (selection.size == filteredSongs.size) {
                                selection.clear()
                            } else {
                                selection.clear()
                                selection.addAll(filteredSongs.map { it.second.id })
                            }
                        },
                    )
                    IconButton(
                        enabled = selection.isNotEmpty(),
                        onClick = {
                            menuState.show {
                                YouTubeSelectionSongMenu(
                                    songSelection =
                                        filteredSongs
                                            .filter { it.second.id in selection }
                                            .map { it.second },
                                    onDismiss = menuState::dismiss,
                                    clearAction = onExitSelectionMode,
                                )
                            }
                        },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    IconButton(
                        onClick = { isSearching = true },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun OnlinePlaylistHeaderWithStyle(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    coroutineScope: CoroutineScope,
    continuation: String?,
    isPodcastPlaylist: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val appleStyle by rememberPreference(UseAppleStyleLayoutKey, defaultValue = false)
    if (!appleStyle) {
        OnlinePlaylistHeader(playlist, songs, dbPlaylist, coroutineScope, continuation, isPodcastPlaylist, modifier)
        return
    }
    // Apple style hero: background gradient from the artwork's dominant color
    val artColor by rememberArtworkColor(playlist.thumbnail?.resize(100, 100))
    val heroColor = artColor ?: MaterialTheme.colorScheme.surfaceVariant
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(heroColor, MaterialTheme.colorScheme.background),
                    ),
                ),
    ) {
        AppleOnlinePlaylistHeader(
            playlist, songs, dbPlaylist, coroutineScope, continuation, isPodcastPlaylist,
            Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun AppleOnlinePlaylistHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    coroutineScope: CoroutineScope,
    continuation: String?,
    isPodcastPlaylist: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val menuState = LocalMenuState.current
    val onColor = Color.White

    Column(
        modifier =
            modifier
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
                model = ImageRequest.Builder(LocalContext.current).data(playlist.thumbnail?.resize(1080, 1080)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = playlist.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = onColor,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        playlist.author?.let { author ->
            Text(
                text = author.name,
                style = MaterialTheme.typography.bodyLarge,
                color = onColor.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            if (author.id != null) {
                                navController.navigate("artist/${author.id}")
                            }
                        },
                    ),
            )
        }

        val totalDuration = songs.sumOf { it.duration ?: 0 }
        val nSongs = pluralStringResource(
            if (isPodcastPlaylist) R.plurals.n_episode else R.plurals.n_song,
            songs.size,
            songs.size,
        )
        val durationText = if (totalDuration > 0) makeTimeString(totalDuration * 1000L) else null
        Text(
            text = listOfNotNull(nSongs, durationText).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = onColor.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                onClick = {
                    if (!isListenTogetherGuest && songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            YouTubePlaylistQueue(
                                playlistId = playlist.id,
                                playlistTitle = playlist.title,
                                initialSongs = songs.shuffled(),
                                initialContinuation = continuation,
                            ),
                        )
                    }
                },
                shape = CircleShape,
                color = onColor.copy(alpha = 0.2f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                        tint = onColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Surface(
                onClick = {
                    if (!isListenTogetherGuest && songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            YouTubePlaylistQueue(
                                playlistId = playlist.id,
                                playlistTitle = playlist.title,
                                initialSongs = songs,
                                initialContinuation = continuation,
                            ),
                        )
                    }
                },
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
                        tint = Color(0xFF111111),
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.play),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111111),
                    )
                }
            }

            Surface(
                onClick = {
                    menuState.show {
                        YouTubePlaylistMenu(
                            playlist = playlist,
                            songs = songs,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                shape = CircleShape,
                color = onColor.copy(alpha = 0.2f),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        painter = painterResource(R.drawable.add),
                        contentDescription = null,
                        tint = onColor,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        val description = playlist.description
        if (!description.isNullOrBlank()) {
            ExpandableText(
                text = description,
                modifier = Modifier.fillMaxWidth(),
                collapsedMaxLines = 3,
            )
        }
    }
}

@Composable
private fun OnlinePlaylistHeader(
    playlist: PlaylistItem,
    songs: List<SongItem>,
    dbPlaylist: Playlist?,
    coroutineScope: CoroutineScope,
    continuation: String?,
    isPodcastPlaylist: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false
    val database = LocalDatabase.current
    val menuState = LocalMenuState.current
    val syncUtils = LocalSyncUtils.current

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            modifier =
                Modifier
                    .size(240.dp)
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(3.dp),
                        spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                    ),
            shape = RoundedCornerShape(3.dp),
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(playlist.thumbnail?.resize(1080, 1080)).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = playlist.title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Creator row - channel avatar + name centered
        val author = playlist.author
        if (author != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier =
                    Modifier.combinedClickable(
                        onClick = {
                            if (author.id != null) {
                                navController.navigate("artist/${author.id}")
                            }
                        },
                    ),
            ) {
                if (playlist.authorAvatarUrl != null) {
                    AsyncImage(
                        model = playlist.authorAvatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape),
                    )
                }
                Text(
                    text = author.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
        }

        // Metadata row - song count, duration
        val totalDuration = songs.sumOf { it.duration ?: 0 }
        val nSongs = pluralStringResource(
            if (isPodcastPlaylist) R.plurals.n_episode else R.plurals.n_song,
            songs.size,
            songs.size,
        )
        val durationText = if (totalDuration > 0) makeTimeString(totalDuration * 1000L) else null
        val metadataText = buildString {
            append(nSongs)
            if (durationText != null) {
                append(" ")
                append(durationText)
            }
        }
        Text(
            text = metadataText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 32.dp),
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Description
        val description = playlist.description
        if (!description.isNullOrBlank()) {
            ExpandableText(
                text = description,
                modifier = Modifier.padding(horizontal = 32.dp),
                collapsedMaxLines = 3,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Like Button - Smaller secondary button
            Surface(
                onClick = {
                    if (dbPlaylist != null) {
                        database.transaction {
                            val currentPlaylist = dbPlaylist.playlist
                            update(currentPlaylist, playlist)
                            update(currentPlaylist.toggleLike())
                        }
                    } else {
                        coroutineScope.launch(Dispatchers.IO) {
                            val playlistEntity =
                                PlaylistEntity(
                                    name = playlist.title,
                                    browseId = playlist.id,
                                    thumbnailUrl = playlist.thumbnail,
                                    isEditable = playlist.isEditable,
                                    remoteSongCount =
                                        playlist.songCountText?.let {
                                            Regex("""\d+""").find(it)?.value?.toIntOrNull()
                                        },
                                    playEndpointParams = playlist.playEndpoint?.params,
                                    shuffleEndpointParams = playlist.shuffleEndpoint?.params,
                                    radioEndpointParams = playlist.radioEndpoint?.params,
                                ).toggleLike()
                            val songMetadata = songs.map { it.toMediaMetadata() }
                            database.withTransaction {
                                insert(playlistEntity)
                                songMetadata.onEach { insert(it) }
                                val songIds = songMetadata.map { it.id to it.setVideoId }
                                val createdPlaylist =
                                    database.playlistBlocking(playlistEntity.id)
                                        ?: throw IllegalStateException("Failed to create playlist")
                                database.addSongsToPlaylist(createdPlaylist, songIds)
                            }
                        }
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (dbPlaylist?.playlist?.bookmarkedAt != null) R.drawable.favorite else R.drawable.favorite_border,
                            ),
                        contentDescription = null,
                        tint =
                            if (dbPlaylist?.playlist?.bookmarkedAt != null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            // Play Button - Larger primary circular button
            Surface(
                onClick = {
                    if (!isListenTogetherGuest && songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            YouTubePlaylistQueue(
                                playlistId = playlist.id,
                                playlistTitle = playlist.title,
                                initialSongs = songs,
                                initialContinuation = continuation,
                            ),
                        )
                    }
                },
                color = MaterialTheme.colorScheme.primary,
                shape = CircleShape,
                modifier = Modifier.size(72.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(32.dp),
                    )
                }
            }

            // Menu Button - Smaller secondary button
            Surface(
                onClick = {
                    menuState.show {
                        YouTubePlaylistMenu(
                            playlist = playlist,
                            songs = songs,
                            coroutineScope = coroutineScope,
                            onDismiss = menuState::dismiss,
                        )
                    }
                },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.size(48.dp),
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}
