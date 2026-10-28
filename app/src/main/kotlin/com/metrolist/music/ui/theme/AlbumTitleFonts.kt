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

private fun staticFont(resId: Int) = FontFamily(Font(resId, FontWeight.Normal))

private fun variableFont(resId: Int) = FontFamily(
    Font(resId, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(resId, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

val bebasNeue = staticFont(R.font.bebas_neue_regular)
val anton = staticFont(R.font.anton_regular)
val pacifico = staticFont(R.font.pacifico_regular)
val abrilFatface = staticFont(R.font.abril_fatface_regular)
val bungee = staticFont(R.font.bungee_regular)
val righteous = staticFont(R.font.righteous_regular)
val playfairDisplay = variableFont(R.font.playfair_display)
val orbitron = variableFont(R.font.orbitron)
val cinzel = variableFont(R.font.cinzel)
val caveat = variableFont(R.font.caveat)
