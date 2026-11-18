package com.metrolist.music.ui.component

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer

/** The layer that records the app's screen, so popup menus can take a still picture of it. */
val LocalScreenLayer = compositionLocalOf<GraphicsLayer?> { null }

fun Modifier.recordScreen(layer: GraphicsLayer): Modifier =
    drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
