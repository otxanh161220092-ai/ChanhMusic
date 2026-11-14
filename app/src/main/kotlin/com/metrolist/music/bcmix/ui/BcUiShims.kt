package com.metrolist.music.bcmix.ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.palette.graphics.Palette
import coil3.Image
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.metrolist.music.R
import com.metrolist.music.bcmix.settings.AppSettings
import com.metrolist.music.bcmix.settings.MixBlend
import com.metrolist.music.bcmix.settings.MixTransitionView
import com.metrolist.music.bcmix.settings.SmartAnalysis
import com.metrolist.music.ui.theme.PlayerColorExtractor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

object BcStrings {
    val mix_view_bars = R.string.bc_mix_view_bars
    val mix_view_one_bar = R.string.bc_mix_view_one_bar
    val mix_view_seconds = R.string.bc_mix_view_seconds
    val mix_view_bpm = R.string.bc_mix_view_bpm
    val automix_overview_unavailable = R.string.bc_automix_overview_unavailable
    val mixing = R.string.bc_mixing
    val automix_analysis_status = R.string.bc_automix_analysis_status
    val automix_not_supported_video = R.string.bc_automix_not_supported_video
    val automix_stopped_in_party = R.string.bc_automix_stopped_in_party
    val analysis_complete = R.string.bc_analysis_complete
    val analysis_refining = R.string.bc_analysis_refining
    val analysis_in_progress = R.string.bc_analysis_in_progress
    val waiting = R.string.bc_waiting
    val failed = R.string.bc_failed
}

/** The settings the mix UI reads, backed by the BitChord automix settings. */
object PlayerSettings {
    val smartMixView: StateFlow<MixTransitionView?> get() = AppSettings.smartMixView
    val smartMixPreview: StateFlow<MixTransitionView?> get() = AppSettings.smartMixPreview
    val smartMixProgress: StateFlow<Float> get() = AppSettings.smartMixProgress
    val smartMixBlend: StateFlow<MixBlend?> get() = AppSettings.smartMixBlend
    val smartMixInProgress: StateFlow<Boolean> get() = AppSettings.smartMixInProgress
    val smartAnalysis: StateFlow<SmartAnalysis> get() = AppSettings.smartAnalysis
    val automixDetail: StateFlow<String?> get() = AppSettings.automixDetail
    val smartFadeEnabled: StateFlow<Boolean> get() = AppSettings.smartFadeEnabled
    val reduceDynamicBlur: StateFlow<Boolean> = MutableStateFlow(false)
    val reduceAnimation: StateFlow<Boolean> = MutableStateFlow(false)

    fun setMixViewOpen(open: Boolean) {
        AppSettings.mixViewOpen.value = open
    }
}

/** The app-level message channel the mix popup uses for "not available" notices. */
class PlayerHostShim {
    fun showMessage(message: String) {}
}

object PlayerPlatform {
    val host = PlayerHostShim()
}

/** Haze is not part of Metrolist; the popup is drawn with its plain tint. */
class HazeState

object HazeMaterials {
    fun regular(color: Color): Color = color
}

@RequiresOptIn("Haze materials are not used in Metrolist")
annotation class ExperimentalHazeMaterialsApi

@Suppress("UNUSED_PARAMETER")
fun Modifier.optimizedHazeEffect(state: HazeState, style: Any?): Modifier = this

fun Modifier.containSheetGestures(): Modifier = this

@Composable
fun PlayerBackHandler(enabled: Boolean, onBack: () -> Unit) {
    BackHandler(enabled = enabled, onBack = onBack)
}

/** Artwork is read back as pixels, so hardware bitmaps are not allowed. */
fun ImageRequest.Builder.forPixelAccess(): ImageRequest.Builder = allowHardware(false)

fun Image.toImageBitmap(): ImageBitmap = toBitmap().asImageBitmap()

/** Mesh colours of the artwork, from Metrolist's own palette extraction. */
fun artworkMeshColors(bitmap: ImageBitmap): List<Color> {
    val argb = bitmap.asAndroidBitmap()
    val palette = Palette.from(argb).generate()
    return PlayerColorExtractor.extractMeshColors(palette, fallbackColor = 0xFF444444.toInt())
}
