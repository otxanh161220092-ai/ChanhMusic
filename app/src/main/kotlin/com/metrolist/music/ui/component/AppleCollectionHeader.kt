/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component
import androidx.compose.ui.graphics.lerp
import com.metrolist.music.constants.CollectionDominantBackgroundKey
import com.metrolist.music.utils.rememberPreference

import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.kyant.backdrop.backdrops.layerBackdrop
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.background
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.theme.abrilFatface
import com.metrolist.music.ui.theme.anton
import com.metrolist.music.ui.theme.bbhBartle
import com.metrolist.music.ui.theme.beVietnamPro
import com.metrolist.music.ui.theme.bebasNeue
import com.metrolist.music.ui.theme.bungee
import com.metrolist.music.ui.theme.caveat
import com.metrolist.music.ui.theme.cinzel
import com.metrolist.music.ui.theme.dancingScript
import com.metrolist.music.ui.theme.lora
import com.metrolist.music.ui.theme.montserrat
import com.metrolist.music.ui.theme.oswald
import com.metrolist.music.ui.theme.orbitron
import com.metrolist.music.ui.theme.pacifico
import com.metrolist.music.ui.theme.playfairDisplay
import com.metrolist.music.ui.theme.quicksand
import com.metrolist.music.ui.theme.righteous
import com.metrolist.music.ui.theme.vietnameseTitleFonts
import coil3.compose.AsyncImage
import com.metrolist.music.R
import com.metrolist.music.ui.utils.resize

/** Text and icon color for the Apple collection content; [Color.Unspecified] outside that layout. */
val LocalAppleCollectionTextColor = compositionLocalOf { Color.Unspecified }

/** Shared "is the backdrop behind the collection light?" flag, written by the header's buttons and read by list rows. */
val LocalAppleBackdropIsLight = compositionLocalOf<androidx.compose.runtime.MutableState<Boolean>?> { null }

/** Black over bright artwork, white over dark, so text and icons stay readable on the backdrop. */
@Composable
fun rememberAppleTextColor(thumbnailUrl: String?): Color = Color.White

/** Secondary text color: the theme's secondary outside Apple collections, a softened text color inside. */
@Composable
fun appleSubtitleColor(): Color {
    val text = LocalAppleCollectionTextColor.current
    if (text == Color.Unspecified) return MaterialTheme.colorScheme.secondary
    // Black on light backdrop, warm ivory on dark, sampled from the real pixels behind the list.
    val backdropIsLight = LocalAppleBackdropIsLight.current?.value ?: (text == Color.Black)
    return if (backdropIsLight) Color.Black else Color(0xFFF7F0E1)
}

/** Transparent top bar whose content follows the artwork brightness in Apple style. */
@Composable
fun appleTopBarColors(enabled: Boolean, thumbnailUrl: String?): TopAppBarColors {
    if (!enabled) return TopAppBarDefaults.topAppBarColors()
    val textColor = rememberAppleTextColor(thumbnailUrl)
    return TopAppBarDefaults.topAppBarColors(
        containerColor = Color.Transparent,
        scrolledContainerColor = Color.Transparent,
        navigationIconContentColor = textColor,
        titleContentColor = textColor,
        actionIconContentColor = textColor,
    )
}

/** Drops the top inset in Apple style so the artwork runs under the status bar and top bar. */
@Composable
fun appleCollectionContentPadding(base: PaddingValues, enabled: Boolean): PaddingValues {
    if (!enabled) return base
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = base.calculateLeftPadding(direction),
        end = base.calculateRightPadding(direction),
        bottom = base.calculateBottomPadding(),
    )
}

/** Page background for Apple style collections: artwork color fading into the page background. */
@Composable
fun rememberAppleCollectionBrush(artworkUrl: String?): Brush {
    val artColor by rememberArtworkColor(artworkUrl?.resize(100, 100))
    val (dominant) = rememberPreference(CollectionDominantBackgroundKey, defaultValue = false)
    val pageColor = MaterialTheme.colorScheme.background
    // Dark pages pull the cover colour toward black and light pages toward white, so it sits comfortably.
    val pageIsDark = pageColor.luminance() < 0.5f
    val heroColor =
        when {
            artColor == null -> MaterialTheme.colorScheme.surfaceVariant
            !dominant -> artColor!!
            pageIsDark -> lerp(artColor!!, Color.Black, 0.5f)
            else -> lerp(artColor!!, Color.White, 0.35f)
        }
    return remember(heroColor, pageColor) {
        Brush.verticalGradient(
            0f to heroColor,
            0.85f to heroColor,
            1f to pageColor,
        )
    }
}

/**
 * Full-screen container for an Apple style collection: blurred artwork over the collection
 * gradient, with [content] (usually the list) drawn on top in the same offscreen layer so
 * highlight buttons blend with it.
 */
@Composable
fun AppleCollectionContainer(
    thumbnailUrl: String?,
    enabled: Boolean,
    /** Records only the artwork background (never the top bar), so top buttons can sample it. */
    backdrop: com.kyant.backdrop.backdrops.LayerBackdrop? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val brush = rememberAppleCollectionBrush(thumbnailUrl)
    Box(modifier = Modifier.fillMaxSize().then(if (enabled) Modifier.appleCollectionBackground(brush) else Modifier)) {
        if (enabled) {
            val textColor = rememberAppleTextColor(thumbnailUrl)
            val backdropIsLight = remember { mutableStateOf(false) }
            CompositionLocalProvider(
                LocalAppleCollectionTextColor provides textColor,
                LocalContentColor provides textColor,
                LocalAppleBackdropIsLight provides backdropIsLight,
            ) {
                if (backdrop != null) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop)) { AppleCollectionBackdrop(thumbnailUrl) }
                } else {
                    AppleCollectionBackdrop(thumbnailUrl)
                }
                content()
            }
        } else {
            content()
        }
    }
}

/**
 * Background for an Apple style collection. The offscreen layer makes highlight buttons blend
 * with the artwork and gradient behind them instead of with the empty surface.
 */
fun Modifier.appleCollectionBackground(brush: Brush): Modifier =
    this.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.background(brush)

/** Overlay-blends a white layer over whatever is already drawn behind the control. */
private fun Modifier.screenHighlight(alpha: Float): Modifier = this.drawBehind {
    drawRoundRect(
        color = Color.White.copy(alpha = alpha),
        cornerRadius = CornerRadius(size.minDimension / 2f),
        blendMode = BlendMode.Overlay,
    )
}

/** Blurred artwork covering the whole collection screen, fading out toward the bottom. */
@Composable
fun BoxScope.AppleCollectionBackdrop(thumbnailUrl: String?) {
    val (dominant) = rememberPreference(CollectionDominantBackgroundKey, defaultValue = false)
    if (dominant) return
    AsyncImage(
        model = thumbnailUrl?.resize(200, 200),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .matchParentSize()
            .clipToBounds()
            .blur(130.dp, BlurredEdgeTreatment.Unbounded)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                // Darkens the blurred artwork a little for every Apple style collection
                drawRect(color = Color.Black.copy(alpha = 0.25f))
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black,
                        0.45f to Color.Black,
                        1f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
    )
}

/**
 * Picks a title font from the artwork's mood and the title length. Each mood has a short list of
 * candidates; titles with Vietnamese diacritics skip any font whose glyphs don't cover them.
 */
private fun albumTitleFontFamily(color: Color?, title: String): FontFamily {
    if (color == null) return FontFamily.SansSerif
    val max = maxOf(color.red, color.green, color.blue)
    val min = minOf(color.red, color.green, color.blue)
    val delta = max - min
    val lightness = (max + min) / 2f
    val saturation = if (delta == 0f) 0f else delta / (1f - kotlin.math.abs(2f * lightness - 1f))
    val hue = when {
        delta == 0f -> 0f
        max == color.red -> 60f * (((color.green - color.blue) / delta) % 6f)
        max == color.green -> 60f * ((color.blue - color.red) / delta + 2f)
        else -> 60f * ((color.red - color.green) / delta + 4f)
    }.let { (it + 360f) % 360f }
    val warm = hue < 70f || hue >= 300f
    val short = title.length <= 10
    val candidates = when {
        short && lightness < 0.35f -> listOf(anton, bebasNeue, oswald)
        short && saturation < 0.25f -> listOf(abrilFatface, playfairDisplay, lora)
        short && warm -> listOf(bungee, pacifico, beVietnamPro)
        short -> listOf(orbitron, quicksand, beVietnamPro)
        saturation < 0.25f && lightness > 0.6f -> listOf(cinzel, lora, playfairDisplay)
        saturation < 0.25f -> listOf(playfairDisplay, lora)
        lightness < 0.35f -> listOf(bebasNeue, oswald)
        lightness > 0.6f && saturation >= 0.5f && warm -> listOf(pacifico, dancingScript)
        lightness > 0.6f && saturation >= 0.5f -> listOf(righteous, quicksand)
        lightness > 0.6f && saturation < 0.5f -> listOf(caveat, dancingScript)
        lightness > 0.6f -> listOf(bbhBartle, montserrat)
        else -> listOf(montserrat, FontFamily.SansSerif)
    }
    val needsVietnamese = title.any { it.code > 127 }
    return candidates.firstOrNull { !needsVietnamese || it in vietnameseTitleFonts }
        ?: FontFamily.SansSerif
}

/**
 * Bigger type for shorter titles, capped so the longest word still fits on one line. Breaking a word
 * across lines reads as a bug, so the word length matters as much as the total length.
 */
private fun albumTitleFontSize(title: String): TextUnit {
    val longestWord = title.split(' ').maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1
    val byTotal = 56f - (title.length - 8).coerceAtLeast(0) * 1.4f
    val byWord = 330f / (longestWord * 0.9f)
    return minOf(byTotal, byWord).coerceIn(22f, 56f).sp
}

/**
 * Apple Music style collection header (album, playlist), replicating the reference layout: square
 * artwork fading into the page, centred bold title, grey artist and meta lines, a row of round
 * side buttons around a solid white Play pill, then the description.
 */
@Composable
fun AppleCollectionHeader(
    title: String,
    thumbnailUrl: String?,
    artistText: String?,
    metaText: String?,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
    onDownload: (() -> Unit)? = null,
    description: String? = null,
    onShuffle: (() -> Unit)? = null,
    onArtistClick: (() -> Unit)? = null,
) {
    val fill = Color.White.copy(alpha = 0.18f)
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AsyncImage(
            model = thumbnailUrl?.resize(900, 1200),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRect(
                            brush = Brush.verticalGradient(0f to Color.Black, 0.7f to Color.Black, 1f to Color.Transparent),
                            blendMode = BlendMode.DstIn,
                        )
                    },
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp),
        )

        if (!artistText.isNullOrBlank()) {
            Text(
                text = artistText,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp).then(if (onArtistClick != null) Modifier.clickable(onClick = onArtistClick) else Modifier),
            )
        }

        if (!metaText.isNullOrBlank()) {
            Text(
                text = metaText,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onShuffle != null) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(fill).clickable(onClick = onShuffle),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(R.string.shuffle_home),
                        tint = Color.White,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Row(
                modifier = Modifier.height(56.dp).width(180.dp).clip(CircleShape).background(Color.White).clickable(onClick = onPlay),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.play),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp).clip(CircleShape).background(fill).clickable(onClick = onDownload ?: {}),
            ) {
                Icon(
                    painter = painterResource(R.drawable.download),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        if (!description.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(20.dp))
            ExpandableText(
                text = description,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                collapsedMaxLines = 3,
            )
        }
    }
}

/**
 * Circle used by the Apple-style top bar. It is NOT sampled glass: the album screen sits inside the
 * root glass backdrop, so a glass surface here would read the layer it is drawn into (feedback loop,
 * freeze and crash). It uses a translucent fill with the same highlight rim instead.
 */
@Composable
fun AppleGlassCircleButton(
    icon: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier =
            modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.18f), CircleShape)
                .highlightBorder()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Round glass back button for Apple-style collection screens. */
@Composable
fun AppleTopBarBackButton(
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
) {
    AppleGlassCircleButton(
        icon = R.drawable.arrow_back,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = Modifier.padding(start = 16.dp),
    )
}

/** Two glass circles on the right of Apple-style collection screens: add-to-library and more. */
@Composable
fun AppleTopBarActionButtons(
    isInLibrary: Boolean,
    onAdd: () -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier.padding(end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppleGlassCircleButton(
            icon = if (isInLibrary) R.drawable.check else R.drawable.add,
            onClick = onAdd,
        )
        AppleGlassCircleButton(
            icon = R.drawable.more_horiz,
            onClick = onMore,
        )
    }
}
