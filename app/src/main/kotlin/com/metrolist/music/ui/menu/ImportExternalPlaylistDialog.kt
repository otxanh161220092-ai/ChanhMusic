/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import android.widget.Toast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.playlistimport.ExternalPlaylistImport
import com.metrolist.music.ui.component.TextFieldDialog
import kotlinx.coroutines.launch

@Composable
fun ImportExternalPlaylistDialog(
    onDismiss: () -> Unit,
    onImported: (String) -> Unit,
) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }

    TextFieldDialog(
        icon = { Icon(painter = painterResource(R.drawable.download), contentDescription = null) },
        title = { Text(text = stringResource(R.string.import_from_link)) },
        placeholder = { Text(text = stringResource(R.string.import_link_hint)) },
        singleLine = true,
        autoFocus = true,
        isInputValid = { ExternalPlaylistImport.isSupported(it.trim()) },
        onDismiss = onDismiss,
        onDone = { link ->
            if (!busy) {
                busy = true
                Toast.makeText(context, context.getString(R.string.import_link_started), Toast.LENGTH_SHORT).show()
                scope.launch {
                    val result = ExternalPlaylistImport.importPlaylist(database, link.trim())
                    busy = false
                    if (result == null) {
                        Toast.makeText(context, context.getString(R.string.import_link_failed), Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.import_link_result, result.matched, result.total),
                            Toast.LENGTH_LONG,
                        ).show()
                        onImported(result.playlistId)
                    }
                    onDismiss()
                }
            }
        },
    )
}
