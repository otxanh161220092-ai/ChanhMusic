package com.metrolist.music.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.ui.component.liquidtabs.DampedDragAnimation
import kotlinx.coroutines.CoroutineScope
import kotlin.math.tanh
import kotlinx.coroutines.launch

/**
 * Press-and-drag stretch for glass surfaces. Pressing scales the surface slightly
 * through [DampedDragAnimation] (the same Kyant spring the bottom tabs use); dragging
 * pulls the surface toward the finger with damping, and releasing springs it back
 * with a small overshoot.
 */
class GlassStretch internal constructor(
    private val scope: CoroutineScope,
    private val maxPx: Float,
) {
    private val offsetX = Animatable(0f)
    private val offsetY = Animatable(0f)

    private val damped =
        DampedDragAnimation(
            animationScope = scope,
            initialValue = 0f,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.03f,
            onDragStarted = {},
            onDragStopped = { settle() },
            onDrag = { _, drag -> nudge(drag) },
        )

    val scaleX: Float get() = damped.scaleX
    val scaleY: Float get() = damped.scaleY
    val translationX: Float get() = offsetX.value
    val translationY: Float get() = offsetY.value
    internal val modifier: Modifier get() = damped.modifier

    private var dragTotal = Offset.Zero

    /** Rubber band: the surface follows the finger, but resistance grows with distance, so it saturates at [maxPx]. */
    private fun rubber(distance: Float): Float = maxPx * tanh(FOLLOW * distance / maxPx)

    private fun nudge(drag: Offset) {
        dragTotal += drag
        scope.launch {
            offsetX.snapTo(rubber(dragTotal.x))
            offsetY.snapTo(rubber(dragTotal.y))
        }
    }

    private fun settle() {
        dragTotal = Offset.Zero
        scope.launch {
            launch { offsetX.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
            launch { offsetY.animateTo(0f, spring(dampingRatio = 0.4f, stiffness = 400f)) }
        }
    }

    private companion object {
        const val FOLLOW = 0.6f
    }
}

@Composable
fun rememberGlassStretch(maxDp: Dp = 96.dp): GlassStretch {
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
