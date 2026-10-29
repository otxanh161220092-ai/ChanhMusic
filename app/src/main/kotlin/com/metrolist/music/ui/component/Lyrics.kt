/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.ui.screens.settings.LyricsPosition
import com.metrolist.music.utils.rememberPreference
import androidx.compose.ui.graphics.Color
import com.metrolist.music.viewmodels.LyricsViewModel

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyrics: Boolean,
    textPositionOverride: LyricsPosition? = null,
    highlightActiveLine: Boolean = false,
    accentOverride: Color? = null,
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    val (experimentalLyrics, _) = rememberPreference(key = ExperimentalLyricsKey, defaultValue = true)
    val lyricsModifier = modifier

    if (experimentalLyrics) {
        ExperimentalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = lyricsModifier,
            showLyrics = showLyrics,
            textPositionOverride = textPositionOverride,
            highlightActiveLine = highlightActiveLine,
            accentOverride = accentOverride,
            lyricsViewModel = lyricsViewModel
        )
    } else {
        OriginalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = lyricsModifier,
            showLyrics = showLyrics
        )
    }
}
