package com.metrolist.music.ui.component.topshelf

import androidx.compose.ui.Modifier

/** Phone build: no secondary mouse button, so the context click is a no-op (BitChord's Android actual). */
fun Modifier.contextClick(onClick: (() -> Unit)?): Modifier = this
