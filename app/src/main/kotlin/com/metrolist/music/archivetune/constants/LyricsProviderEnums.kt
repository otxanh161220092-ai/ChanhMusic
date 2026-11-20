package com.metrolist.music.archivetune.constants

enum class PreferredLyricsProvider {
    BETTER_LYRICS,
    BETTER_LYRICS_PORTATO,
    YOULY_PLUS,
    BINIMUM,
    LRC_RED,
    LRCLIB,
    KUGOU,
    MEGALOBIZ,
    SIMPMUSIC,
    UNISON,
    PAXSENIX_APPLE_MUSIC,
    PAXSENIX_SPOTIFY,
    PAXSENIX_MUSIXMATCH,
    MUSIXMATCH,
}

val DefaultLyricsProviderOrder =
    listOf(
        PreferredLyricsProvider.BETTER_LYRICS,
        PreferredLyricsProvider.BETTER_LYRICS_PORTATO,
        PreferredLyricsProvider.YOULY_PLUS,
        PreferredLyricsProvider.BINIMUM,
        PreferredLyricsProvider.LRC_RED,
        PreferredLyricsProvider.LRCLIB,
        PreferredLyricsProvider.KUGOU,
        PreferredLyricsProvider.MEGALOBIZ,
        PreferredLyricsProvider.SIMPMUSIC,
        PreferredLyricsProvider.UNISON,
        PreferredLyricsProvider.PAXSENIX_APPLE_MUSIC,
        PreferredLyricsProvider.PAXSENIX_SPOTIFY,
        PreferredLyricsProvider.PAXSENIX_MUSIXMATCH,
        PreferredLyricsProvider.MUSIXMATCH,
    )

enum class PlayerBackgroundStyle {
    DEFAULT,
    GRADIENT,
    CUSTOM,
    BLUR,
    COLORING,
    BLUR_GRADIENT,
    GLOW,
    GLOW_ANIMATED,
}

enum class LyricsBackgroundStyle {
    DEFAULT,
    FOLLOW_THEME,
    COLORING,
    CUSTOM;

    fun resolveFor(playerBackgroundStyle: PlayerBackgroundStyle): LyricsBackgroundStyle =
        when {
            playerBackgroundStyle == PlayerBackgroundStyle.CUSTOM -> CUSTOM
            this == CUSTOM -> DEFAULT
            else -> this
        }
}


enum class LyricsAnimationStyle {
    NONE,
    FADE,
    GLOW,
    SLIDE,
    KARAOKE,
    APPLE,
}
