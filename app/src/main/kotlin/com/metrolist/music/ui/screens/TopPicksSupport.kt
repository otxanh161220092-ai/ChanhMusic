package com.metrolist.music.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.topPicks.TopPick
import com.metrolist.music.ui.component.topshelf.ShelfRow

/** Support pieces for the Top Picks shelf, replacing BitChord's shared UI and data layers. */

val PAGE_GUTTER: Dp = 16.dp
val SHELF_CARD_WIDTH: Dp = 160.dp
val LIBRARY_GRID_SPACING: Dp = 12.dp
const val MOTION_FRAME_GAP_MS: Long = 16L
const val HEADER_ART_PX: Int = 600
const val CARD_ART_PX: Int = 300
const val BLOCK_RADIUS_DP: Int = 12

fun artworkAt(px: Int): String? = null

/** A canvas clip. Metrolist has no clip source yet, so every lookup returns null for now. */
data class CanvasArtwork(val url: String)

/** Stand-in for BitChord's AppUi host: no clips are looked up until the canvas source is ported. */
object AppUi {
    val host: AppUiHost = AppUiHost
}

object AppUiHost {
    suspend fun releaseMotion(album: String, artist: String): CanvasArtwork? = null
    suspend fun artistMotion(browseId: String, artistName: String): CanvasArtwork? = null
}

@Composable
fun CanvasArtworkPlayer(
    canvas: CanvasArtwork,
    isPlaying: Boolean,
    modifier: Modifier,
    bottomFade: Any?,
    onCoverChanged: Any?,
    pausedForTransition: Boolean,
) {
    // Video playback of canvas clips is not ported yet.
}

@Composable
fun ShimmerBox(modifier: Modifier = Modifier, shape: Shape = RoundedCornerShape(8.dp)) {
    Box(modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun SectionHeaderSkeleton(index: Int = 0) {
    Column(Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp)) {
        ShimmerBox(Modifier.fillMaxWidth(0.4f).height(22.dp))
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
    )
}

fun Modifier.thumbnailBorder(shape: Shape): Modifier =
    this.border(1.dp, Color.White.copy(alpha = 0.08f), shape)

/** No blur pass yet: the cover is drawn as it is. */
@Composable
fun rememberBlurredBackdrop(url: String?): Any? = url

data class LibraryGridSpec(val columns: Int)

fun libraryGrid(width: Dp): LibraryGridSpec =
    LibraryGridSpec(columns = maxOf(2, (width / 160.dp).toInt()))
