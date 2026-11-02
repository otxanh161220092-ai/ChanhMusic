/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.metrolist.music.R
import com.metrolist.music.playback.automix.AutomixStatus

/** One line under the artist name telling whether AutoMix is still looking for the mix point or has found it. */
@Composable
fun AutomixStatusLine(
    status: AutomixStatus,
    color: Color,
    modifier: Modifier = Modifier,
) {
    // The line always takes the same height while AutoMix is on, so the title, artist and controls
    // never move when the status changes between finding, ready and unavailable.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.height(18.dp).padding(top = 2.dp),
    ) {
        if (status is AutomixStatus.Idle) return@Row
        if (status is AutomixStatus.Analyzing) {
            CircularProgressIndicator(
                modifier = Modifier.size(12.dp),
                strokeWidth = 1.5.dp,
                color = color,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_automix),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(12.dp),
            )
        }
        val text =
            when (status) {
                is AutomixStatus.Ready ->
                    stringResource(
                        if (status.tempoMatched) R.string.automix_status_ready_matched else R.string.automix_status_ready,
                        formatClock(status.mixOutMs),
                        formatClock(status.mixInMs),
                    )
                is AutomixStatus.Unavailable -> stringResource(R.string.automix_status_unavailable)
                else -> stringResource(R.string.automix_status_analyzing)
            }
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private fun formatClock(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
}
