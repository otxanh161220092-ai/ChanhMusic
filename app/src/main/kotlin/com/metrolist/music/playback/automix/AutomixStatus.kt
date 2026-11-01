/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

/** What AutoMix is doing for the song that is playing, shown under the song title. */
sealed interface AutomixStatus {
    /** AutoMix is off, or there is nothing after this song to mix into. */
    data object Idle : AutomixStatus

    /** Both songs are being loaded and analyzed to find the mix points. */
    data object Analyzing : AutomixStatus

    /** The mix is planned: A starts to leave at [mixOutMs] and B comes in from [mixInMs]. */
    data class Ready(
        val mixOutMs: Long,
        val mixInMs: Long,
        val tempoMatched: Boolean,
    ) : AutomixStatus

    /** The songs could not be analyzed or no pair of sections fits; the next song just follows. */
    data object Unavailable : AutomixStatus
}
