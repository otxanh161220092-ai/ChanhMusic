package com.metrolist.music.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * BitChord's favorite star, ported from its BitChordIcons: a five-point star whose
 * tips are softened by the round stroke join. The stroke is tinted by the Icon.
 */
internal val BcStar: ImageVector by lazy { bcStar("bc_star", filled = false) }
internal val BcStarFilled: ImageVector by lazy { bcStar("bc_star_filled", filled = true) }

private const val BC_STAR_STROKE = 2.2f

private fun bcStar(name: String, filled: Boolean): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = BC_STAR_STROKE,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
            fill = if (filled) SolidColor(Color.Black) else null,
        ) {
            moveTo(12f, 3.86f)
            lineTo(14.33f, 9.66f)
            lineTo(20.56f, 10.08f)
            lineTo(15.77f, 14.08f)
            lineTo(17.29f, 20.14f)
            lineTo(12f, 16.82f)
            lineTo(6.71f, 20.14f)
            lineTo(8.23f, 14.08f)
            lineTo(3.44f, 10.08f)
            lineTo(9.67f, 9.66f)
            close()
        }
    }.build()
