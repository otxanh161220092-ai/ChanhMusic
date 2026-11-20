/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package com.metrolist.music.archivetune.lyrics

import android.content.Context
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.metrolist.music.constants.LyricsClickKey
import com.metrolist.music.archivetune.constants.LyricsLineBlurKey
import com.metrolist.music.constants.LyricsLineSpacingKey
import com.metrolist.music.archivetune.constants.LyricsRomanizeChineseKey
import com.metrolist.music.archivetune.constants.LyricsRomanizeHindiKey
import com.metrolist.music.archivetune.constants.LyricsRomanizeJapaneseKey
import com.metrolist.music.archivetune.constants.LyricsRomanizeKoreanKey
import com.metrolist.music.archivetune.constants.LyricsRomanizeOtherLanguagesKey
import com.metrolist.music.constants.LyricsScrollKey
import com.metrolist.music.constants.LyricsTextSizeKey
import com.metrolist.music.archivetune.constants.LyricsV2BounceFactorKey
import com.metrolist.music.archivetune.constants.LyricsV2FillTransitionWidthKey
import com.metrolist.music.archivetune.constants.LyricsV2GlowFactorKey
import com.metrolist.music.archivetune.constants.LyricsV2LrcBounceEnabledKey
import com.metrolist.music.archivetune.constants.TranslateLyricsKey
import com.metrolist.music.db.MusicDatabase
import com.metrolist.music.utils.dataStore
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LyricsRenderingRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
        @ApplicationContext context: Context,
    ) {
        private val dataStore = context.dataStore
        private val preferences = dataStore.data

        suspend fun setShowTranslations(show: Boolean) {
            dataStore.edit { values -> values[TranslateLyricsKey] = show }
        }

        fun observeLyrics(mediaId: String): Flow<String?> =
            database
                .lyrics(mediaId)
                .map { entity -> entity?.lyrics }
                .distinctUntilChanged()

        fun observePreferences(): Flow<LyricsRenderingPreferences> =
            preferences
                .map { values ->
                    LyricsRenderingPreferences(
                        clickEnabled = values[LyricsClickKey] ?: true,
                        showTranslations = values[TranslateLyricsKey] ?: true,
                        scrollEnabled = values[LyricsScrollKey] ?: true,
                        textSizeSp = values[LyricsTextSizeKey] ?: 26f,
                        lineSpacing = values[LyricsLineSpacingKey] ?: 1.3f,
                        lineBlurEnabled = values[LyricsLineBlurKey] ?: true,
                        v2BounceFactor = values[LyricsV2BounceFactorKey] ?: 1f,
                        v2GlowFactor = values[LyricsV2GlowFactorKey] ?: 1f,
                        v2FillTransitionWidthDp = values[LyricsV2FillTransitionWidthKey] ?: 8f,
                        v2LrcBounceEnabled = values[LyricsV2LrcBounceEnabledKey] ?: true,
                        romanization =
                            LyricsRomanizationPreferences(
                                romanizeJapanese = values[LyricsRomanizeJapaneseKey] ?: true,
                                romanizeKorean = values[LyricsRomanizeKoreanKey] ?: true,
                                romanizeChinese = values[LyricsRomanizeChineseKey] ?: true,
                                romanizeHindi = values[LyricsRomanizeHindiKey] ?: true,
                                romanizeOther = values[LyricsRomanizeOtherLanguagesKey] ?: true,
                            ),
                    )
                }.distinctUntilChanged()
    }
