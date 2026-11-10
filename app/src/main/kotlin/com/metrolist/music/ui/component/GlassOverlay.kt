package com.metrolist.music.ui.component

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.matchParentSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Colours for content drawn on glass: black text and icons on the white glass tint. */
fun glassLightScheme(base: ColorScheme): ColorScheme =
    base.copy(
        onSurface = Color.Black,
        onSurfaceVariant = Color.Black.copy(alpha = 0.6f),
    )

/**
 * Records where the app was last touched and closes open glass menus when a press
 * lands outside them. The tracker only observes (Initial pass) and never consumes,
 * so the tapped button still receives its click.
 */
object GlassPressTracker {
    var lastPress: Offset = Offset.Zero
        private set

    class OpenMenu(
        var dismiss: () -> Unit,
    ) {
        var coords: LayoutCoordinates? = null
    }

    private val open = mutableListOf<OpenMenu>()

    fun register(menu: OpenMenu) {
        if (menu !in open) open.add(menu)
    }

    fun unregister(menu: OpenMenu) {
        open.remove(menu)
    }

    fun onPress(position: Offset) {
        lastPress = position
        open.toList().forEach { menu ->
            val c = menu.coords
            if (c != null && c.isAttached && !c.boundsInRoot().contains(position)) {
                menu.dismiss()
            }
        }
    }
}

/** Attach to the root container so every press is recorded and outside taps close menus. */
fun Modifier.glassPressTracker(): Modifier =
    pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            GlassPressTracker.onPress(down.position)
        }
    }

/** One dialog or sheet shown by [GlassAlertDialog]; drawn by [GlassOverlayHost]. */
class GlassOverlayItem(
    var onDismiss: () -> Unit,
) {
    var content: @Composable () -> Unit by mutableStateOf<@Composable () -> Unit>({})
}

object GlassOverlayRegistry {
    val items = mutableStateListOf<GlassOverlayItem>()
}

/** Draws the active glass overlays above the app. Place it last in the root container. */
@Composable
fun BoxScope.GlassOverlayHost() {
    val items = GlassOverlayRegistry.items
    val top = items.lastOrNull()
    BackHandler(enabled = top != null) { top?.onDismiss?.invoke() }
    items.forEach { item ->
        key(item) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.25f))
                        .pointerInput(Unit) { detectTapGestures { item.onDismiss() } },
                contentAlignment = Alignment.Center,
            ) {
                item.content()
            }
        }
    }
}

/**
 * Centered confirmation card in the iOS action-sheet style: glass surface, bold title,
 * message, and stacked full-width pill buttons. Same parameters as AlertDialog, so the
 * call sites only change the name.
 */
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    val item = remember { GlassOverlayItem(onDismissRequest) }
    DisposableEffect(item) {
        GlassOverlayRegistry.items.add(item)
        onDispose { GlassOverlayRegistry.items.remove(item) }
    }
    SideEffect {
        item.onDismiss = onDismissRequest
        item.content = {
            GlassAlertCard(
                modifier = modifier,
                icon = icon,
                title = title,
                text = text,
                confirmButton = confirmButton,
                dismissButton = dismissButton,
            )
        }
    }
}

@Composable
private fun GlassAlertCard(
    modifier: Modifier,
    icon: (@Composable () -> Unit)?,
    title: (@Composable () -> Unit)?,
    text: (@Composable () -> Unit)?,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)?,
) {
    val shape = SmoothCornerShape(28.dp)
    val backdrop = LocalGlassBackdrop.current
    val stretch = rememberGlassStretch(10.dp)
    val visibleState = remember { MutableTransitionState(false).apply { targetState = true } }
    AnimatedVisibility(
        visibleState = visibleState,
        enter = fadeIn(tween(180)) + scaleIn(initialScale = 0.9f, animationSpec = tween(220)),
        exit = fadeOut(tween(120)) + scaleOut(targetScale = 0.9f, animationSpec = tween(120)),
    ) {
        MaterialTheme(colorScheme = glassLightScheme(MaterialTheme.colorScheme)) {
            CompositionLocalProvider(LocalContentColor provides Color.Black) {
                Column(
                    modifier =
                        modifier
                            .width(280.dp)
                            .glassStretch(stretch)
                            .clip(shape)
                            .glassSurface(
                                backdrop = backdrop,
                                shape = shape,
                                fallbackColor = Color.White.copy(alpha = 0.9f),
                                tint = Color.White.copy(alpha = 0.2f),
                                mask = Color.White.copy(alpha = 0.8f),
                            ).pointerInput(Unit) { detectTapGestures { } }
                            .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    icon?.let {
                        it()
                        Spacer(Modifier.height(8.dp))
                    }
                    title?.let {
                        ProvideTextStyle(
                            TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = Color.Black, textAlign = TextAlign.Center),
                        ) { it() }
                    }
                    text?.let {
                        Spacer(Modifier.height(6.dp))
                        ProvideTextStyle(
                            TextStyle(fontSize = 13.sp, color = Color.Black.copy(alpha = 0.7f), textAlign = TextAlign.Center),
                        ) { it() }
                    }
                    Spacer(Modifier.height(16.dp))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        GlassActionPill(confirmButton)
                        dismissButton?.let { GlassActionPill(it) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassActionPill(content: @Composable () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.Black.copy(alpha = 0.06f)),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
