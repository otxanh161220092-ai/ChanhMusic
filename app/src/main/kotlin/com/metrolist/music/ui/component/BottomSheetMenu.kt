/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetDefaults
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.SheetState
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState as rememberMaterialBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.material3.LocalContentColor
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.rememberTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

val LocalMenuState = compositionLocalOf { MenuState() }

@Stable
class MenuState(
    isVisible: Boolean = false,
    content: @Composable ColumnScope.() -> Unit = {},
) {
    var isVisible by mutableStateOf(isVisible)
    var content by mutableStateOf(content)

    fun show(content: @Composable ColumnScope.() -> Unit) {
        isVisible = true
        this.content = content
    }

    fun dismiss() {
        isVisible = false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimatedBottomSheet(
    isVisible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    sheetState: SheetState = rememberMaterialBottomSheetState(initialValue = SheetValue.Hidden),
    sheetMaxWidth: Dp = BottomSheetDefaults.SheetMaxWidth,
    shape: Shape = BottomSheetDefaults.ExpandedShape,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    contentColor: Color = contentColorFor(containerColor),
    tonalElevation: Dp = 0.dp,
    scrimColor: Color = BottomSheetDefaults.ScrimColor,
    dragHandle: @Composable (() -> Unit)? = { BottomSheetDefaults.DragHandle() },
    contentWindowInsets: @Composable () -> WindowInsets = { BottomSheetDefaults.modalWindowInsets },
    properties: ModalBottomSheetProperties = ModalBottomSheetDefaults.properties,
    content: @Composable ColumnScope.() -> Unit,
) {
    var lastContent by remember { mutableStateOf(content) }

    LaunchedEffect(content) {
        if (isVisible) {
            lastContent = content
        }
    }

    LaunchedEffect(isVisible) {
        if (isVisible) {
            sheetState.show()
        } else {
            sheetState.hide()
        }
    }

    if (!sheetState.isVisible && !isVisible) {
        return
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        sheetState = sheetState,
        sheetMaxWidth = sheetMaxWidth,
        shape = shape,
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        scrimColor = scrimColor,
        dragHandle = dragHandle,
        contentWindowInsets = contentWindowInsets,
        properties = properties,
        content = lastContent,
    )
}

/**
 * Menu opened from a button. A liquid glass popup grows out of the tapped point and shrinks
 * back into it. While open, a transparent layer covers the screen, so a tap outside closes
 * the menu and stops there instead of reaching what is underneath.
 */
@Composable
fun BoxScope.BottomSheetMenu(
    state: MenuState,
) {
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val close: () -> Unit = {
        focusManager.clearFocus()
        state.dismiss()
    }
    BackHandler(enabled = state.isVisible, onBack = close)

    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = state.isVisible
    val present = visibleState.currentState || visibleState.targetState

    val entry = remember { GlassPressTracker.OpenMenu(close) }
    entry.dismiss = close
    DisposableEffect(state.isVisible) {
        if (state.isVisible) GlassPressTracker.register(entry) else GlassPressTracker.unregister(entry)
        onDispose { GlassPressTracker.unregister(entry) }
    }

    val transition = rememberTransition(visibleState, label = "glassMenu")
    val progress by transition.animateFloat(
        transitionSpec = { tween(if (targetState) 220 else 160) },
        label = "glassMenuProgress",
    ) { if (it) 1f else 0f }

    if (present) {
        // Covers the screen while open: taps outside close the menu and are consumed here.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = progress }
                    .background(Color.Black.copy(alpha = 0.18f))
                    .pointerInput(Unit) { detectTapGestures { close() } },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val popupWidth = 280.dp
        val popupWidthPx = with(density) { popupWidth.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val screenWPx = with(density) { maxWidth.toPx() }
        val screenHPx = with(density) { maxHeight.toPx() }
        // Captured when the menu opens: the press that tapped the button.
        // Read the tap point only when opening; closing keeps the same point so it shrinks back into the button.
        val openAnchor: OpenAnchor = remember { OpenAnchor() }
        val anchor: Offset = remember(state.isVisible) {
            if (state.isVisible) {
                openAnchor.point = GlassPressTracker.lastPress
            }
            openAnchor.point
        }
        var popupHeightPx by remember { mutableFloatStateOf(0f) }

        val shape = SmoothCornerShape(24.dp)
        val backdrop = remember(state.isVisible) { GlassBackdropOverride.active } ?: LocalGlassBackdrop.current
        val palette = rememberGlassPalette()
        val stretch = rememberGlassStretch()

        if (present) {
            Column(
                modifier =
                    Modifier
                        .width(popupWidth)
                        .heightIn(max = maxHeight * 0.8f)
                        .onSizeChanged { popupHeightPx = it.height.toFloat() }
                        .onGloballyPositioned { entry.coords = it }
                        .graphicsLayer {
                            val left =
                                (anchor.x - popupWidthPx / 2f).coerceIn(
                                    marginPx,
                                    (screenWPx - popupWidthPx - marginPx).coerceAtLeast(marginPx),
                                )
                            val below = anchor.y + gapPx
                            val rawTop =
                                if (below + popupHeightPx + marginPx <= screenHPx) below else anchor.y - gapPx - popupHeightPx
                            val top =
                                rawTop.coerceIn(
                                    marginPx,
                                    (screenHPx - popupHeightPx - marginPx).coerceAtLeast(marginPx),
                                )
                            // Scale pivot is the tapped point, expressed in the popup's own coordinates.
                            transformOrigin =
                                TransformOrigin(
                                    ((anchor.x - left) / popupWidthPx).coerceIn(0f, 1f),
                                    if (popupHeightPx > 0f) ((anchor.y - top) / popupHeightPx).coerceIn(0f, 1f) else 0f,
                                )
                            val s = 0.3f + 0.7f * progress
                            scaleX = s
                            scaleY = s
                            alpha = progress
                            translationX = left
                            translationY = top
                        }
                        .glassStretch(stretch)
                        .clip(shape)
                        .glassSurface(
                            backdrop = backdrop,
                            shape = shape,
                            fallbackColor = palette.fallback,
                            tint = palette.tint,
                            mask = palette.mask,
                            lensDp = 28f,
                            lensAmount = 2.5f,
                        ).padding(vertical = 8.dp),
            ) {
                MaterialTheme(colorScheme = glassScheme(MaterialTheme.colorScheme, palette)) {
                    CompositionLocalProvider(LocalContentColor provides palette.content) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                        ) {
                            state.content(this)
                        }
                    }
                }
            }
        }
    }
}

private class OpenAnchor {
    var point: Offset = Offset.Zero
}
