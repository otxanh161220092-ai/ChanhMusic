package com.metrolist.music.archivetune.db.entities

import com.metrolist.music.db.entities.LyricsEntity

/**
 * The app's lyrics table has no source/updatedAt columns (schema must not change), so the
 * ArchiveTune source-aware helpers are reduced to what can be decided from lyrics + provider.
 * Stored "not found" rows are always treated as retryable.
 */
fun LyricsEntity?.needsRemoteFetch(): Boolean = this == null || lyrics == LyricsEntity.LYRICS_NOT_FOUND

fun LyricsEntity.hasGenericSource(): Boolean = true

fun LyricsEntity.hasTranslationSource(): Boolean = false
