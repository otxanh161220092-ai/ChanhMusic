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
import com.metrolist.music.ui.component.SettingsIconTile
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.metrolist.music.ui.component.Material3SettingsItemRow
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.metrolist.music.ui.icons.SfIcons
import com.metrolist.music.ui.component.Material3SettingsItem
import com.metrolist.music.ui.component.ReleaseNotesCard
import com.metrolist.music.ui.utils.backToMain
import com.metrolist.music.utils.Updater
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
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

    fun route(
        label: String,
        icon: ImageVector,
        tint: Color,
        path: String,
    ) = Material3SettingsItem(
        leadingContent = { SettingsIconTile(icon = rememberVectorPainter(icon), tint = tint) },
        title = { Text(label) },
        onClick = { navController.navigate(path) },
    )

    val updateRow: Material3SettingsItem? = if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
        val releaseInfo = Updater.getCachedLatestRelease()
        val downloadUrl = releaseInfo?.let { Updater.getDownloadUrlForCurrentVariant(it) }
        if (downloadUrl != null) {
            Material3SettingsItem(
                leadingContent = { SettingsIconTile(icon = rememberVectorPainter(SfIcons.Upgrade), tint = SettingsTints.Grey) },
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
            )
        } else {
            null
        }
    } else {
        null
    }

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
                .padding(top = 16.dp, bottom = 8.dp)
                .size(72.dp),
        )

        SettingsCard(
            title = stringResource(R.string.settings_section_ui),
            items = listOf(
                route(stringResource(R.string.appearance), SfIcons.Appearance, SettingsTints.Purple, "settings/appearance")
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard(
            title = stringResource(R.string.settings_section_player_content),
            items = listOf(
                route(stringResource(R.string.player_and_audio), SfIcons.GraphicEq, SettingsTints.Pink, "settings/player"),
                route(stringResource(R.string.content), SfIcons.Language, SettingsTints.Mint, "settings/content"),
                route(stringResource(R.string.ai_lyrics_translation), SfIcons.Translate, SettingsTints.Peach, "settings/ai"),
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (hasAndroidAuto) {
            SettingsCard(
                title = "Android Auto",
                items = listOf(
                    route(stringResource(R.string.android_auto), SfIcons.Phone, SettingsTints.Sky, "settings/android_auto")
                )
            )

            Spacer(modifier = Modifier.height(16.dp))
        }

        SettingsCard(
            title = stringResource(R.string.settings_section_privacy),
            items = listOf(
                route(stringResource(R.string.privacy), SfIcons.Lock, SettingsTints.Slate, "settings/privacy")
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard(
            title = stringResource(R.string.settings_section_storage),
            items = listOf(
                route(stringResource(R.string.storage), SfIcons.Storage, SettingsTints.Teal, "settings/storage"),
                route(stringResource(R.string.backup_restore), SfIcons.CloudUpload, SettingsTints.Periwinkle, "settings/backup_restore"),
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        SettingsCard(
            title = stringResource(R.string.settings_section_system),
            items = buildList {
                if (BuildConfig.UPDATER_AVAILABLE) {
                    add(route(stringResource(R.string.updater), SfIcons.Upgrade, SettingsTints.Grey, "settings/updater"))
                }
                add(route(stringResource(R.string.about), SfIcons.Info, SettingsTints.Grey, "settings/about"))
                updateRow?.let { add(it) }
            }
        )
        if (BuildConfig.UPDATER_AVAILABLE && latestVersionName != BuildConfig.BASE_VERSION_NAME) {
            Spacer(modifier = Modifier.height(16.dp))
            ReleaseNotesCard()
        }

        Spacer(modifier = Modifier.height(16.dp))
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

private object SettingsTints {
    val Purple = Color(0xFFC9B8FF)     // appearance: lavender
    val Pink = Color(0xFFFFB8C8)       // playback: rose
    val Mint = Color(0xFFB8F0D2)       // content: mint
    val Peach = Color(0xFFFFD9A8)      // translation: peach
    val Sky = Color(0xFFAEDCFF)        // Android Auto: sky
    val Teal = Color(0xFFA8ECE6)       // storage: aqua
    val Periwinkle = Color(0xFFC8CCFF) // backup: periwinkle
    val Grey = Color(0xFFD6D6DE)       // about / updates: silver
    val Slate = Color(0xFFC9D3E6)      // privacy: slate
}

/** One rounded card holding rows, separated by a neutral grey divider, with an optional title above it. */
@Composable
private fun SettingsCard(
    title: String?,
    items: List<Material3SettingsItem>,
) {
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
                items.forEachIndexed { index, item ->
                    Material3SettingsItemRow(item = item)
                    if (index < items.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 64.dp),
                            color = Color.Gray.copy(alpha = 0.3f),
                        )
                    }
                }
            }
        }
    }
}
