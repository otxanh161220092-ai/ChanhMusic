/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.Window
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine

/** Window-space rectangle of a laid-out node. */
fun LayoutCoordinates.windowRect(): Rect {
    val pos = positionInWindow()
    return Rect(pos.x, pos.y, pos.x + size.width, pos.y + size.height)
}

/**
 * Average relative luminance (0..1) of a thin strip directly below [bounds], read from the window's
 * rendered pixels with [PixelCopy]. Returns null when the copy fails.
 */
suspend fun sampleLuminanceBelow(
    window: Window,
    bounds: Rect,
    stripHeight: Int = 6,
): Float? =
    suspendCancellableCoroutine { cont ->
        val width = bounds.width.toInt().coerceAtLeast(1)
        val left = bounds.left.toInt()
        val top = bounds.bottom.toInt() + 2
        val bitmap = Bitmap.createBitmap(width, stripHeight, Bitmap.Config.ARGB_8888)
        val area = android.graphics.Rect(left, top, left + width, top + stripHeight)
        PixelCopy.request(
            window,
            area,
            bitmap,
            { result ->
                if (result == PixelCopy.SUCCESS) {
                    val pixels = IntArray(width * stripHeight)
                    bitmap.getPixels(pixels, 0, width, 0, 0, width, stripHeight)
                    var sum = 0.0
                    for (p in pixels) {
                        val r = ((p shr 16) and 0xFF) / 255.0
                        val g = ((p shr 8) and 0xFF) / 255.0
                        val b = (p and 0xFF) / 255.0
                        sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                    }
                    bitmap.recycle()
                    cont.resumeWith(Result.success((sum / pixels.size).toFloat()))
                } else {
                    bitmap.recycle()
                    cont.resumeWith(Result.success(null))
                }
            },
            Handler(Looper.getMainLooper()),
        )
    }

/**
 * True while the pixels just below [bounds] are light. Re-samples about four times a second while
 * [bounds] is known; stays false until the first sample lands.
 */
@Composable
fun rememberBackdropIsLight(bounds: Rect?): State<Boolean> {
    val view = LocalView.current
    val isLight = remember { mutableStateOf(false) }
    LaunchedEffect(bounds) {
        val b = bounds ?: return@LaunchedEffect
        val activity =
            generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull() ?: return@LaunchedEffect
        while (isActive) {
            sampleLuminanceBelow(activity.window, b)?.let { isLight.value = it > 0.5f }
            delay(250)
        }
    }
    return isLight
}
