/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.music.archivetune.viewmodels

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import com.google.common.collect.ImmutableMap
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.metrolist.music.archivetune.R
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity
import com.metrolist.music.archivetune.extensions.toEnum
import com.metrolist.music.archivetune.lyrics.LyricsHelper
import com.metrolist.music.archivetune.lyrics.LyricsResult
import com.metrolist.music.archivetune.lyrics.LyricsUtils
import com.metrolist.music.archivetune.lyrics.LyricsUtils.displayLyricsText
import com.metrolist.music.archivetune.lyrics.LyricsUtils.isLineSyncedLrc
import com.metrolist.music.archivetune.lyrics.LyricsUtils.isTtml
import com.metrolist.music.models.MediaMetadata
import com.metrolist.music.utils.NetworkConnectivityObserver
import com.metrolist.music.utils.reportException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

sealed interface LyricsSearchScreenState {
    data object Loading : LyricsSearchScreenState

    @Immutable
    data class Success(
        val results: ImmutableList<LyricsSearchResultUiModel>,
        val formatCounts: ImmutableMap<LyricsSearchFormat, Int>,
        val isSearching: Boolean,
    ) : LyricsSearchScreenState

    data object Empty : LyricsSearchScreenState

    @Immutable
    data class Error(
        @StringRes val messageResId: Int,
    ) : LyricsSearchScreenState
}

enum class LyricsSearchFormat {
    WORD_SYNCED,
    LINE_SYNCED,
    PLAIN,
}

@Immutable
data class LyricsSearchResultUiModel(
    val id: String,
    val providerName: String,
    val providerUrl: String?,
    val author: String?,
    val authorUrl: String?,
    val lyrics: String,
    val preview: String,
    val lineCount: Int,
    val characterCount: Int,
    val format: LyricsSearchFormat,
)

enum class LyricsRefetchResult {
    Updated,
    NotFound,
    Unavailable,
}

@HiltViewModel
class LyricsMenuViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val lyricsHelper: LyricsHelper,
        val database: MusicDatabase,
        private val networkConnectivity: NetworkConnectivityObserver,
    ) : ViewModel() {
        private var job: Job? = null
        private val searchGeneration = AtomicLong(0L)
        private val _lyricsSearchState = MutableStateFlow<LyricsSearchScreenState>(LyricsSearchScreenState.Empty)
        val lyricsSearchState: StateFlow<LyricsSearchScreenState> = _lyricsSearchState.asStateFlow()
        private val _isRefetching = MutableStateFlow(false)
        val isRefetching: StateFlow<Boolean> = _isRefetching.asStateFlow()
        private val _refetchCompletionEvents = MutableSharedFlow<LyricsRefetchResult>(extraBufferCapacity = 1)
        val refetchCompletionEvents: SharedFlow<LyricsRefetchResult> = _refetchCompletionEvents.asSharedFlow()
        private val _isNetworkAvailable = MutableStateFlow(false)
        val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

        init {
            viewModelScope.launch {
                networkConnectivity.networkStatus.collect { isConnected ->
                    _isNetworkAvailable.value = isConnected
                }
            }

            _isNetworkAvailable.value =
                try {
                    networkConnectivity.isCurrentlyConnected()
                } catch (e: Exception) {
                    true
                }
        }

        fun search(
            mediaId: String,
            title: String,
            artist: String,
            album: String?,
            duration: Int,
        ) {
            val generation = searchGeneration.incrementAndGet()
            job?.cancel()
            _lyricsSearchState.value = LyricsSearchScreenState.Loading
            job =
                viewModelScope.launch(Dispatchers.IO) {
                    val resultModels = mutableListOf<LyricsSearchResultUiModel>()
                    try {
                        lyricsHelper.getAllLyrics(
                            mediaId = mediaId,
                            songTitle = title,
                            songArtists = artist,
                            songAlbum = album,
                            duration = duration,
                        ) { result ->
                            if (generation != searchGeneration.get()) return@getAllLyrics
                            val model = result.toUiModel(resultModels.size)
                            if (model.preview.isBlank()) return@getAllLyrics

                            resultModels += model
                            _lyricsSearchState.value = resultModels.toSuccessState(isSearching = true)
                        }
                        if (generation != searchGeneration.get()) return@launch
                        _lyricsSearchState.value =
                            if (resultModels.isEmpty()) {
                                LyricsSearchScreenState.Empty
                            } else {
                                resultModels.toSuccessState(isSearching = false)
                            }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        if (generation == searchGeneration.get()) {
                            _lyricsSearchState.value = LyricsSearchScreenState.Error(R.string.error_unknown)
                        }
                    }
                }
        }

        fun cancelSearch() {
            searchGeneration.incrementAndGet()
            job?.cancel()
            job = null
        }

        fun resetSearchState() {
            cancelSearch()
            _lyricsSearchState.value = LyricsSearchScreenState.Empty
        }

        fun refetchLyrics(mediaMetadata: MediaMetadata) {
            if (!_isRefetching.compareAndSet(expect = false, update = true)) return

            viewModelScope.launch(Dispatchers.IO) {
                val result =
                    try {
                        val fetched = lyricsHelper.getLyrics(mediaMetadata, forceRefresh = true)
                        when {
                            fetched.isFound -> {
                                database.withTransaction {
                                    replaceLyrics(
                                        id = mediaMetadata.id,
                                        lyrics = fetched.lyrics,
                                        source = LyricsEntity.Source.REMOTE.value,
                                        provider = fetched.providerName,
                                        providerUrl = fetched.providerUrl,
                                        providerAuthor = fetched.author,
                                        providerAuthorUrl = fetched.authorUrl,
                                    )
                                }
                                LyricsRefetchResult.Updated
                            }

                            fetched.isConclusive -> {
                                database.withTransaction {
                                    saveFetchedLyrics(
                                        id = mediaMetadata.id,
                                        lyrics = fetched.lyrics,
                                        provider = fetched.providerName,
                                        providerUrl = fetched.providerUrl,
                                        providerAuthor = fetched.author,
                                        providerAuthorUrl = fetched.authorUrl,
                                    )
                                }
                                LyricsRefetchResult.NotFound
                            }

                            else -> {
                                LyricsRefetchResult.Unavailable
                            }
                        }
                    } catch (e: CancellationException) {
                        _isRefetching.value = false
                        throw e
                    } catch (e: Exception) {
                        reportException(e)
                        LyricsRefetchResult.Unavailable
                    }
                _isRefetching.value = false
                _refetchCompletionEvents.tryEmit(result)
            }
        }

        fun updateLyrics(
            mediaMetadata: MediaMetadata,
            lyrics: String,
            source: LyricsEntity.Source = LyricsEntity.Source.USER_EDIT,
            provider: String? = null,
            providerUrl: String? = null,
            providerAuthor: String? = null,
            providerAuthorUrl: String? = null,
        ) {
            viewModelScope.launch(Dispatchers.IO) {
                val lyricsToSave =
                    when (source) {
                        LyricsEntity.Source.REMOTE,
                        LyricsEntity.Source.EMBEDDED,
                        LyricsEntity.Source.USER_SELECTION,
                        -> LyricsUtils.lyricsOrNotFound(lyrics)

                        LyricsEntity.Source.USER_EDIT,
                        -> lyrics

                        LyricsEntity.Source.TRANSLATION,
                        LyricsEntity.Source.AI_TRANSLATION ->
                            usableTranslatedLyrics(lyrics) ?: return@launch
                    }
                database.query {
                    replaceLyrics(
                        id = mediaMetadata.id,
                        lyrics = lyricsToSave,
                        source = source.value,
                        provider = provider,
                        providerUrl = providerUrl,
                        providerAuthor = providerAuthor,
                        providerAuthorUrl = providerAuthorUrl,
                    )
                }
            }
        }

        private fun usableTranslatedLyrics(lyrics: String): String? =
            LyricsUtils
                .normalizeLyricsText(lyrics)
                .takeIf(LyricsUtils::hasMeaningfulLyricsContent)

        private fun List<LyricsSearchResultUiModel>.toSuccessState(isSearching: Boolean): LyricsSearchScreenState.Success {
            val counts = ImmutableMap.builder<LyricsSearchFormat, Int>()
            LyricsSearchFormat.entries.forEach { format ->
                counts.put(format, count { it.format == format })
            }
            return LyricsSearchScreenState.Success(
                results = ImmutableList.copyOf(this),
                formatCounts = counts.build(),
                isSearching = isSearching,
            )
        }

        private fun LyricsResult.toUiModel(index: Int): LyricsSearchResultUiModel {
            val preview = displayLyricsText(lyrics)
            val lineCount = preview.lineSequence().count { it.isNotBlank() }
            val format =
                when {
                    LyricsUtils.hasWordSyncedLyrics(lyrics) -> LyricsSearchFormat.WORD_SYNCED
                    isTtml(lyrics) ->
                        if (runCatching { LyricsUtils.parseTtml(lyrics) }.getOrDefault(emptyList()).isNotEmpty()) {
                            LyricsSearchFormat.LINE_SYNCED
                        } else {
                            LyricsSearchFormat.PLAIN
                        }
                    isLineSyncedLrc(lyrics) -> LyricsSearchFormat.LINE_SYNCED
                    else -> LyricsSearchFormat.PLAIN
                }

            return LyricsSearchResultUiModel(
                id = "${providerName}_${lyrics.hashCode()}_$index",
                providerName = providerName,
                providerUrl = providerUrl,
                author = author,
                authorUrl = authorUrl,
                lyrics = lyrics,
                preview = preview,
                lineCount = lineCount,
                characterCount = preview.length,
                format = format,
            )
        }
    }
