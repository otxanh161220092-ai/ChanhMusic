/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.music.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.metrolist.music.archivetune.innertube.YouTube
import com.metrolist.music.archivetune.innertube.models.BrowseEndpoint
import com.metrolist.music.archivetune.innertube.pages.MoodAndGenres
import com.metrolist.music.archivetune.repository.MoodAndGenresArtworkRepository
import com.metrolist.music.archivetune.utils.reportException
import javax.inject.Inject

@HiltViewModel
class MoodAndGenresViewModel
    @Inject
    constructor(
        artworkRepository: MoodAndGenresArtworkRepository,
    ) : ViewModel() {
        val moodAndGenres = MutableStateFlow<List<MoodAndGenres.Item>?>(null)
        private val artworkLoader = MoodAndGenresArtworkLoader(artworkRepository, viewModelScope)
        val artwork = artworkLoader.state

        init {
            viewModelScope.launch {
                YouTube
                    .explore()
                    .onSuccess {
                        moodAndGenres.value = it.moodAndGenres
                    }.onFailure {
                        reportException(it)
                    }
            }
        }

        fun setVisibleArtwork(endpoints: List<BrowseEndpoint>) {
            artworkLoader.setVisible(endpoints)
        }
    }
