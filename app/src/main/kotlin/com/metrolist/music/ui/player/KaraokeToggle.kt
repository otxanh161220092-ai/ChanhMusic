/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.player

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.sqrt

/** Whether karaoke is on. Shared so the lyrics panel can read it. */
object KaraokeState {
    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    fun set(on: Boolean) {
        _active.value = on
    }
}

/**
 * Listens to the microphone and publishes how loud the user is singing, 0..1.
 * Uses the voice-recognition source, which skips the automatic gain control that
 * would otherwise flatten the level. Nothing is recorded or stored.
 */
class KaraokeMicMeter {
    private val _level = MutableStateFlow(0f)
    val level: StateFlow<Float> = _level.asStateFlow()
    private var job: Job? = null

    @SuppressLint("MissingPermission")
    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            val rate = 16000
            val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuffer <= 0) return@launch
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                rate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2,
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                return@launch
            }
            val buffer = ShortArray(rate / 20)
            try {
                record.startRecording()
                var shown = 0f
                while (isActive) {
                    val n = record.read(buffer, 0, buffer.size)
                    if (n <= 0) continue
                    var sum = 0.0
                    for (i in 0 until n) {
                        val v = buffer[i] / 32768.0
                        sum += v * v
                    }
                    // Singing is quiet next to speech; this scale fills the bar at a strong voice.
                    val target = (sqrt(sum / n).toFloat() * 6f).coerceIn(0f, 1f)
                    // Rises at once, falls slowly, so the bar reads as a level rather than flickering.
                    shown = maxOf(target, shown * 0.9f)
                    _level.value = shown
                }
            } finally {
                runCatching { record.stop() }
                record.release()
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _level.value = 0f
    }
}

private fun hasMicPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

/**
 * The karaoke button, placed at the right of the lyrics controls. Off, it is a circle like the
 * translate button. On, it turns into a tall pill that fills with the user's singing level,
 * as in Apple Music. Tapping the pill turns karaoke off. Without microphone permission it still
 * switches on; the pill just stays empty.
 */
@Composable
fun KaraokeToggle(tint: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val on by KaraokeState.active.collectAsStateWithLifecycle()
    val meter = remember { KaraokeMicMeter() }
    val level by meter.level.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        KaraokeState.set(true)
    }
    val toggle: () -> Unit = {
        if (on) {
            KaraokeState.set(false)
        } else if (hasMicPermission(context)) {
            KaraokeState.set(true)
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(on) {
        if (on && hasMicPermission(context)) meter.start(this) else meter.stop()
    }
    DisposableEffect(Unit) {
        onDispose { meter.stop() }
    }

    if (on) {
        val shape = RoundedCornerShape(28.dp)
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = modifier
                .width(56.dp)
                .height(120.dp)
                .clip(shape)
                .background(Color.White.copy(alpha = 0.15f))
                .clickable(onClick = toggle),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.15f + 0.85f * level)
                    .background(Color.White.copy(alpha = 0.9f)),
            )
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = "Karaoke",
                tint = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 12.dp).size(22.dp),
            )
        }
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.12f))
                .clickable(onClick = toggle),
        ) {
            Icon(
                painter = painterResource(R.drawable.mic),
                contentDescription = "Karaoke",
                tint = tint.copy(alpha = 0.6f),
            )
        }
    }
}
