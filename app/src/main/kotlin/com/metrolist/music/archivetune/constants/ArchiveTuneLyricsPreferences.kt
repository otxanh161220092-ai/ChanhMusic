package com.metrolist.music.archivetune.constants

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import java.time.LocalDateTime
import java.time.ZoneOffset

val BlurRadiusKey = floatPreferencesKey("blurRadius")

// Backdrop blur for detail pages

val DefaultLyricsSyncOffsetMode = LyricsSyncOffsetMode.PER_SONG

val DisableBlurKey = booleanPreferencesKey("disableBlur")

val EnableBetterLyricsPortatoKey = booleanPreferencesKey("enableBetterLyricsPortato")

val EnableBinimumLyricsKey = booleanPreferencesKey("enableBinimumLyrics")

val EnableHapticFeedbackKey = booleanPreferencesKey("enableHapticFeedback")

val EnableLrcRedLyricsKey = booleanPreferencesKey("enableLrcRedLyrics")

val EnableMegalobizLyricsKey = booleanPreferencesKey("enableMegalobizLyrics")

val EnableMusixmatchLyricsKey = booleanPreferencesKey("enableMusixmatchLyrics")

val EnablePaxsenixAppleMusicLyricsKey = booleanPreferencesKey("enablePaxsenixAppleMusicLyrics")

val EnablePaxsenixLyricsKey = booleanPreferencesKey("enablePaxsenixLyrics")

val EnablePaxsenixMusixmatchLyricsKey = booleanPreferencesKey("enablePaxsenixMusixmatchLyrics")

val EnablePaxsenixSpotifyLyricsKey = booleanPreferencesKey("enablePaxsenixSpotifyLyrics")

val EnableSimpMusicLyricsKey = booleanPreferencesKey("enableSimpMusicLyrics")

val EnableUnisonLyricsKey = booleanPreferencesKey("enableUnisonLyrics")

val EnableYouLyPlusLyricsKey = booleanPreferencesKey("enableYouLyPlusLyrics")

val LyricsAutoHideControlsKey = booleanPreferencesKey("lyricsAutoHideControls")

val LyricsBackgroundStyleKey = stringPreferencesKey("lyricsBackgroundStyle")

val LyricsGlobalSyncOffsetKey = intPreferencesKey("lyricsGlobalSyncOffset")

val LyricsLineBlurKey = booleanPreferencesKey("lyricsLineBlur")

enum class LyricsMode {
    V2,
    ENHANCED,
}

val LyricsModeKey = stringPreferencesKey("lyricsMode")

val LyricsRomanizeChineseKey = booleanPreferencesKey("lyricsRomanizeChinese")

val LyricsRomanizeHindiKey = booleanPreferencesKey("lyricsRomanizeHindi")

val LyricsRomanizeJapaneseKey = booleanPreferencesKey("lyricsRomanizeJapanese")

val LyricsRomanizeKoreanKey = booleanPreferencesKey("lyricsRomanizeKorean")

val LyricsRomanizeOtherLanguagesKey = booleanPreferencesKey("lyricsRomanizeOtherLanguages")

const val LyricsSyncOffsetLimitMs = 5000

enum class LyricsSyncOffsetMode {
    GLOBAL,
    PER_SONG,
}

val LyricsSyncOffsetModeKey = stringPreferencesKey("lyricsSyncOffsetMode")

val PaxsenixApiKeyKey = stringPreferencesKey("paxsenixApiKey")

val PlayerCustomBlurKey = floatPreferencesKey("playerCustomBlur")

val PlayerCustomBrightnessKey = floatPreferencesKey("playerCustomBrightness")

val PlayerCustomContrastKey = floatPreferencesKey("playerCustomContrast")

val PlayerCustomImageUriKey = stringPreferencesKey("playerCustomImageUri")

val TranslateLyricsKey = booleanPreferencesKey("translateLyrics")

fun deserializeLyricsProviderOrder(orderStr: String?): List<PreferredLyricsProvider> {
    if (orderStr.isNullOrBlank()) return DefaultLyricsProviderOrder

    val parsed =
        orderStr
            .split(",")
            .mapNotNull { name ->
                PreferredLyricsProvider.entries.find { it.name == name.trim() }
            }.distinct()

    val normalized =
        when {
            parsed.take(3) ==
                listOf(
                    PreferredLyricsProvider.LRCLIB,
                    PreferredLyricsProvider.KUGOU,
                    PreferredLyricsProvider.BETTER_LYRICS,
                )
            -> listOf(PreferredLyricsProvider.BETTER_LYRICS) + parsed.filterNot { it == PreferredLyricsProvider.BETTER_LYRICS }

            else -> parsed
        }

    val missing = DefaultLyricsProviderOrder.filterNot { it in normalized }
    return normalized + missing
}

val UseLyricsV2Key = booleanPreferencesKey("useLyricsV2")

val LyricsV2BounceFactorKey = floatPreferencesKey("lyricsV2BounceFactor")

val LyricsV2GlowFactorKey = floatPreferencesKey("lyricsV2GlowFactor")

val LyricsV2FillTransitionWidthKey = floatPreferencesKey("lyricsV2FillTransitionWidth")

val LyricsV2LrcBounceEnabledKey = booleanPreferencesKey("lyricsV2LrcBounceEnabled")

val PlayerBackgroundStyleKey = stringPreferencesKey("playerBackgroundStyle")
