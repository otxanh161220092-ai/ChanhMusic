package com.metrolist.music.bcmix.data.listentogether

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Listen-together session state. NOT PORTED: Metrolist has no party feature,
 * so the app is never in a party and every track is admitted.
 */
object ListenTogether {
    data class State(val inParty: Boolean = false) {
        fun admits(mediaId: String): Boolean = true
    }

    val state: StateFlow<State> = MutableStateFlow(State())
}
