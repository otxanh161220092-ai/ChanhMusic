/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

val DefaultThemeColor = Color(0xFFED5564)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    neutralSurfaces: Boolean = false,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // Determine if system dynamic colors should be used (Android S+ and default theme color)
    val useSystemDynamicColor = (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    // Select the appropriate color scheme generation method
    val baseColorScheme = if (useSystemDynamicColor) {
        // Use standard Material 3 dynamic color functions for system wallpaper colors
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        // Use materialKolor only when a specific seed color is provided
        rememberDynamicColorScheme(
            seedColor = themeColor, // themeColor is guaranteed non-default here
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot // Keep existing style
        )
    }

    // Apply pureBlack modification if needed, similar to original logic
    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    // Neutral surfaces: greys only, so backgrounds do not pick up wallpaper or album tints
    val finalColorScheme = remember(colorScheme, neutralSurfaces, darkTheme) {
        if (neutralSurfaces) colorScheme.withNeutralSurfaces(darkTheme) else colorScheme
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = finalColorScheme,
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color = Color(
    Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .rankedColors(1, DefaultThemeColor.toArgb())
        .first()
)

internal fun Palette.rankedColors(
    desiredColorCount: Int,
    fallbackColor: Int,
): List<Int> = Score.score(
    swatches.associate { it.rgb to it.population },
    desiredColorCount,
    fallbackColor,
    true,
)

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}

private fun ColorScheme.withNeutralSurfaces(dark: Boolean): ColorScheme =
    if (dark) {
        copy(
            background = Color(0xFF121212),
            onBackground = Color(0xFFF2F2F2),
            surface = Color(0xFF161616),
            onSurface = Color(0xFFF2F2F2),
            surfaceVariant = Color(0xFF2A2A2A),
            onSurfaceVariant = Color(0xFFA6A6A6),
            surfaceContainerLowest = Color(0xFF0F0F0F),
            surfaceContainerLow = Color(0xFF1A1A1A),
            surfaceContainer = Color(0xFF1F1F1F),
            surfaceContainerHigh = Color(0xFF262626),
            surfaceContainerHighest = Color(0xFF303030),
            primary = Color(0xFFF2F2F2),
            onPrimary = Color(0xFF111111),
            primaryContainer = Color(0x33FFFFFF),
            onPrimaryContainer = Color(0xFFF2F2F2),
            secondary = Color(0xFFBDBDBD),
            onSecondary = Color(0xFF111111),
            secondaryContainer = Color(0x33FFFFFF),
            onSecondaryContainer = Color(0xFFF2F2F2),
        )
    } else {
        copy(
            background = Color(0xFFFFFFFF),
            onBackground = Color(0xFF111111),
            surface = Color(0xFFFFFFFF),
            onSurface = Color(0xFF111111),
            surfaceVariant = Color(0xFFEFEFEF),
            onSurfaceVariant = Color(0xFF6B6B6B),
            surfaceContainerLowest = Color(0xFFFFFFFF),
            surfaceContainerLow = Color(0xFFF7F7F7),
            surfaceContainer = Color(0xFFF2F2F2),
            surfaceContainerHigh = Color(0xFFEBEBEB),
            surfaceContainerHighest = Color(0xFFE3E3E3),
            primary = Color(0xFF111111),
            onPrimary = Color(0xFFFFFFFF),
            primaryContainer = Color(0x14000000),
            onPrimaryContainer = Color(0xFF111111),
            secondary = Color(0xFF6B6B6B),
            onSecondary = Color(0xFFFFFFFF),
            secondaryContainer = Color(0x14000000),
            onSecondaryContainer = Color(0xFF111111),
        )
    }
