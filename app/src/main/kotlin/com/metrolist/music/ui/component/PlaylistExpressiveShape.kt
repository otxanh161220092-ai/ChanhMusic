/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package com.metrolist.music.ui.component

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.RoundedPolygon

private val playlistShapePolygons: List<RoundedPolygon> = listOf(
    MaterialShapes.Cookie4Sided,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Cookie7Sided,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Cookie12Sided,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Clover8Leaf,
    MaterialShapes.Burst,
    MaterialShapes.SoftBurst,
    MaterialShapes.Pill,
    MaterialShapes.Sunny,
    MaterialShapes.VerySunny,
    MaterialShapes.Oval,
    MaterialShapes.Arch,
    MaterialShapes.Pentagon,
    MaterialShapes.Gem,
    MaterialShapes.Flower,
    MaterialShapes.Boom,
    MaterialShapes.Diamond,
    MaterialShapes.Puffy,
    MaterialShapes.Ghostish,
    MaterialShapes.Bun,
)

/**
 * The shape is picked from the playlist id, so each playlist keeps the same shape
 * on every launch.
 */
@Composable
fun expressivePlaylistShape(playlistId: String): Shape {
    val polygon = playlistShapePolygons[Math.floorMod(playlistId.hashCode(), playlistShapePolygons.size)]
    return remember(polygon) { polygon.toShape() }
}
