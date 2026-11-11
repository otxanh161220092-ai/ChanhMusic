/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

/**
 * Menu rows separated by thin lines, with no boxed buttons. A pill highlight follows the
 * finger over the rows while it is held down. Rows are opened by their own click on release.
 * [selectable] decides which rows can be highlighted.
 */
@Composable
fun GlassHighlightGroup(
    count: Int,
    selectable: (Int) -> Boolean,
    content: @Composable (rowModifier: (Int) -> Modifier) -> Unit,
) {
    val density = LocalDensity.current
    var rects by remember(count) { mutableStateOf<List<Rect?>>(List(count) { null }) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    var highlight by remember { mutableIntStateOf(-1) }
    val selectableState = rememberUpdatedState(selectable)
    val tint = LocalContentColor.current.copy(alpha = 0.12f)

    val indexAt: (Offset) -> Int = { p ->
        rects.indices.firstOrNull { i ->
            selectableState.value(i) && rects[i]?.contains(p) == true
        } ?: -1
    }

    val rowModifier: (Int) -> Modifier = { i ->
        Modifier.onGloballyPositioned { coords ->
            val r = coords.boundsInRoot()
            if (rects.getOrNull(i) != r) {
                rects = rects.toMutableList().also { it[i] = r }
            }
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .onGloballyPositioned { origin = it.positionInRoot() }
                .pointerInput(count) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        highlight = indexAt(origin + down.position)
                        var pressed: Boolean
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.first()
                            highlight = indexAt(origin + change.position)
                            pressed = event.changes.any { it.pressed }
                        } while (pressed)
                        highlight = -1
                    }
                },
    ) {
        val rect = rects.getOrNull(highlight)
        if (rect != null) {
            val top by animateFloatAsState(targetValue = rect.top - origin.y, label = "glassHighlightTop")
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .graphicsLayer { translationY = top }
                        .height(with(density) { rect.height.toDp() })
                        .clip(RoundedCornerShape(12.dp))
                        .background(tint),
            )
        }
        content(rowModifier)
    }
}

/** Thin separator between menu rows. */
@Composable
fun GlassMenuDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(LocalContentColor.current.copy(alpha = 0.15f)),
    )
}

@Composable
fun Material3MenuGroup(
    items: List<Material3MenuItemData>
) {
    GlassHighlightGroup(
        count = items.size,
        selectable = { i -> items[i].onClick != null },
    ) { rowModifier ->
        Column(modifier = Modifier.fillMaxWidth()) {
            items.forEachIndexed { index, item ->
                if (index > 0) GlassMenuDivider()
                Material3MenuItemRow(item = item, modifier = rowModifier(index))
            }
        }
    }
}

@Composable
private fun Material3MenuItemRow(
    item: Material3MenuItemData,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(
                enabled = item.onClick != null,
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = { item.onClick?.invoke() }
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        item.icon?.let { icon ->
            icon()
            Spacer(modifier = Modifier.width(16.dp))
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            ProvideTextStyle(MaterialTheme.typography.titleMedium) {
                item.title()
            }

            item.description?.let { desc ->
                Spacer(modifier = Modifier.height(2.dp))
                ProvideTextStyle(
                    MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    desc()
                }
            }
        }
        item.trailingContent?.let { trailing ->
            Spacer(modifier = Modifier.width(8.dp))
            trailing()
        }
    }
}

data class Material3MenuItemData(
    val icon: (@Composable () -> Unit)? = null,
    val title: @Composable () -> Unit,
    val description: (@Composable () -> Unit)? = null,
    val onClick: (() -> Unit)? = null,
    val cardColors: androidx.compose.material3.CardColors? = null,
    val trailingContent: (@Composable () -> Unit)? = null
)
