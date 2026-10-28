/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.metrolist.music.BuildConfig
import com.metrolist.music.LocalDatabase
import com.metrolist.music.LocalNavController
import com.metrolist.music.LocalPlayerAwareWindowInsets
import com.metrolist.music.R
import com.metrolist.music.constants.PauseListenHistoryKey
import com.metrolist.music.utils.rememberPreference

/**
 * Padding for tab screens. The top app bar is hidden on these screens, so only the status bar
 * sits above the content; the player and navigation insets stay as they are.
 */
@Composable
fun tabScreenContentPadding(): PaddingValues {
    val playerAware = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val direction = LocalLayoutDirection.current
    return PaddingValues(
        start = playerAware.calculateLeftPadding(direction),
        end = playerAware.calculateRightPadding(direction),
        top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
        bottom = playerAware.calculateBottomPadding(),
    )
}

/** Large title row for tab screens: title, account avatar, and a menu with history, stats and listen together. */
@Composable
fun TabHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val navController = LocalNavController.current
    val database = LocalDatabase.current
    val pauseListenHistory by rememberPreference(PauseListenHistoryKey, defaultValue = false)
    val eventCount by database.eventCount().collectAsStateWithLifecycle(initialValue = 0)
    val showHistoryButton = !(pauseListenHistory && eventCount == 0)

    var showMenu by remember { mutableStateOf(false) }
    var showAccountDialog by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { showAccountDialog = true },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.person),
                contentDescription = stringResource(R.string.account),
                modifier = Modifier.size(22.dp),
            )
        }
        Box {
            IconButton(onClick = { showMenu = true }) {
                Icon(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = null,
                )
            }
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false },
            ) {
                if (showHistoryButton) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.history)) },
                        leadingIcon = {
                            Icon(painter = painterResource(R.drawable.history), contentDescription = null)
                        },
                        onClick = {
                            showMenu = false
                            navController.navigate("history")
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.stats)) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.stats), contentDescription = null)
                    },
                    onClick = {
                        showMenu = false
                        navController.navigate("stats")
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.together)) },
                    leadingIcon = {
                        Icon(painter = painterResource(R.drawable.group_outlined), contentDescription = null)
                    },
                    onClick = {
                        showMenu = false
                        navController.navigate("listen_together_from_topbar")
                    },
                )
            }
        }
    }

    if (showAccountDialog) {
        AccountSettingsDialog(
            onDismiss = { showAccountDialog = false },
            latestVersionName = BuildConfig.BASE_VERSION_NAME,
        )
    }
}
