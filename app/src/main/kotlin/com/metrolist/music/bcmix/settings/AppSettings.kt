package com.metrolist.music.bcmix.settings

import kotlinx.coroutines.flow.MutableStateFlow

/**
 * The Automix settings the BitChord transition engine reads and writes. Values
 * live in memory here; the Metrolist settings screen binds to these flows.
 */
object AppSettings {
    val crossfadeSeconds = MutableStateFlow(0)
    val smartFadeEnabled = MutableStateFlow(false)
    val automixPerformanceMode = MutableStateFlow(AutomixPerformanceMode.BALANCED)
    val playbackSpeed = MutableStateFlow(1.0f)
    val smartMixInProgress = MutableStateFlow(false)
    val smartMixBlend = MutableStateFlow<MixBlend?>(null)
    val smartMixView = MutableStateFlow<MixTransitionView?>(null)
    val smartMixProgress = MutableStateFlow(0f)
    val smartMixPreview = MutableStateFlow<MixTransitionView?>(null)
    val mixViewOpen = MutableStateFlow(false)
    val smartAnalysis = MutableStateFlow(SmartAnalysis())
    val automixDetail = MutableStateFlow<String?>(null)
    val smartTransitionWindow = MutableStateFlow<TransitionWindow?>(null)
}
