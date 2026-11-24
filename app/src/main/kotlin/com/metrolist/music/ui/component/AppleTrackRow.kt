/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.metrolist.music.R
import com.metrolist.music.db.entities.Song
import com.metrolist.music.ui.player.BcStarFilled

/**
 * Track row for Apple style collections: track number, title with an explicit badge, and a
 * trailing action. Colours are fixed white so the row reads the same on any artwork.
 */
@Composable
fun AppleTrackRow(
    index: Int,
    song: Song,
    isActive: Boolean,
    /** Apple's grey dot to the left of a popular track. */
    popular: Boolean = false,
    modifier: Modifier = Modifier,
    trailingContent: @Composable () -> Unit,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // A liked song shows BitChord's filled star here, over the popular dot.
            Box(modifier = Modifier.width(12.dp), contentAlignment = Alignment.Center) {
                when {
                    song.song.liked -> Icon(
                        imageVector = BcStarFilled,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                    popular -> Box(
                        modifier = Modifier
                            .size(5.dp)
                            .background(Color.White.copy(alpha = 0.5f), CircleShape),
                    )
                }
            }
            Text(
                text = index.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = appleSubtitleColor(),
                textAlign = TextAlign.Center,
                modifier = Modifier.width(32.dp),
            )
            Spacer(modifier = Modifier.width(20.dp))
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = song.song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = LocalAppleCollectionTextColor.current.takeIf { it != Color.Unspecified } ?: Color.White,
                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (song.song.explicit) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        painter = painterResource(R.drawable.ic_explicit),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Box(contentAlignment = Alignment.Center) { trailingContent() }
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 76.dp),
            color = Color.White.copy(alpha = 0.15f),
        )
    }
}
