package com.metrolist.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.component.liquidtabs.DampedDragAnimation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.ln
import kotlin.math.tanh

/**
 * Press-and-drag stretch for glass surfaces. Pressing scales the surface slightly
 * through [DampedDragAnimation] (the Kyant spring the bottom tabs use). Dragging moves
 * the surface with a rubber-band curve: the farther it is pulled, the more resistance,
 * saturating at [maxPx]. Releasing animates it back from its current position.
 */
class GlassStretch internal constructor(
    private val scope: CoroutineScope,
    private val maxPx: Float,
) {
    private var offsetX by mutableFloatStateOf(0f)
    private var offsetY by mutableFloatStateOf(0f)

    // Raw finger distance; the rubber-band curve maps it to the displayed offset.
    private var dragX = 0f
    private var dragY = 0f
    private var settleJob: Job? = null

    private val damped =
        DampedDragAnimation(
            animationScope = scope,
            initialValue = 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.03f,
            onDragStarted = {
                settleJob?.cancel()
                // Continue from the displayed offset, so grabbing mid-release does not jump.
                dragX = inverseRubber(offsetX)
                dragY = inverseRubber(offsetY)
            },
            onDragStopped = { settle() },
            onDrag = { _, drag -> nudge(drag) },
        )

    val scaleX: Float get() = damped.scaleX
    val scaleY: Float get() = damped.scaleY
    val translationX: Float get() = offsetX
    val translationY: Float get() = offsetY
    internal val modifier: Modifier get() = damped.modifier

    /** Rubber band: follows the finger with a slope of [FOLLOW] at rest, then saturates at [maxPx]. */
    private fun rubber(distance: Float): Float = maxPx * tanh(FOLLOW * distance / maxPx)

    private fun inverseRubber(offset: Float): Float {
        val t = (offset / maxPx).coerceIn(-0.98f, 0.98f)
        return maxPx * 0.5f * ln((1f + t) / (1f - t)) / FOLLOW
    }

    private fun nudge(drag: Offset) {
        dragX += drag.x
        dragY += drag.y
        offsetX = rubber(dragX)
        offsetY = rubber(dragY)
    }

    private fun settle() {
        val startX = offsetX
        val startY = offsetY
        settleJob =
            scope.launch {
                coroutineScope {
                    launch {
                        Animatable(startX).animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f)) {
                            offsetX = value
                        }
                    }
                    launch {
                        Animatable(startY).animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f)) {
                            offsetY = value
                        }
                    }
                }
                dragX = 0f
                dragY = 0f
            }
    }

    private companion object {
        const val FOLLOW = 0.6f
    }
}

@Composable
fun rememberGlassStretch(maxDp: Dp = 40.dp): GlassStretch {
    val scope = rememberCoroutineScope()
    val maxPx = with(LocalDensity.current) { maxDp.toPx() }
    return remember(scope, maxPx) { GlassStretch(scope, maxPx) }
}

/** Applies [stretch] to the surface and forwards its press and drag gestures. */
fun Modifier.glassStretch(stretch: GlassStretch): Modifier =
    graphicsLayer {
        scaleX = stretch.scaleX
        scaleY = stretch.scaleY
        translationX = stretch.translationX
        translationY = stretch.translationY
    }.then(stretch.modifier)
