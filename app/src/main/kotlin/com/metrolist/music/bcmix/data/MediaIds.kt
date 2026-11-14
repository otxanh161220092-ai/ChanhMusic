package com.metrolist.music.bcmix.data

import android.net.Uri
import com.metrolist.music.bcmix.data.sources.SourceRegistry

/**
 * The media key a stream URI belongs to. Ported from BitChord's `mediaIdIn`.
 * Source-backed tracks carry `s` (config id) and `t` (track id); YouTube
 * tracks carry `v`.
 */
fun mediaIdIn(uri: Uri): String? = if (uri.authority == "source") {
    val configId = uri.getQueryParameter("s")
    val trackId = uri.getQueryParameter("t")
    if (configId != null && trackId != null) SourceRegistry.trackKey(configId, trackId) else null
} else {
    uri.getQueryParameter("v")
}
