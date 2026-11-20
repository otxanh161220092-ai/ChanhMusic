package com.metrolist.music.archivetune.di

import com.metrolist.music.archivetune.lyrics.LyricsHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ArchiveTuneLyricsEntryPoint {
    fun archiveTuneLyricsHelper(): LyricsHelper
}
