/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import com.metrolist.music.ui.component.highlightBorder
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import com.metrolist.music.bcmix.audio.VocalCancel
import com.metrolist.music.ui.component.smoothCornerShape
import kotlinx.coroutines.delay

/**
 * The karaoke button, at the right of the lyrics controls. It switches vocal reduction on
 * and off. While Automix is on it does nothing but show a glass hint, because Automix and
 * karaoke cannot run together; turning Automix on also turns karaoke off.
 */
/**
 * The glass circle behind the lyrics controls: a white veil blended in Overlay mode, with the
 * same highlight ring the other glass buttons use. [active] makes the veil stronger.
 */
fun Modifier.playerGlassCircle(active: Boolean): Modifier =
    this
        .size(44.dp)
        .clip(CircleShape)
        .drawBehind {
            drawRect(Color.White.copy(alpha = 0.14f))
            drawRect(
                Color.White.copy(alpha = if (active) 0.55f else 0.3f),
                blendMode = BlendMode.Overlay,
            )
        }
        .highlightBorder()

@Composable
fun KaraokeToggle(automixOn: Boolean, tint: Color, modifier: Modifier = Modifier) {
    val on by VocalCancel.state.collectAsStateWithLifecycle()
    var hintShown by remember { mutableStateOf(false) }

    LaunchedEffect(automixOn) {
        if (automixOn) {
            VocalCancel.set(false)
        }
    }
    // The hint closes by itself after a few seconds, like the Apple Music tip it mirrors.
    LaunchedEffect(hintShown) {
        if (hintShown) {
            delay(5000)
            hintShown = false
        }
    }

    val toggle: () -> Unit = {
        when {
            automixOn -> hintShown = !hintShown
            else -> VocalCancel.set(!on)
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .playerGlassCircle(on)
            .clickable(onClick = toggle),
    ) {
        Icon(
            painter = painterResource(R.drawable.mic),
            contentDescription = "Karaoke",
            tint = if (on) Color.Black.copy(alpha = 0.8f) else tint.copy(alpha = 0.6f),
        )
    }

    if (hintShown) {
        KaraokeHint(onDismiss = { hintShown = false })
    }
}

/**
 * The glass hint shown over the lyrics when Automix blocks karaoke. Its tail points down at the
 * button; the popup is placed above the button and closes on its X or a tap outside.
 */
@Composable
private fun KaraokeHint(onDismiss: () -> Unit) {
    val density = LocalDensity.current
    var heightPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(density) { 12.dp.toPx() }
    val shape = smoothCornerShape(24.dp)

    Popup(
        alignment = Alignment.TopEnd,
        offset = IntOffset(0, -(heightPx + gapPx).toInt()),
        onDismissRequest = onDismiss,
        properties = PopupProperties(),
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            modifier = Modifier.width(300.dp),
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                modifier = Modifier
                    .onSizeChanged { heightPx = it.height.toFloat() }
                    .clip(shape)
                    .background(Color(0xCC2E2E2E))
                    .border(0.5.dp, Color.White.copy(alpha = 0.25f), shape)
                    .padding(start = 16.dp, top = 14.dp, end = 10.dp, bottom = 16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.mic),
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(top = 2.dp).size(28.dp),
                )
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Cần tắt Automix",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Karaoke tách giọng hát để bạn hát theo, nên phải tắt Automix trước khi mở.",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        textAlign = TextAlign.Start,
                    )
                }
                Text(
                    text = "✕",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 18.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clickable(onClick = onDismiss),
                )
            }
            // The tail, pointing down at the button's centre.
            Canvas(modifier = Modifier.padding(end = 18.dp).size(width = 16.dp, height = 9.dp)) {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(path, color = Color(0xCC2E2E2E))
            }
        }
    }
}
