package com.metrolist.music.archivetune.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily

/**
 * The ArchiveTune lyrics font setting is not ported (it depends on ArchiveTune's font preference
 * system). Lyrics use the app's default font for now.
 */
@Composable
fun rememberArchiveTuneLyricsFontFamily(): FontFamily = FontFamily.Default
