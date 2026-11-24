/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.menu

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.metrolist.music.LocalDatabase
import com.metrolist.music.R
import com.metrolist.music.playlistimport.ExternalImportResult
import com.metrolist.music.playlistimport.ExternalPlaylistImport
import com.metrolist.music.playlistimport.ExternalTrack
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private const val OTHER_LANGUAGE = "Manual / Other"

/** Same choices as BitChord's import sheet; they steer the YouTube Music search. */
private val IMPORT_LANGUAGES = listOf(
    "Tamil", "Telugu", "Kannada", "Malayalam", "Hindi", "English",
    "Multilingual", "No Preference", OTHER_LANGUAGE,
)

/**
 * Paste a Spotify or Apple Music playlist link, match its songs on YouTube Music and keep
 * the result as a local playlist. Styled like BitChord's Spotify import card. Tracks that
 * matched nothing are listed after the import instead of being dropped silently.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ImportExternalPlaylistDialog(
    onDismiss: () -> Unit,
    onImported: (String) -> Unit,
) {
    val database = LocalDatabase.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var link by remember { mutableStateOf("") }
    var language by remember { mutableStateOf("No Preference") }
    var customLanguage by remember { mutableStateOf("") }
    var working by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ExternalImportResult?>(null) }

    fun start() {
        val trimmed = link.trim()
        if (!ExternalPlaylistImport.isSupported(trimmed)) {
            status = context.getString(R.string.import_link_invalid)
            failed = true
            return
        }
        val effectiveLanguage = if (language == OTHER_LANGUAGE) customLanguage.trim().ifBlank { null } else language
        working = true
        failed = false
        status = context.getString(R.string.import_link_fetching)
        scope.launch {
            val imported = try {
                ExternalPlaylistImport.importPlaylist(database, trimmed, effectiveLanguage) { done, total ->
                    status = context.getString(R.string.import_link_matching, done, total)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                null
            }
            working = false
            if (imported == null) {
                status = context.getString(R.string.import_link_failed)
                failed = true
                return@launch
            }
            onImported(imported.playlistId)
            if (imported.unmatched.isEmpty()) {
                onDismiss()
            } else {
                result = imported
                status = context.getString(R.string.import_link_result, imported.matched, imported.total)
            }
        }
    }

    Dialog(onDismissRequest = { if (!working) onDismiss() }) {
        Surface(
            modifier = Modifier.widthIn(max = 360.dp).fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            val finished = result
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.import_from_link),
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.import_link_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))

                if (finished != null) {
                    Text(
                        text = stringResource(R.string.import_link_unmatched),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(8.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        finished.unmatched.forEach { track: ExternalTrack ->
                            Text(
                                text = if (track.artists.isBlank()) track.title else "${track.title} — ${track.artists}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = link,
                        onValueChange = { link = it },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !working,
                        singleLine = true,
                        shape = RoundedCornerShape(percent = 50),
                        placeholder = { Text(text = stringResource(R.string.import_link_hint)) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = stringResource(R.string.import_link_language),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        IMPORT_LANGUAGES.forEach { option ->
                            val selected = option == language
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(percent = 50))
                                    .background(
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                    .clickable(enabled = !working) { language = option }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            ) {
                                Text(
                                    text = if (option == OTHER_LANGUAGE) stringResource(R.string.import_link_language_other) else option,
                                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
                                    color = if (selected) MaterialTheme.colorScheme.onPrimary
                                    else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    if (language == OTHER_LANGUAGE) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = customLanguage,
                            onValueChange = { customLanguage = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !working,
                            singleLine = true,
                            shape = RoundedCornerShape(percent = 50),
                            placeholder = { Text(text = stringResource(R.string.import_link_language_custom)) },
                        )
                    }
                }

                status?.let { text ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (finished != null) {
                        TextButton(onClick = onDismiss) { Text(text = stringResource(R.string.done)) }
                    } else {
                        TextButton(onClick = onDismiss, enabled = !working) {
                            Text(text = stringResource(R.string.cancel))
                        }
                        TextButton(onClick = ::start, enabled = link.isNotBlank() && !working) {
                            Text(text = stringResource(R.string.import_link_button))
                        }
                    }
                }
            }
        }
    }
}

