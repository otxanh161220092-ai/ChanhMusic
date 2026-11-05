/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

/** What the player screen needs to blend the two covers while a mix runs; [progress] goes from 0 to 1 across the mix. */
data class MixVisual(
    val outgoingId: String,
    val outgoingArtwork: String?,
    val incomingId: String,
    val incomingArtwork: String?,
    val progress: Float,
)
