/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.LocalPlayerConnection
import com.metrolist.music.lyrics.LyricsUtils
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.metrolist.music.constants.ExperimentalLyricsKey
import com.metrolist.music.constants.LyricsAnimationStyle
import com.metrolist.music.constants.LyricsAnimationStyleKey
import com.metrolist.music.utils.rememberEnumPreference
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
    val lyricsAnimationStyle by rememberEnumPreference(LyricsAnimationStyleKey, LyricsAnimationStyle.APPLE)
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
    } else if (lyricsAnimationStyle == LyricsAnimationStyle.BRACCATO) {
        BraccatoLyricsPanel(
            sliderPositionProvider = sliderPositionProvider,
            modifier = lyricsModifier,
            showLyrics = showLyrics
        )
    } else {
        OriginalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = lyricsModifier,
            showLyrics = showLyrics
        )
    }
}

@Composable
private fun BraccatoLyricsPanel(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier,
    showLyrics: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val lyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val lines =
        remember(lyricsEntity) {
            lyricsEntity?.lyrics?.trim()?.takeIf { it.isNotEmpty() }?.let { LyricsUtils.parseLyrics(it) }.orEmpty()
        }
    if (!showLyrics || lines.isEmpty()) return
    BraccatoLyrics(
        lines = lines,
        positionMs = { sliderPositionProvider() ?: playerConnection.player.currentPosition },
        isPlaying = isPlaying,
        onSeekMs = { playerConnection.player.seekTo(it) },
        modifier = modifier,
    )
}
