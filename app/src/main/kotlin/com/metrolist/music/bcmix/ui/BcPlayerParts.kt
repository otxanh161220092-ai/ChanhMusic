/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The BitChord Automix pieces of the player: the analysis status line (with its
 * "Mixing" state), the scrubber that shows a blend's beat pulse and transition
 * window, and the mix popup overlay. Ported from BitChord's PlayerControls and
 * NowPlayingScreen.
 */

package com.metrolist.music.bcmix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.bcmix.settings.AppSettings
import com.metrolist.music.bcmix.settings.TrackAnalysisState

/** Analysis line under the artist: "this song / next" while searching, the blend's name while mixing. */
@Composable
fun BcAutomixStatus(
    color: Color,
    modifier: Modifier = Modifier,
) {
    val smartAnalysis by AppSettings.smartAnalysis.collectAsStateWithLifecycle()
    val automixDetail by AppSettings.automixDetail.collectAsStateWithLifecycle()
    val mixing by AppSettings.smartMixInProgress.collectAsStateWithLifecycle()
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth().height(18.dp).padding(top = 2.dp),
    ) {
        if (mixing) {
            Text(
                text = stringResource(BcStrings.mixing),
                style = MaterialTheme.typography.labelMedium,
                color = color,
                modifier = Modifier.clickable { MixViewRequest.open = true },
            )
        } else {
            Text(
                text = automixDetail ?: stringResource(
                    BcStrings.automix_analysis_status,
                    smartAnalysis.current.label(),
                    smartAnalysis.next.label(),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.clickable { MixViewRequest.open = true },
            )
        }
    }
}

@Composable
private fun TrackAnalysisState.label(): String = stringResource(
    when (this) {
        TrackAnalysisState.ANALYSED -> BcStrings.analysis_complete
        TrackAnalysisState.REFINING -> BcStrings.analysis_refining
        TrackAnalysisState.ANALYSING -> BcStrings.analysis_in_progress
        TrackAnalysisState.WAITING -> BcStrings.waiting
        TrackAnalysisState.FAILED -> BcStrings.failed
    },
)

/**
 * The progress bar while Automix is on: the blend's beat pulse across the bar
 * during a mix, and the transition window marked on it.
 */
@Composable
fun BcScrubber(
    positionMs: Long,
    durationMs: Long,
    onScrub: (Long) -> Unit,
    onScrubFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val transitionWindow by AppSettings.smartTransitionWindow.collectAsStateWithLifecycle()
    val mixPulse = rememberMixPulse({ AppSettings.smartMixBlend.value }, enabled = true)
    val mixing by AppSettings.smartMixInProgress.collectAsStateWithLifecycle()
    val fraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    ThinSlider(
        value = fraction,
        onValueChange = { if (durationMs > 0) onScrub((it * durationMs).toLong()) },
        onValueChangeFinished = onScrubFinished,
        mixing = mixing,
        mixPulse = mixPulse,
        transitionWindow = transitionWindow?.let { it.start..it.end },
        modifier = modifier,
    )
}
