package com.metrolist.music.ui.screens.library

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import com.metrolist.music.LocalPlayerAwareWindowInsets
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Back for the library pages: a "<" chevron on its own line, above the filters. */
@Composable
internal fun LibraryBackButton(onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(start = 8.dp),
    ) {
        Icon(
            imageVector = LibraryBackChevron,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(28.dp),
        )
    }
}

internal val LibraryBackChevron: ImageVector by lazy {
    ImageVector.Builder(
        name = "library_back_chevron",
        defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f,
    ).apply {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2.4f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(15f, 5f)
            lineTo(8f, 12f)
            lineTo(15f, 19f)
        }
    }.build()
}

/**
 * Content padding for the library pages: the status bar and the side and bottom
 * insets, without the app bar's height. These pages show no app bar, so reserving
 * that space left an empty band above the back button.
 */
@Composable
internal fun libraryPagePadding(): PaddingValues {
    val insets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val layout = LocalLayoutDirection.current
    return PaddingValues(
        start = insets.calculateLeftPadding(layout),
        end = insets.calculateRightPadding(layout),
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        bottom = insets.calculateBottomPadding(),
    )
}
