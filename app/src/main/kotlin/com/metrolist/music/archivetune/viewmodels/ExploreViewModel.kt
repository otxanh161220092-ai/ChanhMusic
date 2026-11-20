/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.music.archivetune.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.metrolist.music.archivetune.aicontentfilter.FilterAiContentUseCase
import com.metrolist.music.archivetune.aicontentfilter.LoadAiContentFilterPolicyUseCase
import com.metrolist.music.archivetune.constants.HideExplicitKey
import com.metrolist.music.archivetune.constants.HideVideoKey
import com.metrolist.music.archivetune.db.MusicDatabase
import com.metrolist.music.archivetune.extensions.filterBlockedArtists
import com.metrolist.music.archivetune.innertube.YouTube
import com.metrolist.music.archivetune.innertube.models.filterExplicit
import com.metrolist.music.archivetune.innertube.models.filterVideo
import com.metrolist.music.archivetune.innertube.pages.ExplorePage
import com.metrolist.music.archivetune.utils.dataStore
import com.metrolist.music.archivetune.utils.get
import com.metrolist.music.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel
    @Inject
    constructor(
        @ApplicationContext val context: Context,
        val database: MusicDatabase,
        private val loadAiContentFilterPolicy: LoadAiContentFilterPolicyUseCase,
        private val filterAiContent: FilterAiContentUseCase,
    ) : ViewModel() {
        val explorePage = MutableStateFlow<ExplorePage?>(null)

        private suspend fun load() {
            YouTube
                .explore()
                .onSuccess { page ->
                    val blockedArtistIds = database.getBlockedArtistIds().toSet()
                    val aiContentFilterPolicy = loadAiContentFilterPolicy()
                    val artists: MutableMap<Int, String> = mutableMapOf()
                    val favouriteArtists: MutableMap<Int, String> = mutableMapOf()
                    database.allArtistsByPlayTime().first().let { list ->
                        var favIndex = 0
                        for ((artistsIndex, artist) in list.withIndex()) {
                            artists[artistsIndex] = artist.id
                            if (artist.artist.bookmarkedAt != null) {
                                favouriteArtists[favIndex] = artist.id
                                favIndex++
                            }
                        }
                    }
                    explorePage.value =
                        page.copy(
                            newReleaseAlbums =
                                filterAiContent(
                                    page.newReleaseAlbums
                                        .sortedBy { album ->
                                            val artistIds = album.artists.orEmpty().mapNotNull { it.id }
                                            val firstArtistKey =
                                                artistIds.firstNotNullOfOrNull { artistId ->
                                                    if (artistId in favouriteArtists.values) {
                                                        favouriteArtists.entries.firstOrNull { it.value == artistId }?.key
                                                    } else {
                                                        artists.entries.firstOrNull { it.value == artistId }?.key
                                                    }
                                                } ?: Int.MAX_VALUE
                                            firstArtistKey
                                        }.filterExplicit(
                                            context.dataStore.get(HideExplicitKey, false),
                                        ).filterVideo(context.dataStore.get(HideVideoKey, false))
                                        .filterBlockedArtists(blockedArtistIds),
                                    aiContentFilterPolicy,
                                ),
                        )
                }.onFailure {
                    reportException(it)
                }
        }

        init {
            viewModelScope.launch(Dispatchers.IO) {
                load()
            }
        }
    }
