/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens

import com.metrolist.music.ui.component.appleSubtitleColor
import com.metrolist.music.models.toMediaMetadata
import androidx.compose.material3.LocalContentColor
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.clickable
import com.metrolist.music.ui.component.GradientTopAppBar

import com.metrolist.music.ui.component.smoothCornerShape

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachReversed
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.metrolist.music.LocalDatabase
import com.metrolist.music.bcmix.canvas.CanvasArtwork
import com.metrolist.music.topPicks.AppleAlbumCanvas
import com.metrolist.music.topPicks.AppleAlbumDetails
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.metrolist.music.LocalDownloadUtil
import com.metrolist.music.LocalListenTogetherManager
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.R
import com.metrolist.music.constants.HideExplicitKey
import com.metrolist.music.constants.HideVideoSongsKey
import com.metrolist.music.db.entities.Album
import com.metrolist.music.playback.queues.LocalAlbumRadio
import com.metrolist.music.ui.component.AppleCollectionContainer
import com.metrolist.music.ui.component.appleCollectionContentPadding
import com.metrolist.music.ui.component.appleTopBarColors
import com.metrolist.music.ui.component.AppleCollectionHeader
import com.metrolist.music.ui.component.ClickableArtistText
import com.metrolist.music.ui.component.IconButton
import com.metrolist.music.ui.component.LocalMenuState
import com.metrolist.music.ui.component.NavigationTitle
import com.metrolist.music.ui.component.AppleTrackRow
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.extensions.toMediaItem
import com.metrolist.music.ui.component.AppleTopBarActionButtons
import com.metrolist.music.ui.component.FloatingBackState
import com.metrolist.music.ui.component.SongListItem
import com.metrolist.music.ui.component.YouTubeGridItem
import com.metrolist.music.ui.menu.AlbumMenu
import com.metrolist.music.ui.menu.SelectionSongMenu
import com.metrolist.music.ui.menu.SongMenu
import com.metrolist.music.ui.menu.YouTubeAlbumMenu
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.ui.utils.resize
import com.metrolist.music.utils.makeTimeString
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.viewmodels.AlbumViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val appleStyle = true
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val listenTogetherManager = LocalListenTogetherManager.current
    val isListenTogetherGuest = listenTogetherManager?.let { it.isInRoom && !it.isHost } ?: false

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlistId by viewModel.playlistId.collectAsStateWithLifecycle()
    val albumWithSongs by viewModel.albumWithSongs.collectAsStateWithLifecycle()
    val otherVersions by viewModel.otherVersions.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)
    val hideVideoSongs by rememberPreference(key = HideVideoSongsKey, defaultValue = false)

    val filteredSongs =
        remember(albumWithSongs, hideExplicit, hideVideoSongs) {
            var songs = albumWithSongs?.songs ?: emptyList()
            if (hideExplicit) {
                songs = songs.filter { !it.song.explicit }
            }
            if (hideVideoSongs) {
                songs = songs.filter { !it.song.isVideo }
            }
            songs
        }

    var inSelectMode by rememberSaveable { mutableStateOf(false) }
    val selection =
        rememberSaveable(
            saver =
                listSaver<MutableList<String>, String>(
                    save = { it.toList() },
                    restore = { it.toMutableStateList() },
                ),
        ) { mutableStateListOf() }
    val onExitSelectionMode = {
        inSelectMode = false
        selection.clear()
    }
    if (inSelectMode) {
        BackHandler(onBack = onExitSelectionMode)
    }

    LaunchedEffect(filteredSongs) {
        selection.fastForEachReversed { songId ->
            if (filteredSongs.find { it.id == songId } == null) {
                selection.remove(songId)
            }
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloadState by remember {
        mutableIntStateOf(Download.STATE_STOPPED)
    }

    LaunchedEffect(albumWithSongs) {
        val songs = albumWithSongs?.songs?.map { it.id }
        if (songs.isNullOrEmpty()) return@LaunchedEffect
        downloadUtil.downloads.collect { downloads ->
            downloadState =
                if (songs.all { downloads[it]?.state == Download.STATE_COMPLETED }) {
                    Download.STATE_COMPLETED
                } else if (songs.all {
                        downloads[it]?.state == Download.STATE_QUEUED ||
                            downloads[it]?.state == Download.STATE_DOWNLOADING ||
                            downloads[it]?.state == Download.STATE_COMPLETED
                    }
                ) {
                    Download.STATE_DOWNLOADING
                } else {
                    Download.STATE_STOPPED
                }
        }
    }

    // Apple's album motion artwork, looked up once per album. Its 3:4 rendition
    // fills the same portrait frame the still cover does; null keeps the still.
    val albumMotion by produceState<CanvasArtwork?>(null, albumWithSongs?.album?.title, appleStyle) {
        value = null
        val current = albumWithSongs ?: return@produceState
        if (!appleStyle) return@produceState
        val artist = current.artists.joinToString(", ") { it.name }
        val clip = withContext(Dispatchers.IO) {
            AppleAlbumCanvas.searchAlbum(current.album.title, artist)
        } ?: return@produceState
        value = clip.toPlayerClip().let { player ->
            clip.portraitUrl?.let { player.copy(url = it, fallbackUrl = clip.url) } ?: player
        }
    }

    // Genre, year, lossless and the editorial description from Apple, looked up once per album.
    val albumDetails by produceState<AppleAlbumDetails?>(null, albumWithSongs?.album?.title, appleStyle) {
        value = null
        val current = albumWithSongs ?: return@produceState
        if (!appleStyle) return@produceState
        val artist = current.artists.joinToString(", ") { it.name }
        value = withContext(Dispatchers.IO) { AppleAlbumCanvas.albumDetails(current.album.title, artist) }
    }

    // Apple-style footer: the artist's music videos and more albums by the same artist.
    val footerArtistId = albumWithSongs?.artists?.firstOrNull()?.id
    val footerArtistName = albumWithSongs?.artists?.firstOrNull()?.name
    val footerVideoTileWidth = (LocalConfiguration.current.screenWidthDp * 0.82f).dp
    val artistPage by produceState<com.metrolist.innertube.pages.ArtistPage?>(null, footerArtistId, appleStyle) {
        value = null
        val artistId = footerArtistId ?: return@produceState
        if (!appleStyle) return@produceState
        value = withContext(Dispatchers.IO) { com.metrolist.innertube.YouTube.artist(artistId).getOrNull() }
    }

    AppleCollectionContainer(thumbnailUrl = albumWithSongs?.album?.thumbnailUrl, enabled = appleStyle) {
    LazyColumn(
        contentPadding = appleCollectionContentPadding(LocalPlayerAwareWindowInsets.current.asPaddingValues(), appleStyle),
    ) {
        val albumWithSongs = albumWithSongs
        if (albumWithSongs != null && albumWithSongs.songs.isNotEmpty() && appleStyle) {
            item(key = "album_header_apple") {
                val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
                val songCount =
                    pluralStringResource(
                        R.plurals.n_song,
                        albumWithSongs.songs.size,
                        albumWithSongs.songs.size,
                    )
                val metaText =
                    if (totalDuration > 0) "$songCount ${makeTimeString(totalDuration * 1000L)}" else songCount
                run {
                    AppleCollectionHeader(
                        title = albumWithSongs.album.title,
                        thumbnailUrl = albumWithSongs.album.thumbnailUrl,
                        motion = albumMotion,
                        artistText = albumWithSongs.artists.joinToString(", ") { it.name },
                        metaText = metaText,
                        detailsText =
                            listOfNotNull(albumDetails?.genre, albumDetails?.year ?: albumWithSongs.album.year?.toString())
                                .joinToString(" • ")
                                .ifBlank { null },
                        lossless = albumDetails?.lossless == true,
                        description = albumDetails?.description,
                        onPlay = {
                            if (!isListenTogetherGuest) {
                                playerConnection.service.getAutomix(playlistId)
                                playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                            }
                        },
                        onDownload = {
                            albumWithSongs.songs.forEach { downloadUtil.download(it) }
                        },
                        onShuffle = {
                            albumWithSongs.album.playlistId?.let { playerConnection.service.getAutomix(it) }
                            playerConnection.playQueue(
                                com.metrolist.music.playback.queues.ListQueue(
                                    title = albumWithSongs.album.title,
                                    items = albumWithSongs.songs.shuffled().map { it.toMediaItem() },
                                ),
                            )
                        },
                        onMore = {
                            menuState.show {
                                AlbumMenu(
                                    originalAlbum = Album(albumWithSongs.album, albumWithSongs.artists),
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                    )
                }
            }
        }

        if (albumWithSongs != null && albumWithSongs.songs.isNotEmpty()) {
            if (!appleStyle) item(key = "album_header") {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Album Thumbnail - Large centered with shadow
                    Surface(
                        modifier =
                            Modifier
                                .size(240.dp)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = smoothCornerShape(3.dp),
                                    spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                ),
                        shape = smoothCornerShape(3.dp),
                    ) {
                        AsyncImage(
                            model = albumWithSongs.album.thumbnailUrl?.resize(1080, 1080),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Album Name
                    Text(
                        text = albumWithSongs.album.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Artist Names - Below the album name
                    ClickableArtistText(
                        artists = albumWithSongs.artists,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                        ),
                        color = MaterialTheme.colorScheme.secondary,
                        maxLines = Int.MAX_VALUE,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Metadata - Year first, then song count • duration
                    val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        // Year
                        if (albumWithSongs.album.year != null) {
                            Text(
                                text = albumWithSongs.album.year.toString(),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            )
                        }

                        // Song Count • Duration
                        Text(
                            text =
                                buildString {
                                    append(
                                        pluralStringResource(
                                            R.plurals.n_song,
                                            albumWithSongs.songs.size,
                                            albumWithSongs.songs.size,
                                        ),
                                    )
                                    if (totalDuration > 0) {
                                        append(" ")
                                        append(makeTimeString(totalDuration * 1000L))
                                    }
                                },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Action Buttons Row
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
                                database.query {
                                    update(albumWithSongs.album.toggleLike())
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
                                            if (albumWithSongs.album.bookmarkedAt !=
                                                null
                                            ) {
                                                R.drawable.favorite
                                            } else {
                                                R.drawable.favorite_border
                                            },
                                        ),
                                    contentDescription = null,
                                    tint =
                                        if (albumWithSongs.album.bookmarkedAt != null) {
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
                                if (!isListenTogetherGuest) {
                                    playerConnection.service.getAutomix(playlistId)
                                    playerConnection.playQueue(
                                        LocalAlbumRadio(albumWithSongs),
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
                                    AlbumMenu(
                                        originalAlbum =
                                            Album(
                                                albumWithSongs.album,
                                                albumWithSongs.artists,
                                            ),
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

            if (filteredSongs.isNotEmpty()) {
                itemsIndexed(
                    items = filteredSongs,
                    key = { index, song -> "${song.id}_$index" },
                ) { index, song ->
                    val onCheckedChange: (Boolean) -> Unit = {
                        if (it) {
                            selection.add(song.id)
                        } else {
                            selection.remove(song.id)
                        }
                    }

                    val rowModifier =
                            Modifier
                                .fillMaxWidth()
                                .animateItem()
                                .combinedClickable(
                                    onClick = {
                                        if (inSelectMode) {
                                            onCheckedChange(song.id !in selection)
                                        } else if (!isListenTogetherGuest) {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.togglePlayPause()
                                            } else {
                                                playerConnection.service.getAutomix(playlistId)
                                                playerConnection.playQueue(
                                                    LocalAlbumRadio(albumWithSongs, startIndex = index),
                                                )
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!inSelectMode) {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            inSelectMode = true
                                            onCheckedChange(true)
                                        }
                                    },
                                )
                    val rowTrailing: @Composable () -> Unit = {
                            if (inSelectMode) {
                                Checkbox(
                                    checked = song.id in selection,
                                    onCheckedChange = onCheckedChange,
                                )
                            } else {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = null,
                                    )
                                }
                            }
                    }
                    if (appleStyle) {
                        AppleTrackRow(
                            index = index + 1,
                            song = song,
                            isActive = song.id == mediaMetadata?.id,
                            popular = albumDetails?.popular?.contains(AppleAlbumCanvas.trackKey(song.song.title)) == true,
                            modifier = rowModifier,
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = makeTimeString(song.song.duration.toLong() * 1000L),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.White.copy(alpha = 0.7f),
                                        textAlign = androidx.compose.ui.text.style.TextAlign.End,
                                        modifier = Modifier.width(44.dp),
                                    )
                                    rowTrailing()
                                }
                            },
                        )
                    } else {
                        SongListItem(
                            song = song,
                            albumIndex = index + 1,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            trailingContent = { rowTrailing() },
                            modifier = rowModifier,
                        )
                    }
                }
            }

            if (appleStyle && albumWithSongs != null && albumDetails != null) {
                val details = albumDetails!!
                val totalSeconds = albumWithSongs.songs.sumOf { it.song.duration }
                val hours = totalSeconds / 3600
                val minutes = (totalSeconds % 3600) / 60
                val durationText = if (hours > 0) "$hours hour${if (hours > 1) "s" else ""} $minutes minutes" else "$minutes minutes"
                val dateText = details.releaseDateIso?.let { iso ->
                    runCatching {
                        java.time.LocalDate.parse(iso).format(
                            java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", java.util.Locale.ENGLISH),
                        )
                    }.getOrNull()
                }
                item(key = "apple_album_info") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (details.masteredForItunes) {
                            Text(
                                text = "Apple Digital Master",
                                style = MaterialTheme.typography.bodyMedium,
                                color = appleSubtitleColor(),
                            )
                        }
                        dateText?.let {
                            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = appleSubtitleColor())
                        }
                        Text(
                            text = "${albumWithSongs.songs.size} songs, $durationText",
                            style = MaterialTheme.typography.bodyMedium,
                            color = appleSubtitleColor(),
                        )
                        details.copyright?.let { line ->
                            Text(
                                text = if (line.startsWith("℗") || line.startsWith("©")) line else "℗ $line",
                                style = MaterialTheme.typography.bodyMedium,
                                color = appleSubtitleColor(),
                            )
                        }
                    }
                }
            }
            if (appleStyle && albumWithSongs != null) {
                val pageItems = artistPage?.sections?.flatMap { it.items }.orEmpty()
                val videos = pageItems.filterIsInstance<com.metrolist.innertube.models.SongItem>()
                    .filter { it.musicVideoType != null }
                    .distinctBy { it.id }
                val moreAlbums = pageItems.filterIsInstance<com.metrolist.innertube.models.AlbumItem>()
                    .filter { it.id != albumWithSongs.album.id }
                    .distinctBy { it.id }
                if (videos.isNotEmpty()) {
                    item(key = "apple_footer_videos") {
                        Column(modifier = Modifier.padding(top = 24.dp)) {
                            Text(
                                text = "Music Videos",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = LocalContentColor.current,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            LazyRow(
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(0.dp),
) {
                                items(items = videos, key = { "footer_video_${it.id}" }) { video ->
                                    Column(
                                        modifier = Modifier
                                            .width(footerVideoTileWidth)
                                            .clickable {
                                                playerConnection.playQueue(
                                                    com.metrolist.music.playback.queues.YouTubeQueue(
                                                        com.metrolist.innertube.models.WatchEndpoint(videoId = video.id),
                                                        video.toMediaMetadata(),
                                                    ),
                                                )
                                            },
                                    ) {
                                        AsyncImage(
                                            model = video.thumbnail,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .aspectRatio(16f / 9f)
                                                .clip(smoothCornerShape(10.dp)),
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = video.title,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            text = video.artists.joinToString(" & ") { it.name },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = appleSubtitleColor(),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                if (moreAlbums.isNotEmpty()) {
                    item(key = "apple_footer_more") {
                        Column(modifier = Modifier.padding(top = 24.dp)) {
                            Text(
                                text = "More by ${footerArtistName.orEmpty()}",
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = LocalContentColor.current,
                                modifier = Modifier.padding(horizontal = 24.dp),
                            )
                            LazyRow(
    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
    horizontalArrangement = Arrangement.spacedBy(0.dp),
) {
                                items(items = moreAlbums, key = { "footer_album_${it.id}" }) { other ->
                                    YouTubeGridItem(
                                        item = other,
                                        isActive = false,
                                        isPlaying = false,
                                        thumbnailRatio = 1f,
                                        modifier = Modifier
                                            .clickable { navController.navigate("album/${other.id}") },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (otherVersions.isNotEmpty()) {
                item(key = "other_versions_title") {
                    NavigationTitle(
                        title = stringResource(R.string.other_versions),
                        modifier = Modifier.animateItem(),
                    )
                }
                item(key = "other_versions_list") {
                    LazyRow(
                        contentPadding = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues(),
                    ) {
                        items(
                            items = otherVersions.distinctBy { it.id },
                            key = { "album_other_${it.id}" },
                        ) { item ->
                            YouTubeGridItem(
                                item = item,
                                isActive = mediaMetadata?.album?.id == item.id,
                                isPlaying = isPlaying,
                                coroutineScope = scope,
                                modifier =
                                    Modifier
                                        .combinedClickable(
                                            onClick = { navController.navigate("album/${item.id}") },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                menuState.show {
                                                    YouTubeAlbumMenu(
                                                        albumItem = item,
                                                        onDismiss = menuState::dismiss,
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }
                    }
                }
            }
        } else {
            item(key = "loading") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            }
        }
    }
    }

    val appleActionsReady = appleStyle && !inSelectMode && albumWithSongs != null
    SideEffect {
        FloatingBackState.floatingActions =
            if (appleActionsReady) {
                {
                    AppleTopBarActionButtons(
                        isInLibrary = albumWithSongs?.album?.bookmarkedAt != null,
                        onAdd = {
                            albumWithSongs?.album?.let { current -> database.query { update(current.toggleLike()) } }
                        },
                        onMore = {
                            albumWithSongs?.let { current ->
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum = Album(current.album, current.artists),
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            }
                        },
                    )
                }
            } else {
                null
            }
    }
    DisposableEffect(Unit) {
        onDispose { FloatingBackState.floatingActions = null }
    }

    GradientTopAppBar(
        title = {
            if (inSelectMode) {
                Text(pluralStringResource(R.plurals.n_selected, selection.size, selection.size))
            } else if (!appleStyle) {
                Text(
                    text = albumWithSongs?.album?.title.orEmpty(),
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        navigationIcon = {
            com.metrolist.music.ui.component.OriginalBackSlot {
            if (inSelectMode) {
                IconButton(onClick = onExitSelectionMode) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                    )
                }
            } else {
                IconButton(
                    onClick = { navController.navigateUp() },
                    onLongClick = { navController.backToMain() },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            }
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
                            selection.addAll(filteredSongs.map { it.id })
                        }
                    },
                )
                IconButton(
                    enabled = selection.isNotEmpty(),
                    onClick = {
                        menuState.show {
                            SelectionSongMenu(
                                songSelection =
                                    selection.mapNotNull { songId ->
                                        filteredSongs.find { it.id == songId }
                                    },
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
            }
        },
        colors = appleTopBarColors(appleStyle, albumWithSongs?.album?.thumbnailUrl),
    )
}
