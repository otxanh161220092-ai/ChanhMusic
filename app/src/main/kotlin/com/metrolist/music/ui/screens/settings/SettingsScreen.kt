/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.screens.settings

import com.metrolist.music.ui.theme.subtitleGrayColor

import com.metrolist.music.ui.component.GradientTopAppBar

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.navigation.NavController
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.ui.component.IconButton

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.painter.Painter
import com.metrolist.music.ui.component.Material3SettingsItemRow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.metrolist.music.ui.component.Material3SettingsGroup
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.Updater
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
private class SettingsRow(val label: String, val item: Material3SettingsItem)

private class SettingsSection(val title: String?, val rows: List<SettingsRow>)

@Composable
fun SettingsScreen(
    navController: NavController,
    latestVersionName: String,
) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isAndroid12OrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val hasAndroidAuto = remember {
        try {
            context.packageManager.getPackageInfo(
                "com.google.android.projection.gearhead", 0
            )
            true
        } catch (e: Exception) {
            false
        }
    }

    var query by remember { mutableStateOf("") }
    var searchFocused by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    fun row(label: String, icon: Painter?, route: String) = SettingsRow(
        label = label,
        item = Material3SettingsItem(
            icon = icon,
            title = { Text(label) },
            onClick = { navController.navigate(route) },
        ),
    )

    val appearance = row(stringResource(R.string.appearance), painterResource(R.drawable.palette), "settings/appearance")
    val player = row(stringResource(R.string.player_and_audio), painterResource(R.drawable.play), "settings/player")
    val content = row(stringResource(R.string.content), painterResource(R.drawable.language), "settings/content")
    val ai = row(stringResource(R.string.ai_lyrics_translation), painterResource(R.drawable.translate), "settings/ai")
    val autoRow = row(stringResource(R.string.android_auto), painterResource(R.drawable.ic_android_auto), "settings/android_auto")
    val privacy = row(stringResource(R.string.privacy), painterResource(R.drawable.security), "settings/privacy")
    val storage = row(stringResource(R.string.storage), painterResource(R.drawable.storage), "settings/storage")
    val backup = row(stringResource(R.string.backup_restore), painterResource(R.drawable.restore), "settings/backup_restore")
    val about = row(stringResource(R.string.about), painterResource(R.drawable.info), "settings/about")
    val updater = if (BuildConfig.UPDATER_AVAILABLE) {
        row(stringResource(R.string.updater), painterResource(R.drawable.update), "settings/updater")
    } else {
        null
    }

    val newVersionRow: SettingsRow? = if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
        val releaseInfo = Updater.getCachedLatestRelease()
        val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }
        if (downloadUrl != null) {
            SettingsRow(
                label = stringResource(R.string.new_version_available),
                item = Material3SettingsItem(
                    icon = painterResource(R.drawable.update),
                    title = { Text(text = stringResource(R.string.new_version_available)) },
                    description = {
                        Text(
                            text = latestVersionName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = subtitleGrayColor()
                        )
                    },
                    showBadge = true,
                    onClick = { uriHandler.openUri(downloadUrl) }
                ),
            )
        } else {
            null
        }
    } else {
        null
    }

    val sections = buildList {
        add(SettingsSection(stringResource(R.string.settings_section_ui), listOf(appearance)))
        add(SettingsSection(stringResource(R.string.settings_section_player_content), listOf(player, content, ai)))
        if (hasAndroidAuto) add(SettingsSection("Android Auto", listOf(autoRow)))
        add(SettingsSection(stringResource(R.string.settings_section_privacy), listOf(privacy)))
        add(SettingsSection(stringResource(R.string.settings_section_storage), listOf(storage, backup)))
        add(
            SettingsSection(
                stringResource(R.string.settings_section_system),
                buildList {
                    updater?.let { add(it) }
                    add(about)
                    newVersionRow?.let { add(it) }
                },
            )
        )
    }

    val searching = query.isNotBlank()
    val results = if (searching) {
        val needle = query.trim()
        sections.flatMap { it.rows }.filter { it.label.contains(needle, ignoreCase = true) }
    } else {
        emptyList()
    }

    val searchBarShape = RoundedCornerShape(50)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(
                Modifier.windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Top
                    )
                )
            )

            Image(
                painter = painterResource(R.drawable.app_logo),
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
                    .size(64.dp),
            )

            if (searching) {
                SettingsCard(rows = results)
                if (results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_search_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = subtitleGrayColor(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                sections.forEach { section ->
                    SettingsCard(title = section.title, rows = section.rows)
                    Spacer(modifier = Modifier.height(16.dp))
                }
                if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
                    ReleaseNotesCard()
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            // Room for the search bar at the bottom of the screen.
            Spacer(modifier = Modifier.height(72.dp))
        }

        // Dim layer: sits under the search bar and covers everything else while it is focused.
        if (searchFocused) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        focusManager.clearFocus()
                    }
            )
        }

        // Search bar: above the nav bar and above the mini player when it is shown.
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Bottom))
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(52.dp)
                    .clip(searchBarShape)
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                Image(
                    painter = painterResource(R.drawable.settings_search_glass),
                    contentDescription = null,
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier.matchParentSize(),
                )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = null,
                    tint = Color.Black.copy(alpha = 0.55f),
                    modifier = Modifier.size(22.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = stringResource(R.string.settings_search_hint),
                            style = MaterialTheme.typography.bodyLarge,
                            color = Color.Black.copy(alpha = 0.5f),
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.Black),
                        cursorBrush = SolidColor(Color.Black),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { searchFocused = it.isFocused },
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = Color.Black.copy(alpha = 0.55f),
                        modifier = Modifier
                            .size(20.dp)
                            .clickable { query = "" },
                    )
                }
            }
            }
            if (searchFocused || query.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable {
                            query = ""
                            focusManager.clearFocus()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        tint = Color.Black,
                    )
                }
            }
        }
    }

    GradientTopAppBar(
        title = { Text(stringResource(R.string.settings)) },
        navigationIcon = {
            com.metrolist.music.ui.component.OriginalBackSlot {
                IconButton(
                    onClick = navController::navigateUp,
                    onLongClick = navController::backToMain
                ) {
                    Icon(
                        painterResource(R.drawable.arrow_back),
                        contentDescription = null
                    )
                }
            }
        }
    )
}

/** One rounded card holding rows separated by dividers, with an optional section title above it. */
@Composable
private fun SettingsCard(
    title: String? = null,
    rows: List<SettingsRow>,
) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth()) {
        title?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp, top = 8.dp)
            )
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                rows.forEachIndexed { index, row ->
                    Material3SettingsItemRow(item = row.item)
                    if (index < rows.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                        )
                    }
                }
            }
        }
    }
}
