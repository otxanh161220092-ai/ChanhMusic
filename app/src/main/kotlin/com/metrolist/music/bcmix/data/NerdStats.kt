package com.metrolist.music.bcmix.data

/**
 * Diagnostics sink for the stream race. NOT PORTED: BitChord's version feeds
 * the nerd-stats panel, which Metrolist does not show. These are no-ops.
 */
object NerdStats {
    fun pickedBitrateKbps(videoId: String): Int? = null
    fun onLosslessRaceEnd(videoId: String) {}
    fun onLosslessRaceStart(videoId: String) {}
    fun racingLossless(videoId: String): Boolean = false
    fun isLosslessMime(mime: String?): Boolean = mime?.let { it.contains("flac") || it.contains("alac") || it.contains("wav") } ?: false
    fun isDolbyAtmosMime(mime: String?): Boolean = mime?.contains("eac3") == true
}
