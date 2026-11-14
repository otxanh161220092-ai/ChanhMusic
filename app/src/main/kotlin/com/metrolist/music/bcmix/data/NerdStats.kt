package com.metrolist.music.bcmix.data

/**
 * Diagnostics sink for the stream race. NOT PORTED: BitChord's version feeds
 * the nerd-stats panel, which Metrolist does not show. These are no-ops.
 */
object NerdStats {
    fun pickedBitrateKbps(videoId: String): Int? = null
    fun onLosslessRaceEnd(videoId: String) {}
}
