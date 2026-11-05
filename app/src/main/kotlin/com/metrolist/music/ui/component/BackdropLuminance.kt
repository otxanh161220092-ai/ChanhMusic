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
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

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
        val decor = window.decorView
        // PixelCopy throws when the window has no surface (app backgrounded, activity stopping).
        if (!decor.isAttachedToWindow || decor.visibility != android.view.View.VISIBLE || !decor.isShown) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        val width = bounds.width.toInt().coerceAtLeast(1)
        val left = bounds.left.toInt()
        val top = bounds.bottom.toInt() + 2
        val bitmap =
            try {
                Bitmap.createBitmap(width, stripHeight, Bitmap.Config.ARGB_8888)
            } catch (e: IllegalArgumentException) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }
        val area = android.graphics.Rect(left, top, left + width, top + stripHeight)
        val finish: (Float?) -> Unit = { value ->
            bitmap.recycle()
            if (cont.isActive) cont.resume(value)
        }
        try {
            PixelCopy.request(
                window,
                area,
                bitmap,
                { result ->
                    if (result != PixelCopy.SUCCESS) {
                        finish(null)
                    } else {
                        val value =
                            runCatching {
                                val pixels = IntArray(width * stripHeight)
                                bitmap.getPixels(pixels, 0, width, 0, 0, width, stripHeight)
                                var sum = 0.0
                                for (p in pixels) {
                                    val r = ((p shr 16) and 0xFF) / 255.0
                                    val g = ((p shr 8) and 0xFF) / 255.0
                                    val b = (p and 0xFF) / 255.0
                                    sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                                }
                                (sum / pixels.size).toFloat()
                            }.getOrNull()
                        finish(value)
                    }
                },
                Handler(Looper.getMainLooper()),
            )
        } catch (e: Exception) {
            // Any failure to copy means no reading this tick; the next tick tries again.
            finish(null)
        }
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
