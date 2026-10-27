/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.ui.theme.rankedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Dominant color of the artwork at [thumbnailUrl]; null while loading or when unavailable. */
@Composable
fun rememberArtworkColor(thumbnailUrl: String?): State<Color?> {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.surfaceVariant.toArgb()
    return produceState<Color?>(initialValue = null, thumbnailUrl) {
        if (thumbnailUrl == null) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.IO) {
            val request =
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .size(100, 100)
                    .allowHardware(false)
                    .build()
            val bitmap =
                runCatching { context.imageLoader.execute(request).image?.toBitmap() }
                    .getOrNull() ?: return@withContext null
            withContext(Dispatchers.Default) {
                val palette =
                    Palette
                        .from(bitmap)
                        .maximumColorCount(8)
                        .generate()
                Color(palette.rankedColors(1, fallback).first())
            }
        }
    }
}
