/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.metrolist.music.R

val bebasNeue = FontFamily(Font(R.font.bebas_neue_regular, FontWeight.Normal))

val anton = FontFamily(Font(R.font.anton_regular, FontWeight.Normal))

val pacifico = FontFamily(Font(R.font.pacifico_regular, FontWeight.Normal))

val playfairDisplay = FontFamily(
    Font(
        R.font.playfair_display,
        FontWeight.Normal,
        variationSettings = FontVariation.Settings(FontVariation.weight(400)),
    ),
    Font(
        R.font.playfair_display,
        FontWeight.Bold,
        variationSettings = FontVariation.Settings(FontVariation.weight(700)),
    ),
)
