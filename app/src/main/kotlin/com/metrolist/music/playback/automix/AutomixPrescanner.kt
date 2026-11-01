/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.automix

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Analyzes the next queue item in the background, once the current song is half way through.
 * Results go through [AutomixAnalyzer], so they are written to the store and reused next time.
 *
 * Only one pre-scan runs at a time. A new request for a different song cancels the previous one.
 */
class AutomixPrescanner(
    private val analyzer: AutomixAnalyzer,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null
    private var requestedMediaId: String? = null

    /** Call once when the current song reaches its midpoint, with the id of the song that follows it. */
    fun onMidpoint(nextMediaId: String?) {
        if (nextMediaId == null || nextMediaId == requestedMediaId) return
        requestedMediaId = nextMediaId
        job?.cancel()
        job =
            scope.launch(Dispatchers.IO) {
                try {
                    analyzer.analyze(nextMediaId)
                    Timber.tag(TAG).d("Pre-scanned %s", nextMediaId)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(TAG).w(e, "Pre-scan failed for %s", nextMediaId)
                }
            }
    }

    fun cancel() {
        job?.cancel()
        job = null
        requestedMediaId = null
    }

    private companion object {
        const val TAG = "AutomixPrescanner"
    }
}
