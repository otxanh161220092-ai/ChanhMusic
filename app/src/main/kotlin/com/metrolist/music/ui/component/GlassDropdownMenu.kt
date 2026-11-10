package com.metrolist.music.ui.component

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.metrolist.music.R
import kotlin.math.roundToInt

/** One row in a [GlassDropdownMenu]. */
data class GlassMenuEntry(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit,
)

/**
 * Dropdown menu drawn as liquid glass over the app backdrop, the same surface
 * as the bottom bar and mini player. It opens from the top-right corner, and
 * a highlight pill slides to whichever row the finger is over.
 */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    entries: List<GlassMenuEntry>,
    modifier: Modifier = Modifier,
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = expanded

    if (visibleState.currentState || visibleState.targetState) {
        Popup(
            alignment = Alignment.TopEnd,
            offset = IntOffset(x = 0, y = 0),
            onDismissRequest = onDismissRequest,
            properties = PopupProperties(focusable = true),
        ) {
            AnimatedVisibility(
                visibleState = visibleState,
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
                    onEntryClick = {
                        it.onClick()
                        onDismissRequest()
                    },
                    modifier = modifier,
                )
            }
        }
    }
}

@Composable
private fun GlassMenuBody(
    entries: List<GlassMenuEntry>,
    onEntryClick: (GlassMenuEntry) -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val backdrop = LocalGlassBackdrop.current
    val fallback = MaterialTheme.colorScheme.surfaceContainerHigh
    val rowTops = remember { mutableStateMapOf<Int, Float>() }
    val rowHeights = remember { mutableStateMapOf<Int, Float>() }
    var highlight by remember { mutableStateOf<Int?>(null) }
    val density = LocalDensity.current

    val indexAt: (Float) -> Int? = { y ->
        entries.indices.firstOrNull { i ->
            val top = rowTops[i]
            val h = rowHeights[i]
            top != null && h != null && y >= top && y < top + h
        }
    }

    val highlightIndex = highlight
    val pillTop by animateFloatAsState(
        targetValue = highlightIndex?.let { rowTops[it] } ?: 0f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "pillTop",
    )
    val pillHeightPx = highlightIndex?.let { rowHeights[it] } ?: 0f
    val pillAlpha by animateFloatAsState(
        targetValue = if (highlightIndex != null) 1f else 0f,
        animationSpec = tween(120),
        label = "pillAlpha",
    )

    Column(
        modifier =
            modifier
                .width(IntrinsicSize.Max)
                .widthIn(min = 172.dp)
                .clip(shape)
                .then(
                    if (backdrop != null) {
                        Modifier.glassSurface(backdrop, shape, fallback, Color.White.copy(alpha = 0.2f))
                    } else {
                        Modifier.background(fallback, shape)
                    },
                ).padding(vertical = 8.dp),
    ) {
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
            if (highlightIndex != null) {
                Box(
                    modifier =
                        Modifier
                            .offset { IntOffset(0, pillTop.roundToInt()) }
                            .fillMaxWidth()
                            .height(with(density) { pillHeightPx.toDp() })
                            .padding(horizontal = 8.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.18f * pillAlpha)),
                )
            }
            Column {
                entries.forEachIndexed { index, entry ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .padding(horizontal = 20.dp)
                                .onGloballyPositioned { coords ->
                                    rowTops[index] = coords.positionInParent().y
                                    rowHeights[index] = coords.size.height.toFloat()
                                },
                    ) {
                        Text(
                            text = entry.label,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Icon(
                            painter =
                                painterResource(
                                    if (entry.selected) {
                                        R.drawable.radio_button_checked
                                    } else {
                                        R.drawable.radio_button_unchecked
                                    },
                                ),
                            contentDescription = null,
                        )
                    }
                }
            }
        }
    }
}
