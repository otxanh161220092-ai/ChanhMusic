/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import android.net.Uri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends file:// requests to [local] and every other request to [streams]. Stem files never pass
 * through the streaming cache, so they cannot be stored as a song's cached audio.
 */
internal class KaraokeRoutingDataSource(
    private val streams: DataSource,
    private val local: DataSource,
) : DataSource {

    private var active: DataSource = streams

    override fun addTransferListener(transferListener: TransferListener) {
        streams.addTransferListener(transferListener)
        local.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        active = if (dataSpec.uri.scheme == "file") local else streams
        return active.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = active.read(buffer, offset, length)

    override fun getUri(): Uri? = active.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active.responseHeaders

    override fun close() {
        active.close()
    }
}
