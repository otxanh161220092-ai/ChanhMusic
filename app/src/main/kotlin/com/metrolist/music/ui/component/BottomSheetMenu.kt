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
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * Menu opened from a button. It is a liquid glass popup anchored at the position the
 * user tapped (recorded by [glassPressTracker] at the app root), not a bottom sheet.
 * A press outside the popup closes it, and Back closes it too.
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

    val entry = remember { GlassPressTracker.OpenMenu(close) }
    entry.dismiss = close
    DisposableEffect(state.isVisible) {
        if (state.isVisible) GlassPressTracker.register(entry) else GlassPressTracker.unregister(entry)
        onDispose { GlassPressTracker.unregister(entry) }
    }

    // Light dim over the screen. It has no pointer input, so taps still reach the outside-tap closer.
    AnimatedVisibility(
        visibleState = visibleState,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(tween(160)),
        exit = fadeOut(tween(140)),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val popupWidth = 280.dp
        val popupWidthPx = with(density) { popupWidth.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val gapPx = with(density) { 8.dp.toPx() }
        val screenWPx = with(density) { maxWidth.toPx() }
        val screenHPx = with(density) { maxHeight.toPx() }
        // Captured when the menu opens: the press that tapped the button.
        val anchor = remember(state.isVisible) { GlassPressTracker.lastPress }
        var popupHeightPx by remember { mutableFloatStateOf(0f) }

        val shape = SmoothCornerShape(24.dp)
        val backdrop = LocalGlassBackdrop.current
        val stretch = rememberGlassStretch()

        AnimatedVisibility(
            visibleState = visibleState,
            modifier =
                Modifier.graphicsLayer {
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
                    translationX = left
                    translationY = top
                },
            enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.85f, transformOrigin = TransformOrigin(0f, 0f), animationSpec = tween(220)),
            exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.85f, transformOrigin = TransformOrigin(0f, 0f), animationSpec = tween(120)),
        ) {
            Column(
                modifier =
                    Modifier
                        .width(popupWidth)
                        .heightIn(max = maxHeight * 0.7f)
                        .onSizeChanged { popupHeightPx = it.height.toFloat() }
                        .onGloballyPositioned { entry.coords = it }
                        .glassStretch(stretch)
                        .clip(shape)
                        .glassSurface(
                            backdrop = backdrop,
                            shape = shape,
                            fallbackColor = Color.White.copy(alpha = 0.9f),
                            tint = Color.White.copy(alpha = 0.2f),
                            mask = Color.White.copy(alpha = 0.8f),
                        ).verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
            ) {
                MaterialTheme(colorScheme = glassLightScheme(MaterialTheme.colorScheme)) {
                    CompositionLocalProvider(LocalContentColor provides Color.Black) {
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
