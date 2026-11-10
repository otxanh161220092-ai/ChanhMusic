package com.metrolist.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.clickable
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.R

private val RowHeight = 48.dp
private val DestructiveColor = Color(0xFFFF3B30)

/**
 * One item in a [GlassDropdownMenu].
 *
 * - [isHeader]: a section title, drawn small and grey, not clickable.
 * - [isDivider]: a thin separator line.
 * - [icon] draws on the left; [shortcut] text draws on the right.
 * - [selected] adds a radio mark (null hides it); [hasSubmenu] adds a chevron.
 * - [destructive] colours the row red; [enabled] = false greys it out.
 */
data class GlassMenuEntry(
    val label: String,
    val onClick: () -> Unit = {},
    @DrawableRes val icon: Int? = null,
    val selected: Boolean? = null,
    val shortcut: String? = null,
    val hasSubmenu: Boolean = false,
    val destructive: Boolean = false,
    val enabled: Boolean = true,
    val isHeader: Boolean = false,
    val isDivider: Boolean = false,
)

/** One icon button in the top row of a [GlassDropdownMenu]. */
data class GlassTopAction(
    val label: String,
    @DrawableRes val icon: Int,
    val selected: Boolean = false,
    val destructive: Boolean = false,
    val onClick: () -> Unit,
)

/**
 * Dropdown menu drawn as liquid glass over the app backdrop, the same surface
 * as the bottom bar and mini player. It is drawn inside the caller's layout
 * (a BoxScope), not in a popup window, so the glass samples the right region.
 * It opens from the top-right corner. Back closes it.
 */
@Composable
fun BoxScope.GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<GlassMenuEntry>,
    modifier: Modifier = Modifier,
    topActions: List<GlassTopAction> = emptyList(),
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded
    BackHandler(enabled = expanded, onBack = onDismissRequest)
    val entry = remember { GlassPressTracker.OpenMenu(onDismissRequest) }
    entry.dismiss = onDismissRequest
    DisposableEffect(expanded) {
        if (expanded) GlassPressTracker.register(entry) else GlassPressTracker.unregister(entry)
        onDispose { GlassPressTracker.unregister(entry) }
    }
    val stretch = rememberGlassStretch()

    AnimatedVisibility(
        visibleState = visibleState,
        modifier = modifier.align(Alignment.TopEnd),
        enter =
            fadeIn(tween(180)) +
                scaleIn(
                    initialScale = 0.9f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow),
                ),
        exit =
            fadeOut(tween(120)) +
                scaleOut(
                    targetScale = 0.9f,
                    transformOrigin = TransformOrigin(1f, 0f),
                    animationSpec = tween(120),
                ),
    ) {
        GlassMenuBody(
            entries = entries,
            topActions = topActions,
            stretch = stretch,
            onPlaced = { entry.coords = it },
            onEntryClick = {
                it.onClick()
                onDismissRequest()
            },
            onTopActionClick = {
                it.onClick()
                onDismissRequest()
            },
        )
    }
}

@Composable
private fun GlassMenuBody(
    entries: List<GlassMenuEntry>,
    topActions: List<GlassTopAction>,
    stretch: GlassStretch,
    onPlaced: (LayoutCoordinates) -> Unit,
    onEntryClick: (GlassMenuEntry) -> Unit,
    onTopActionClick: (GlassTopAction) -> Unit,
) {
    val shape = SmoothCornerShape(24.dp)
    val backdrop = LocalGlassBackdrop.current
    val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val density = LocalDensity.current
    val rowHeightPx = with(density) { RowHeight.toPx() }

    // Measured heights of each entry, so headers and dividers take their real size.
    val heights = remember(entries) { mutableStateOf(List(entries.size) { 0f }) }
    val tops = heights.value.runningFold(0f) { acc, h -> acc + h }
    var highlight by remember { mutableStateOf<Int?>(null) }

    val indexAt: (Float) -> Int? = { y ->
        entries.indices.firstOrNull { i ->
            val e = entries[i]
            val top = tops.getOrNull(i) ?: return@firstOrNull false
            val h = heights.value.getOrNull(i) ?: 0f
            e.enabled && !e.isHeader && !e.isDivider && h > 0f && y >= top && y < top + h
        }
    }

    Column(
        modifier =
            Modifier
                .onGloballyPositioned(onPlaced)
                .width(240.dp)
                .glassStretch(stretch)
                .clip(shape)
                .then(
                    if (backdrop != null) {
                        Modifier.glassSurface(backdrop, shape, fallback, Color.White.copy(alpha = 0.2f), mask = Color.White.copy(alpha = 0.8f))
                    } else {
                        Modifier.background(fallback, shape)
                    },
                ).padding(vertical = 8.dp),
    ) {
        if (topActions.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                topActions.forEach { action ->
                    val tint = if (action.destructive) DestructiveColor else Color.Black
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                            Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (action.selected) Color.Black.copy(alpha = 0.1f) else Color.Transparent)
                                .clickable { onTopActionClick(action) }
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        Icon(painter = painterResource(action.icon), contentDescription = null, tint = tint)
                        Text(text = action.label, fontSize = 12.sp, color = tint, maxLines = 1)
                    }
                }
            }
            GlassDivider()
        }

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .pointerInput(entries) {
                        awaitEachGesture {
                            val down = awaitFirstDown()
                            highlight = indexAt(down.position.y)
                            var releasedOver: Int? = null
                            var pressed = true
                            while (pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.first()
                                if (change.pressed) {
                                    highlight = indexAt(change.position.y)
                                    change.consume()
                                } else {
                                    releasedOver = indexAt(change.position.y)
                                    pressed = false
                                }
                            }
                            highlight = null
                            releasedOver?.let { onEntryClick(entries[it]) }
                        }
                    },
        ) {
            val hi = highlight
            if (hi != null) {
                val pillTop by animateFloatAsState(
                    targetValue = tops.getOrElse(hi) { 0f },
                    animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
                    label = "pillTop",
                )
                Box(
                    modifier =
                        Modifier
                            .graphicsLayer { translationY = pillTop }
                            .fillMaxWidth()
                            .height(with(density) { (heights.value.getOrElse(hi) { rowHeightPx }).toDp() })
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.Black.copy(alpha = 0.1f)),
                )
            }
            Column {
                entries.forEachIndexed { index, entry ->
                    when {
                        entry.isDivider -> GlassDivider(
                            modifier = Modifier.onSizeChanged { s ->
                                updateHeight(heights, index, s.height.toFloat())
                            },
                        )
                        entry.isHeader -> Text(
                            text = entry.label,
                            fontSize = 13.sp,
                            color = Color.Black.copy(alpha = 0.45f),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 4.dp)
                                    .onSizeChanged { s -> updateHeight(heights, index, s.height.toFloat()) },
                        )
                        else -> GlassRow(
                            entry = entry,
                            modifier = Modifier.onSizeChanged { s ->
                                updateHeight(heights, index, s.height.toFloat())
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassRow(
    entry: GlassMenuEntry,
    modifier: Modifier,
) {
    val tint = when {
        !entry.enabled -> Color.Black.copy(alpha = 0.3f)
        entry.destructive -> DestructiveColor
        else -> Color.Black
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth().height(RowHeight).padding(horizontal = 16.dp),
    ) {
        entry.icon?.let {
            Icon(painter = painterResource(it), contentDescription = null, tint = tint)
            Spacer(Modifier.width(14.dp))
        }
        Text(
            text = entry.label,
            fontSize = 16.sp,
            color = tint,
            modifier = Modifier.weight(1f),
        )
        entry.shortcut?.let {
            Text(text = it, fontSize = 14.sp, color = Color.Black.copy(alpha = 0.45f))
            Spacer(Modifier.width(8.dp))
        }
        entry.selected?.let { selected ->
            Icon(
                painter = painterResource(
                    if (selected) R.drawable.radio_button_checked else R.drawable.radio_button_unchecked,
                ),
                contentDescription = null,
                tint = Color.Black,
            )
        }
        if (entry.hasSubmenu) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.width(18.dp),
            )
        }
    }
}

@Composable
private fun GlassDivider(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(1.dp)
                .background(Color.Black.copy(alpha = 0.12f)),
    )
}

private fun updateHeight(
    heights: androidx.compose.runtime.MutableState<List<Float>>,
    index: Int,
    value: Float,
) {
    val current = heights.value
    if (index < current.size && current[index] != value) {
        heights.value = current.toMutableList().also { it[index] = value }
    }
}
