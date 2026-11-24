package com.metrolist.music.ui.component.liquidtabs

import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import com.metrolist.music.R
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.graphics.ImageBitmap
import com.metrolist.music.ui.component.SmoothCornerShape
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.metrolist.music.ui.component.highlightPill
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.clipRect


import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    val isLightTheme = !isSystemInDarkTheme()
    val accentColor = Color(0xFF0A84FF)
    val barFill = ImageBitmap.imageResource(R.drawable.bottom_bar_glass)
    val containerColor = Color.Transparent

    val touchGlowAlpha = remember { Animatable(0f) }
    var touchPos by remember { mutableStateOf(Offset.Zero) }
    val glassBlur = com.metrolist.music.ui.component.LocalGlassBlur.current

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }
        val barHeight = 66f.dp

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        val selectedTabIndexState by rememberUpdatedState(selectedTabIndex)
        val onTabSelectedState by rememberUpdatedState(onTabSelected)
        var currentIndex by remember {
            mutableIntStateOf(selectedTabIndex())
        }
        val dampedDragAnimation = remember(animationScope) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 74f / 64f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(Unit) {
            snapshotFlow { selectedTabIndexState() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelectedState(index)
                }
        }

        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { SmoothCornerShape(999.dp) },
                    effects = {
                        blur(glassBlur.dp.toPx())
                        lens(
                            32f.dp.toPx(),
                            36f.dp.toPx(),
                            chromaticAberration = true,
                        )
                    },
                    highlight = { Highlight.Default.copy(alpha = 0f) },
                    shadow = { Shadow(alpha = 0.2f) },
                    innerShadow = { InnerShadow(radius = 10f.dp, alpha = 0.3f) },
                    onDrawSurface = {
                        drawImage(barFill, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                    },
                )
                .then(dampedDragAnimation.modifier)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        touchPos = down.position
                        animationScope.launch {
                            touchGlowAlpha.animateTo(1f, spring(1f, 400f, 0.001f))
                        }
                        var pressed = true
                        while (pressed) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            touchPos = change.position
                            pressed = change.pressed
                        }
                        animationScope.launch {
                            touchGlowAlpha.animateTo(0f, spring(1f, 400f, 0.001f))
                        }
                    }
                }
                .height(barHeight)
                .fillMaxWidth()
                .padding(4f.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            // Selected-tab overlay: same content tinted blue, clipped to the cell
            // under the (continuously dragged) selection position.
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawWithContent {
                        val pad = 4.dp.toPx()
                        val tabW = (size.width - 2 * pad) / tabsCount
                        val v = dampedDragAnimation.value
                        val left = if (isLtr) pad + v * tabW
                        else size.width - pad - (v + 1f) * tabW
                        clipRect(left, 0f, left + tabW, size.height) {
                            this@drawWithContent.drawContent()
                        }
                    }
                    .height(barHeight)
                    .fillMaxWidth()
                    .padding(4f.dp)
                    .graphicsLayer(colorFilter = ColorFilter.tint(accentColor)),
                verticalAlignment = Alignment.CenterVertically,
                content = {
                    CompositionLocalProvider(LocalLiquidBottomTabScale provides { 1f }) {
                        content()
                    }
                }
            )
        }

        // Touch glow: masked to the bar capsule, drawn above the tabs.
        Box(
            Modifier
                .matchParentSize()
                .clip(SmoothCornerShape(999.dp))
                .drawWithContent {
                    drawContent()
                    val a = touchGlowAlpha.value
                    if (a > 0f) {
                        val radius = size.height * 1.2f
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(Color.White.copy(0.8f * a), Color.Transparent),
                                center = touchPos,
                                radius = radius
                            ),
                            radius = radius,
                            center = touchPos,
                            blendMode = BlendMode.Overlay
                        )
                    }
                }
        )

    }
}
