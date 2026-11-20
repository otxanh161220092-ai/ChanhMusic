/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.music.archivetune.viewmodels

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.metrolist.music.archivetune.constants.DefaultLyricsSyncOffsetMode
import com.metrolist.music.archivetune.constants.LyricsGlobalSyncOffsetKey
import com.metrolist.music.archivetune.constants.LyricsSyncOffsetLimitMs
import com.metrolist.music.archivetune.constants.LyricsSyncOffsetMode
import com.metrolist.music.archivetune.constants.LyricsSyncOffsetModeKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.archivetune.constants.LyricsPerSongSyncOffsetsKey
import kotlinx.serialization.json.Json
import com.metrolist.music.extensions.toEnum
import com.metrolist.music.utils.dataStore
import javax.inject.Inject

@Immutable
data class LyricsSyncOffsetState(
    val mode: LyricsSyncOffsetMode,
    val songId: String?,
    val offsetMs: Int,
) {
    fun offsetFor(currentSongId: String?): Int =
        if (mode == LyricsSyncOffsetMode.GLOBAL || songId == currentSongId) offsetMs else 0
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LyricsSyncOffsetViewModel
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: MusicDatabase,
    ) : ViewModel() {
        private val songId = MutableStateFlow<String?>(null)

        private val mode =
            context.dataStore.data
                .map { it[LyricsSyncOffsetModeKey].toEnum(DefaultLyricsSyncOffsetMode) }
                .distinctUntilChanged()

        private val globalOffset =
            context.dataStore.data
                .map { (it[LyricsGlobalSyncOffsetKey] ?: 0).coerceToLimit() }
                .distinctUntilChanged()

        val state: StateFlow<LyricsSyncOffsetState> =
            combine(mode, songId) { offsetMode, id -> offsetMode to id }
                .flatMapLatest { (offsetMode, id) ->
                    when {
                        offsetMode == LyricsSyncOffsetMode.GLOBAL ->
                            globalOffset.map { LyricsSyncOffsetState(offsetMode, id, it) }

                        id == null ->
                            flowOf(LyricsSyncOffsetState(offsetMode, null, 0))

                        else ->
                            context.dataStore.data
                                .map { LyricsSyncOffsetState(offsetMode, id, (perSongOffsets(it)[id] ?: 0).coerceToLimit()) }
                    }
                }.distinctUntilChanged()
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = LyricsSyncOffsetState(DefaultLyricsSyncOffsetMode, null, 0),
                )

        fun setSongId(id: String?) {
            songId.value = id
        }

        fun setOffset(
            songId: String,
            offsetMs: Int,
        ) {
            val value = offsetMs.coerceToLimit()
            viewModelScope.launch {
                val currentMode = context.dataStore.data.first()[LyricsSyncOffsetModeKey].toEnum(DefaultLyricsSyncOffsetMode)
                when (currentMode) {
                    LyricsSyncOffsetMode.GLOBAL ->
                        context.dataStore.edit { it[LyricsGlobalSyncOffsetKey] = value }

                    LyricsSyncOffsetMode.PER_SONG ->
                        if (value == 0) {
                            context.dataStore.edit { prefs ->
                                val map = perSongOffsets(prefs).toMutableMap()
                                map.remove(songId)
                                prefs[LyricsPerSongSyncOffsetsKey] = Json.encodeToString(map)
                            }
                        } else {
                            context.dataStore.edit { prefs ->
                                val map = perSongOffsets(prefs).toMutableMap()
                                map[songId] = value
                                prefs[LyricsPerSongSyncOffsetsKey] = Json.encodeToString(map)
                            }
                        }
                }
            }
        }

        private fun perSongOffsets(prefs: androidx.datastore.preferences.core.Preferences): Map<String, Int> =
            prefs[LyricsPerSongSyncOffsetsKey]
                ?.let { runCatching { Json.decodeFromString<Map<String, Int>>(it) }.getOrNull() }
                ?: emptyMap()

        private fun Int.coerceToLimit(): Int = coerceIn(-LyricsSyncOffsetLimitMs, LyricsSyncOffsetLimitMs)
    }
