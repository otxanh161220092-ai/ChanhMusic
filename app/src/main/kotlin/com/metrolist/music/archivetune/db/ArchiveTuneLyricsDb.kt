package com.metrolist.music.archivetune.db

import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.db.entities.LyricsEntity

/**
 * Source tags used by the ported ArchiveTune lyrics code.
 * The app's lyrics table has no source/provider-url/author columns and the schema must not change,
 * so only the lyrics text and provider name are persisted.
 */
enum class LyricsEntitySource(
    val value: Int,
) {
    REMOTE(0),
    EMBEDDED(1),
    USER_SELECTION(2),
    USER_EDIT(3),
    TRANSLATION(4),
    AI_TRANSLATION(5),
}

fun MusicDatabase.replaceLyrics(
    id: String,
    lyrics: String,
    source: Int,
    provider: String?,
    providerUrl: String?,
    providerAuthor: String?,
    providerAuthorUrl: String?,
) {
    upsert(LyricsEntity(id = id, lyrics = lyrics, provider = provider ?: "Unknown"))
}

fun MusicDatabase.saveFetchedLyrics(
    id: String,
    lyrics: String,
    provider: String?,
    providerUrl: String?,
    providerAuthor: String?,
    providerAuthorUrl: String?,
) {
    upsert(LyricsEntity(id = id, lyrics = lyrics, provider = provider ?: "Unknown"))
}
