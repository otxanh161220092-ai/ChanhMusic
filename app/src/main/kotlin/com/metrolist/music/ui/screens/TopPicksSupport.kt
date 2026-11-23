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
import com.metrolist.music.topPicks.AppleAlbumCanvas
import com.metrolist.music.topPicks.CanvasArtwork
import com.metrolist.music.topPicks.CanvasSource
import com.metrolist.music.topPicks.TopPick
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.graphics.Bitmap
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
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
/** Stand-in for BitChord's AppUi host: no clips are looked up until the canvas source is ported. */
object AppUi {
    val host: AppUiHost = AppUiHost
}

object AppUiHost {
    /** Apple motion artwork for a release, looked up off the main thread. Null when none. */
    suspend fun releaseMotion(album: String, artist: String): CanvasArtwork? =
        withContext(Dispatchers.IO) { AppleAlbumCanvas.searchAlbum(album, artist) }

    /** Artist clips are not ported yet. */
    suspend fun artistMotion(browseId: String, artistName: String): CanvasArtwork? = null
}

/**
 * The Top Picks clip in the player's own type. Metrolist's canvas player (the one
 * the Now Playing and artist header already use) takes its own CanvasArtwork and a
 * CanvasSource with only SPOTIFY and OTHER, so an Apple clip is passed as OTHER.
 */
fun CanvasArtwork.toPlayerClip(): com.metrolist.music.bcmix.canvas.CanvasArtwork =
    com.metrolist.music.bcmix.canvas.CanvasArtwork(
        url = url,
        fallbackUrl = fallbackUrl,
        title = title,
        artist = artist,
        album = album,
        source = if (source == CanvasSource.SPOTIFY) {
            com.metrolist.music.bcmix.canvas.CanvasSource.SPOTIFY
        } else {
            com.metrolist.music.bcmix.canvas.CanvasSource.OTHER
        },
        portraitUrl = portraitUrl,
    )

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
fun SectionHeader(title: String, subtitle: String = "", onShowAll: (() -> Unit)? = null) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(horizontal = PAGE_GUTTER, vertical = 10.dp),
    )
}

fun Modifier.thumbnailBorder(shape: Shape): Modifier =
    this.border(1.dp, Color.White.copy(alpha = 0.08f), shape)

/**
 * The cover's lower strip, shrunk and blurred once as it loads — what Apple lays
 * under a release card's caption. Ported from BitChord's BlurredBackdrop (Android).
 */
@Composable
fun rememberBlurredBackdrop(url: String?): Any? {
    val context = LocalPlatformContext.current
    return remember(url, context) {
        url ?: return@remember null
        ImageRequest.Builder(context)
            .data(url)
            .transformations(BackdropBlur)
            .build()
    }
}

private object BackdropBlur : Transformation() {
    /** The share of the cover, from the bottom, that the backdrop continues. */
    private const val STRIP = 0.4f
    private const val WIDTH = 48
    private const val RADIUS = 3
    private const val PASSES = 3

    override val cacheKey = "backdropBlur:$STRIP:$WIDTH:$RADIUS:$PASSES"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val top = (input.height * (1f - STRIP)).toInt().coerceIn(0, input.height - 1)
        val height = ((input.height - top) * WIDTH / input.width.toFloat()).toInt().coerceAtLeast(4)
        val software = if (input.config == Bitmap.Config.HARDWARE) input.copy(Bitmap.Config.ARGB_8888, false) else input
        val strip = Bitmap.createBitmap(software, 0, top, software.width, software.height - top)
        val small = Bitmap.createScaledBitmap(strip, WIDTH, height, true)
        val pixels = IntArray(WIDTH * height)
        small.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, height)
        repeat(PASSES) {
            boxBlur(pixels, WIDTH, height, RADIUS, horizontal = true)
            boxBlur(pixels, WIDTH, height, RADIUS, horizontal = false)
        }
        return Bitmap.createBitmap(pixels, WIDTH, height, Bitmap.Config.ARGB_8888)
    }

    /** One pass of a running-sum box blur along rows or columns, edges clamped. */
    private fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val lines = if (horizontal) height else width
        val length = if (horizontal) width else height
        val line = IntArray(length)
        val window = radius * 2 + 1
        for (l in 0 until lines) {
            fun at(i: Int) = if (horizontal) l * width + i else i * width + l
            for (i in 0 until length) line[i] = pixels[at(i)]
            var r = 0
            var g = 0
            var b = 0
            for (k in -radius..radius) {
                val p = line[k.coerceIn(0, length - 1)]
                r += p shr 16 and 0xFF
                g += p shr 8 and 0xFF
                b += p and 0xFF
            }
            for (i in 0 until length) {
                pixels[at(i)] = (0xFF shl 24) or ((r / window) shl 16) or ((g / window) shl 8) or (b / window)
                val out = line[(i - radius).coerceIn(0, length - 1)]
                val inn = line[(i + radius + 1).coerceIn(0, length - 1)]
                r += (inn shr 16 and 0xFF) - (out shr 16 and 0xFF)
                g += (inn shr 8 and 0xFF) - (out shr 8 and 0xFF)
                b += (inn and 0xFF) - (out and 0xFF)
            }
        }
    }
}

data class LibraryGridSpec(val columns: Int)

fun libraryGrid(width: Dp): LibraryGridSpec =
    LibraryGridSpec(columns = maxOf(2, (width / 160.dp).toInt()))

/** Subtitle text as BitChord localizes it; Metrolist shows the YouTube text unchanged. */
fun localizeCardSubtitle(text: String): String = text
