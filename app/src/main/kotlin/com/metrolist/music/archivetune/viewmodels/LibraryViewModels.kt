/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalCoroutinesApi::class)

package com.metrolist.music.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.metrolist.music.archivetune.R
import com.metrolist.music.archivetune.constants.AiApiKeyKey
import com.metrolist.music.archivetune.constants.AiApiValidationStatus
import com.metrolist.music.archivetune.constants.AiApiValidationStatusKey
import com.metrolist.music.archivetune.constants.AiCustomEndpointKey
import com.metrolist.music.archivetune.constants.AiProvider
import com.metrolist.music.archivetune.constants.AiProviderKey
import com.metrolist.music.archivetune.constants.AlbumFilter
import com.metrolist.music.archivetune.constants.AlbumFilterKey
import com.metrolist.music.archivetune.constants.AlbumSortDescendingKey
import com.metrolist.music.archivetune.constants.AlbumSortType
import com.metrolist.music.archivetune.constants.AlbumSortTypeKey
import com.metrolist.music.archivetune.constants.ArtistFilter
import com.metrolist.music.archivetune.constants.ArtistFilterKey
import com.metrolist.music.archivetune.constants.ArtistSongSortDescendingKey
import com.metrolist.music.archivetune.constants.ArtistSongSortType
import com.metrolist.music.archivetune.constants.ArtistSongSortTypeKey
import com.metrolist.music.archivetune.constants.ArtistSortDescendingKey
import com.metrolist.music.archivetune.constants.ArtistSortType
import com.metrolist.music.archivetune.constants.ArtistSortTypeKey
import com.metrolist.music.archivetune.constants.HideExplicitKey
import com.metrolist.music.archivetune.constants.HideVideoKey
import com.metrolist.music.archivetune.constants.LibraryFilter
import com.metrolist.music.archivetune.constants.PlaylistSortDescendingKey
import com.metrolist.music.archivetune.constants.PlaylistSortType
import com.metrolist.music.archivetune.constants.PlaylistSortTypeKey
import com.metrolist.music.archivetune.constants.SongFilter
import com.metrolist.music.archivetune.constants.SongFilterKey
import com.metrolist.music.archivetune.constants.SongSortDescendingKey
import com.metrolist.music.archivetune.constants.SongSortType
import com.metrolist.music.archivetune.constants.SongSortTypeKey
import com.metrolist.music.archivetune.constants.TopSize
import com.metrolist.music.archivetune.db.MusicDatabase
import com.metrolist.music.archivetune.db.entities.Playlist
import com.metrolist.music.archivetune.db.entities.Song
import com.metrolist.music.archivetune.extensions.filterExplicit
import com.metrolist.music.archivetune.extensions.filterExplicitAlbums
import com.metrolist.music.archivetune.extensions.filterVideo
import com.metrolist.music.archivetune.extensions.reversed
import com.metrolist.music.archivetune.extensions.toEnum
import com.metrolist.music.archivetune.innertube.YouTube
import com.metrolist.music.archivetune.library.LibrarySyncFailure
import com.metrolist.music.archivetune.library.LibrarySyncTarget
import com.metrolist.music.archivetune.library.LibraryTopMix
import com.metrolist.music.archivetune.library.ObserveLibraryTopMixesUseCase
import com.metrolist.music.archivetune.library.RefreshLibraryResult
import com.metrolist.music.archivetune.library.RefreshLibraryUseCase
import com.metrolist.music.archivetune.library.RefreshLibraryTopMixesResult
import com.metrolist.music.archivetune.library.RefreshLibraryTopMixesUseCase
import com.metrolist.music.archivetune.library.TopMixGenerationFailure
import com.metrolist.music.archivetune.models.MediaMetadata
import com.metrolist.music.archivetune.models.toMediaMetadata
import com.metrolist.music.archivetune.playback.DownloadUtil
import com.metrolist.music.archivetune.utils.dataStore
import com.metrolist.music.archivetune.utils.get
import com.metrolist.music.archivetune.utils.isLocalMediaId
import com.metrolist.music.archivetune.utils.reportException
import java.text.Collator
import java.time.Duration
import java.time.LocalDateTime
import java.util.Locale
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException

private const val MOST_PLAYED_ALBUM_WINDOW_MILLIS = 14L * 24L * 60L * 60L * 1000L

@Immutable
sealed interface LibraryRefreshState {
    data object Loading : LibraryRefreshState

    data object Success : LibraryRefreshState

    data object Empty : LibraryRefreshState

    data class Error(val failure: LibrarySyncFailure) : LibraryRefreshState
}

abstract class LibraryRefreshViewModel(
    private val refreshLibraryUseCase: RefreshLibraryUseCase,
) : ViewModel() {
    private val _refreshState = MutableStateFlow<LibraryRefreshState>(LibraryRefreshState.Empty)
    val refreshState = _refreshState.asStateFlow()
    @Volatile
    private var refreshJob: Job? = null
    @Volatile
    private var activeTarget: LibrarySyncTarget? = null
    @Volatile
    private var refreshGeneration = 0L

    protected fun refreshLibrary(target: LibrarySyncTarget) {
        if (activeTarget == target && refreshJob?.isActive == true) return

        refreshJob?.cancel()
        activeTarget = target
        val generation = ++refreshGeneration
        _refreshState.value = LibraryRefreshState.Loading
        refreshJob =
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val result = refreshLibraryUseCase(target)
                    if (generation == refreshGeneration) {
                        _refreshState.value = when (result) {
                            RefreshLibraryResult.Success -> LibraryRefreshState.Success
                            is RefreshLibraryResult.Failure -> LibraryRefreshState.Error(result.reason)
                        }
                    }
                } catch (e: CancellationException) {
                    if (generation == refreshGeneration) {
                        _refreshState.value = LibraryRefreshState.Empty
                    }
                    throw e
                } finally {
                    if (generation == refreshGeneration) {
                        refreshJob = null
                        activeTarget = null
                    }
                }
            }
    }

    fun onRefreshErrorShown() {
        if (_refreshState.value is LibraryRefreshState.Error) {
            _refreshState.value = LibraryRefreshState.Empty
        }
    }
}

@HiltViewModel
class LibrarySongsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        refreshLibrary: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {

        val allSongs =
            context.dataStore.data
                .map {
                    Triple(
                        Triple(
                            it[SongFilterKey].toEnum(SongFilter.LIKED),
                            it[SongSortTypeKey].toEnum(SongSortType.CREATE_DATE),
                            (it[SongSortDescendingKey] ?: true),
                        ),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filterSort, hideExplicit, hideVideo) ->
                    val (filter, sortType, descending) = filterSort
                    when (filter) {
                        SongFilter.LIBRARY -> {
                            database.songs(sortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                        }

                        SongFilter.LIKED -> {
                            database.likedSongs(sortType, descending, hideVideo).map { it.filterExplicit(hideExplicit) }
                        }

                        SongFilter.DOWNLOADED -> {
                            downloadUtil.downloads.flatMapLatest { downloads ->
                                database
                                    .allSongs()
                                    .flowOn(Dispatchers.IO)
                                    .map { songs ->
                                        songs.filter { song: Song ->
                                            downloads[song.id]?.state == Download.STATE_COMPLETED
                                        }
                                    }.map { songs ->
                                        when (sortType) {
                                            SongSortType.CREATE_DATE -> {
                                                songs.sortedBy { song: Song ->
                                                    downloads[song.id]?.updateTimeMs ?: 0L
                                                }
                                            }

                                            SongSortType.NAME -> {
                                                songs.sortedBy { song: Song -> song.song.title }
                                            }

                                            SongSortType.ARTIST -> {
                                                val collator =
                                                    Collator.getInstance(Locale.getDefault())
                                                collator.strength = Collator.PRIMARY
                                                songs.sortedWith(
                                                    compareBy<Song, String>(collator) { song ->
                                                        song.artists.joinToString("") { artist -> artist.name }
                                                    },
                                                )
                                            }

                                            SongSortType.PLAY_TIME -> {
                                                songs.sortedBy { song: Song -> song.song.totalPlayTime }
                                            }
                                        }.reversed(descending)
                                            .filterExplicit(hideExplicit)
                                            .filterVideo(hideVideo)
                                    }
                            }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: SongFilter) {
            when (filter) {
                SongFilter.LIKED -> refreshLibrary(LibrarySyncTarget.LikedSongs)
                SongFilter.LIBRARY -> refreshLibrary(LibrarySyncTarget.Songs)
                SongFilter.DOWNLOADED -> Unit
            }
        }
    }

@HiltViewModel
class LibraryArtistsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        refreshLibrary: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {

        val allArtists =
            context.dataStore.data
                .map {
                    Triple(
                        it[ArtistFilterKey].toEnum(ArtistFilter.LIKED),
                        it[ArtistSortTypeKey].toEnum(ArtistSortType.CREATE_DATE),
                        it[ArtistSortDescendingKey] ?: true,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filter, sortType, descending) ->
                    when (filter) {
                        ArtistFilter.LIBRARY -> database.artists(sortType, descending)
                        ArtistFilter.LIKED -> database.artistsBookmarked(sortType, descending)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: ArtistFilter) {
            refreshLibrary(
                if (filter == ArtistFilter.LIBRARY) LibrarySyncTarget.All else LibrarySyncTarget.Artists,
            )
        }

        init {
            viewModelScope.launch(Dispatchers.IO) {
                allArtists.collect { artists ->
                    artists
                        .map { it.artist }
                        .filter {
                            it.thumbnailUrl == null || Duration.between(
                                it.lastUpdateTime,
                                LocalDateTime.now(),
                            ) > Duration.ofDays(10)
                        }.forEach { artist ->
                            YouTube.artist(artist.id).onSuccess { artistPage ->
                                database.query {
                                    update(artist, artistPage)
                                }
                            }
                        }
                }
            }
        }
    }

@HiltViewModel
class LibraryAlbumsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        downloadUtil: DownloadUtil,
        refreshLibrary: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {

        val allAlbums =
            context.dataStore.data
                .map {
                    Pair(
                        Triple(
                            it[AlbumFilterKey].toEnum(AlbumFilter.LIKED),
                            it[AlbumSortTypeKey].toEnum(AlbumSortType.CREATE_DATE),
                            it[AlbumSortDescendingKey] ?: true,
                        ),
                        it[HideExplicitKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (filterSort, hideExplicit) ->
                    val (filter, sortType, descending) = filterSort
                    when (filter) {
                        AlbumFilter.DOWNLOADED -> {
                            downloadUtil.downloads.flatMapLatest { downloads ->
                                database
                                    .allSongs()
                                    .flowOn(Dispatchers.IO)
                                    .map { songs ->
                                        songs
                                            .filter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }
                                            .mapNotNull { it.song.albumId }
                                            .toSet()
                                    }.flatMapLatest { downloadedAlbumIds ->
                                        database
                                            .albumsByIds(downloadedAlbumIds, sortType, descending)
                                            .map { albums -> albums.filterExplicitAlbums(hideExplicit) }
                                    }
                            }
                        }

                        AlbumFilter.DOWNLOADED_FULL -> {
                            downloadUtil.downloads.flatMapLatest { downloads ->
                                database
                                    .allSongs()
                                    .flowOn(Dispatchers.IO)
                                    .map { songs ->
                                        songs
                                            .filter { song -> downloads[song.id]?.state == Download.STATE_COMPLETED }
                                            .mapNotNull { song -> song.song.albumId?.let { albumId -> albumId to song } }
                                            .groupBy({ it.first }, { it.second })
                                            .mapValues { (_, songList) -> songList.size }
                                    }.flatMapLatest { downloadedCountByAlbum ->
                                        database
                                            .albumsByIds(downloadedCountByAlbum.keys, sortType, descending)
                                            .map { albums ->
                                                albums
                                                    .filter { album ->
                                                        val totalSongsInAlbum = album.album.songCount
                                                        val downloadedSongsCount = downloadedCountByAlbum[album.album.id] ?: 0
                                                        totalSongsInAlbum > 0 && downloadedSongsCount >= totalSongsInAlbum
                                                    }.filterExplicitAlbums(hideExplicit)
                                            }
                                    }
                            }
                        }

                        AlbumFilter.LIBRARY -> {
                            database.albums(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                        }

                        AlbumFilter.LIKED -> {
                            database.albumsLiked(sortType, descending).map { it.filterExplicitAlbums(hideExplicit) }
                        }
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        fun refresh(filter: AlbumFilter) {
            when (filter) {
                AlbumFilter.LIBRARY -> refreshLibrary(LibrarySyncTarget.All)
                AlbumFilter.LIKED -> refreshLibrary(LibrarySyncTarget.Albums)
                AlbumFilter.DOWNLOADED,
                AlbumFilter.DOWNLOADED_FULL,
                -> Unit
            }
        }

        init {
            viewModelScope.launch(Dispatchers.IO) {
                allAlbums.collect { albums ->
                    albums
                        .filter {
                            it.album.songCount == 0
                        }.forEach { album ->
                            YouTube
                                .album(album.id)
                                .onSuccess { albumPage ->
                                    database.query {
                                        update(album.album, albumPage, album.artists)
                                    }
                                }.onFailure {
                                    reportException(it)
                                    if (it.message?.contains("NOT_FOUND") == true) {
                                        database.query {
                                            delete(album.album)
                                        }
                                    }
                                }
                        }
                }
            }
        }
    }

@HiltViewModel
class LibraryPlaylistsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val database: MusicDatabase,
        private val downloadUtil: DownloadUtil,
        refreshLibrary: RefreshLibraryUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {
        val allPlaylists =
            context.dataStore.data
                .map {
                    it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to (
                        it[PlaylistSortDescendingKey]
                            ?: true
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (sortType, descending) ->
                    database.playlists(sortType, descending)
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        val downloadedPlaylistIds =
            combine(
                database.allPlaylistSongMapsForDownloads(),
                downloadUtil.downloads,
            ) { playlistSongMaps, downloads ->
                playlistSongMaps
                    .groupBy { it.playlistId }
                    .mapNotNull { (playlistId, songMaps) ->
                        val songIds =
                            songMaps
                                .asSequence()
                                .map { it.songId }
                                .filterNot { it.isLocalMediaId() }
                                .distinct()
                                .toList()
                        playlistId.takeIf {
                            songIds.isNotEmpty() &&
                                songIds.all { songId ->
                                    downloads[songId]?.state == Download.STATE_COMPLETED
                                }
                        }
                    }.toSet()
            }.stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

        fun sync() {
            refreshLibrary(LibrarySyncTarget.Playlists)
        }

        fun updateCustomPlaylistOrder(playlists: List<Playlist>) {
            if (playlists.isEmpty()) return
            viewModelScope.launch(Dispatchers.IO) {
                database.withTransaction {
                    playlists.forEachIndexed { index, playlist ->
                        setPlaylistCustomOrder(playlist.id, index)
                    }
                }
            }
        }

        val topValue =
            context.dataStore.data
                .map { it[TopSize] ?: "50" }
                .distinctUntilChanged()
    }

@HiltViewModel
class ArtistSongsViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        database: MusicDatabase,
        savedStateHandle: SavedStateHandle,
    ) : ViewModel() {
        private val artistId = savedStateHandle.get<String>("artistId")!!
        val artist =
            database
                .artist(artistId)
                .stateIn(viewModelScope, SharingStarted.Lazily, null)

        val songs =
            context.dataStore.data
                .map {
                    Triple(
                        it[ArtistSongSortTypeKey].toEnum(ArtistSongSortType.CREATE_DATE) to (
                            it[ArtistSongSortDescendingKey]
                                ?: true
                        ),
                        it[HideExplicitKey] ?: false,
                        it[HideVideoKey] ?: false,
                    )
                }.distinctUntilChanged()
                .flatMapLatest { (sortDesc, hideExplicit, hideVideo) ->
                    val (sortType, descending) = sortDesc
                    database.artistSongs(artistId, sortType, descending).map {
                        it.filterExplicit(hideExplicit).filterVideo(hideVideo)
                    }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    }

@HiltViewModel
class LibraryMixViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
        refreshLibrary: RefreshLibraryUseCase,
        observeLibraryTopMixes: ObserveLibraryTopMixesUseCase,
        private val refreshLibraryTopMixes: RefreshLibraryTopMixesUseCase,
    ) : LibraryRefreshViewModel(refreshLibrary) {
        private val _isTopMixRefreshing = MutableStateFlow(false)
        private val _topMixInitialError = MutableStateFlow<String?>(null)
        private val _topMixEvents = MutableSharedFlow<String>()
        val topMixEvents = _topMixEvents.asSharedFlow()
        private var hasRequestedInitialTopMixGeneration = false

        private val isTopMixAiAvailable =
            context.dataStore.data
                .map { prefs ->
                    val provider = prefs[AiProviderKey].toEnum(AiProvider.NONE)
                    provider != AiProvider.NONE &&
                        prefs[AiApiKeyKey].orEmpty().isNotBlank() &&
                        (provider != AiProvider.CUSTOM || prefs[AiCustomEndpointKey].orEmpty().isNotBlank()) &&
                        prefs[AiApiValidationStatusKey].toEnum(AiApiValidationStatus.UNKNOWN) != AiApiValidationStatus.FAILED
                }.distinctUntilChanged()
                .stateIn(viewModelScope, SharingStarted.Lazily, false)

        private val observedTopMixes =
            observeLibraryTopMixes()
                .map<List<LibraryTopMix>, List<LibraryTopMix>?> { it }
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    reportException(throwable)
                    _topMixInitialError.value = context.getString(R.string.library_top_mixes_failed)
                    emit(emptyList())
                }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

        val topMixesUiState =
            combine(
                observedTopMixes,
                isTopMixAiAvailable,
                _isTopMixRefreshing,
                _topMixInitialError,
            ) { mixes, isAiAvailable, isRefreshing, initialError ->
                when {
                    mixes == null -> {
                        LibraryTopMixesUiState.Loading
                    }

                    initialError != null && mixes.isEmpty() -> {
                        LibraryTopMixesUiState.Error(initialError)
                    }

                    mixes.isNotEmpty() -> {
                        LibraryTopMixesUiState.Success(
                            mixes = ImmutableList.copyOf(mixes.map { it.toUiModel() }),
                            isRefreshing = isRefreshing,
                        )
                    }

                    !isAiAvailable -> {
                        LibraryTopMixesUiState.Empty(
                            reason = LibraryTopMixEmptyReason.AI_NOT_CONFIGURED,
                            isRefreshing = isRefreshing,
                        )
                    }

                    else -> {
                        LibraryTopMixesUiState.Empty(
                            reason = LibraryTopMixEmptyReason.NO_RECENT_HISTORY,
                            isRefreshing = isRefreshing,
                        )
                    }
                }
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryTopMixesUiState.Loading)

        val mostPlayedAlbumUiState =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database
                        .mostPlayedAlbums(
                            fromTimeStamp = System.currentTimeMillis() - MOST_PLAYED_ALBUM_WINDOW_MILLIS,
                            limit = 10,
                        ).flatMapLatest { albums ->
                            val album =
                                albums
                                    .filterExplicitAlbums(hideExplicit)
                                    .firstOrNull()
                            if (album == null) {
                                flowOf(MostPlayedAlbumUiState.Empty)
                            } else {
                                database.albumWithSongs(album.id).map { albumWithSongs ->
                                    val songs = albumWithSongs?.songs.orEmpty()
                                    if (songs.isEmpty()) {
                                        MostPlayedAlbumUiState.Empty
                                    } else {
                                        MostPlayedAlbumUiState.Success(
                                            album =
                                                MostPlayedAlbumUiModel(
                                                    id = album.id,
                                                    title = album.title,
                                                    thumbnailUrl = album.thumbnailUrl,
                                                    trackCount = songs.size,
                                                    tracks = ImmutableList.copyOf(songs.map { it.toMediaMetadata() }),
                                                ),
                                        )
                                    }
                                }
                            }
                        }
                }.flowOn(Dispatchers.IO)
                .catch { throwable ->
                    if (throwable is CancellationException) throw throwable
                    reportException(throwable)
                    emit(MostPlayedAlbumUiState.Error(context.getString(R.string.error_unknown)))
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MostPlayedAlbumUiState.Loading)

        init {
            viewModelScope.launch {
                combine(observedTopMixes, isTopMixAiAvailable) { mixes, isAiAvailable ->
                    mixes != null && mixes.isEmpty() && isAiAvailable
                }.distinctUntilChanged()
                    .collect { shouldGenerate ->
                        if (shouldGenerate && !hasRequestedInitialTopMixGeneration) {
                            hasRequestedInitialTopMixGeneration = true
                            refreshTopMixesInternal(isInitialGeneration = true)
                        }
                    }
            }
        }

        fun syncAllLibrary() {
            refreshLibrary(LibrarySyncTarget.All)
        }

        fun refreshTopMixes() {
            hasRequestedInitialTopMixGeneration = true
            refreshTopMixesInternal(isInitialGeneration = false)
        }

        private fun refreshTopMixesInternal(isInitialGeneration: Boolean) {
            if (_isTopMixRefreshing.value) return
            viewModelScope.launch(Dispatchers.IO) {
                _isTopMixRefreshing.value = true
                _topMixInitialError.value = null
                val hasVisibleMixes = observedTopMixes.value.orEmpty().isNotEmpty()
                try {
                    when (val result = refreshLibraryTopMixes()) {
                        RefreshLibraryTopMixesResult.Success -> {
                            _topMixInitialError.value = null
                        }

                        is RefreshLibraryTopMixesResult.Failure -> {
                            result.cause?.let(::reportException)
                            val message = result.reason.toTopMixMessage(result.cause)
                            if (!isInitialGeneration && hasVisibleMixes) {
                                _topMixEvents.emit(message)
                            } else {
                                _topMixInitialError.value = message
                            }
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    reportException(e)
                    val message = TopMixGenerationFailure.AI_REQUEST_FAILED.toTopMixMessage(e)
                    if (!isInitialGeneration && hasVisibleMixes) {
                        _topMixEvents.emit(message)
                    } else {
                        _topMixInitialError.value = message
                    }
                } finally {
                    _isTopMixRefreshing.value = false
                }
            }
        }

        private fun TopMixGenerationFailure.toTopMixMessage(cause: Throwable?): String =
            when (this) {
                TopMixGenerationFailure.AI_NOT_CONFIGURED -> {
                    context.getString(R.string.library_top_mixes_ai_not_configured_desc)
                }

                TopMixGenerationFailure.NO_RECENT_HISTORY -> {
                    context.getString(R.string.library_top_mixes_no_recent_history)
                }

                TopMixGenerationFailure.NO_VALID_MIXES -> {
                    context.getString(R.string.library_top_mixes_no_valid_mixes)
                }

                TopMixGenerationFailure.AI_REQUEST_FAILED -> {
                    buildString {
                        append(context.getString(R.string.library_top_mixes_failed))
                        cause?.localizedMessage?.takeIf(String::isNotBlank)?.let { message ->
                            append(": ")
                            append(message)
                        }
                    }
                }
            }

        val topValue =
            context.dataStore.data
                .map { it[TopSize] ?: "50" }
                .distinctUntilChanged()
        var artists =
            database
                .artistsBookmarked(
                    ArtistSortType.CREATE_DATE,
                    true,
                ).stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        var albums =
            context.dataStore.data
                .map { it[HideExplicitKey] ?: false }
                .distinctUntilChanged()
                .flatMapLatest { hideExplicit ->
                    database.albumsLiked(AlbumSortType.CREATE_DATE, true).map { it.filterExplicitAlbums(hideExplicit) }
                }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
        var playlists =
            context.dataStore.data
                .map {
                    it[PlaylistSortTypeKey].toEnum(PlaylistSortType.CUSTOM) to (it[PlaylistSortDescendingKey] ?: true)
                }.distinctUntilChanged()
                .flatMapLatest { (sortType, descending) -> database.playlists(sortType, descending) }
                .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

        init {
            viewModelScope.launch(Dispatchers.IO) {
                albums.collect { albums ->
                    albums
                        .filter {
                            it.album.songCount == 0
                        }.forEach { album ->
                            YouTube
                                .album(album.id)
                                .onSuccess { albumPage ->
                                    database.query {
                                        update(album.album, albumPage, album.artists)
                                    }
                                }.onFailure {
                                    reportException(it)
                                    if (it.message?.contains("NOT_FOUND") == true) {
                                        database.query {
                                            delete(album.album)
                                        }
                                    }
                                }
                        }
                }
            }
            viewModelScope.launch(Dispatchers.IO) {
                artists.collect { artists ->
                    artists
                        .map { it.artist }
                        .filter {
                            it.thumbnailUrl == null ||
                                Duration.between(
                                    it.lastUpdateTime,
                                    LocalDateTime.now(),
                                ) > Duration.ofDays(10)
                        }.forEach { artist ->
                            YouTube.artist(artist.id).onSuccess { artistPage ->
                                database.query {
                                    update(artist, artistPage)
                                }
                            }
                        }
                }
            }
        }
    }

@Immutable
sealed interface LibraryTopMixesUiState {
    data object Loading : LibraryTopMixesUiState

    @Immutable
    data class Success(
        val mixes: ImmutableList<LibraryTopMixUiModel>,
        val isRefreshing: Boolean,
    ) : LibraryTopMixesUiState

    @Immutable
    data class Empty(
        val reason: LibraryTopMixEmptyReason,
        val isRefreshing: Boolean,
    ) : LibraryTopMixesUiState

    @Immutable
    data class Error(
        val message: String,
    ) : LibraryTopMixesUiState
}

enum class LibraryTopMixEmptyReason {
    AI_NOT_CONFIGURED,
    NO_RECENT_HISTORY,
}

@Immutable
data class LibraryTopMixUiModel(
    val id: String,
    val title: String,
    val description: String,
    val tracks: ImmutableList<MediaMetadata>,
)

private fun LibraryTopMix.toUiModel() =
    LibraryTopMixUiModel(
        id = id,
        title = title,
        description = description,
        tracks = ImmutableList.copyOf(tracks),
    )

@Immutable
sealed interface MostPlayedAlbumUiState {
    data object Loading : MostPlayedAlbumUiState

    @Immutable
    data class Success(
        val album: MostPlayedAlbumUiModel,
    ) : MostPlayedAlbumUiState

    data object Empty : MostPlayedAlbumUiState

    @Immutable
    data class Error(
        val message: String,
    ) : MostPlayedAlbumUiState
}

@Immutable
data class MostPlayedAlbumUiModel(
    val id: String,
    val title: String,
    val thumbnailUrl: String?,
    val trackCount: Int,
    val tracks: ImmutableList<MediaMetadata>,
)

@HiltViewModel
class LibraryViewModel
    @Inject
    constructor() : ViewModel() {
        private val curScreen = mutableStateOf(LibraryFilter.LIBRARY)
        val filter: MutableState<LibraryFilter> = curScreen
    }
