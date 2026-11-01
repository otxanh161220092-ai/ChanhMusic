/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component.liquidtabs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.draw.drawWithContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Scale factor applied to tab items while the pill is pressed. */
val LocalLiquidBottomTabScale = staticCompositionLocalOf<() -> Float> { { 1f } }

/**
 * Drag-with-damping value used by [LiquidBottomTabs]: the value follows the finger, springs to the
 * nearest tab on release, and exposes press progress for the lens and scale effects.
 */
class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    initialValue: Float,
    private val valueRange: ClosedFloatingPointRange<Float>,
    private val visibilityThreshold: Float,
    private val initialScale: Float,
    private val pressedScale: Float,
    private val onDragStarted: DampedDragAnimation.() -> Unit,
    private val onDragStopped: DampedDragAnimation.() -> Unit,
    private val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
) {
    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val pressAnimation = Animatable(0f, 0.001f)

    var targetValue: Float = initialValue
        private set

    val value: Float get() = valueAnimation.value
    val velocity: Float get() = valueAnimation.velocity
    val pressProgress: Float get() = pressAnimation.value
    private val scale: Float get() = lerpFloat(initialScale, pressedScale, pressProgress)
    val scaleX: Float get() = scale
    val scaleY: Float get() = scale

    fun updateValue(newValue: Float) {
        targetValue = newValue.coerceIn(valueRange.start, valueRange.endInclusive)
        animationScope.launch { valueAnimation.snapTo(targetValue) }
    }

    fun animateToValue(newValue: Float) {
        targetValue = newValue.coerceIn(valueRange.start, valueRange.endInclusive)
        animationScope.launch {
            valueAnimation.animateTo(targetValue, spring(1f, 300f, visibilityThreshold))
        }
    }

    private fun animatePress(to: Float) {
        animationScope.launch {
            pressAnimation.animateTo(to, spring(1f, 500f, 0.001f))
        }
    }

    val modifier: Modifier = Modifier
        .pointerInput(animationScope) {
            detectTapGestures(
                onPress = {
                    animatePress(1f)
                    tryAwaitRelease()
                    animatePress(0f)
                },
            )
        }
        .pointerInput(animationScope) {
            detectDragGestures(
                onDragStart = {
                    animatePress(1f)
                    onDragStarted()
                },
                onDragEnd = {
                    animatePress(0f)
                    onDragStopped()
                },
                onDragCancel = {
                    animatePress(0f)
                    onDragStopped()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    onDrag(size, dragAmount)
                },
            )
        }
}

/**
 * White glow that follows the touch point on the tab pill, drawn over the content.
 */
class InteractiveHighlight(
    private val animationScope: CoroutineScope,
    private val position: (size: androidx.compose.ui.geometry.Size, offset: Offset) -> Offset,
) {
    private val pressAnimation = Animatable(0f, 0.001f)
    private var touch = Offset.Unspecified

    private fun animatePress(to: Float) {
        animationScope.launch { pressAnimation.animateTo(to, spring(1f, 500f, 0.001f)) }
    }

    val modifier: Modifier = Modifier.drawWithContent {
        drawContent()
        val progress = pressAnimation.value
        if (progress > 0f) {
            val center = position(size, touch)
            drawHighlight(center, progress)
        }
    }

    val gestureModifier: Modifier = Modifier.pointerInput(animationScope) {
        detectTapGestures(
            onPress = { offset ->
                touch = offset
                animatePress(1f)
                tryAwaitRelease()
                animatePress(0f)
            },
        )
    }
}

private fun DrawScope.drawHighlight(center: Offset, progress: Float) {
    val radius = size.height * 1.5f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = 0.18f * progress), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun lerpFloat(start: Float, stop: Float, fraction: Float): Float =
    start + (stop - start) * fraction

/**
 * Fully rounded pill shape. Must be a [androidx.compose.foundation.shape.CornerBasedShape]:
 * the backdrop lens effect rejects any other shape type.
 */
fun Capsule(): Shape = RoundedCornerShape(percent = 50)
