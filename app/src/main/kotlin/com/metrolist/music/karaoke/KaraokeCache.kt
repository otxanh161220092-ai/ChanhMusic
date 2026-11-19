/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * Separated stems kept per track, so a song is split once and then plays from disk. Each track
 * has an instrumental and a vocal WAV, written under a temporary name and renamed only when both
 * are complete, so an interrupted run never leaves a half-written stem that looks ready.
 */
object KaraokeCache {

    private fun dir(context: Context, key: String): File {
        val safe = key.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.ifEmpty { "track" }
        return File(context.filesDir, "karaoke/cache/$safe")
    }

    fun instrumentalFile(context: Context, key: String): File = File(dir(context, key), "instrumental.wav")

    fun vocalFile(context: Context, key: String): File = File(dir(context, key), "vocals.wav")

    /** Whether both stems for [key] are already on disk. */
    fun isReady(context: Context, key: String): Boolean =
        instrumentalFile(context, key).let { it.isFile && it.length() > 44 } &&
            vocalFile(context, key).let { it.isFile && it.length() > 44 }

    /**
     * Separates the audio at [uri] into the stems for [key], unless they already exist. Reports
     * progress from 0 to 1. Returns false when the input has no audio or the work fails.
     */
    fun prepare(
        context: Context,
        key: String,
        uri: Uri,
        onProgress: (Float) -> Unit = {},
    ): Boolean {
        if (isReady(context, key)) {
            onProgress(1f)
            return true
        }
        val (left, right) = KaraokeDecoder.decode(context, uri) ?: return false
        val target = dir(context, key).apply { mkdirs() }
        val instTmp = File(target, "instrumental.wav.part")
        val vocTmp = File(target, "vocals.wav.part")
        return try {
            WavWriter(instTmp, left.size, VocalSeparator.SAMPLE_RATE).use { inst ->
                WavWriter(vocTmp, left.size, VocalSeparator.SAMPLE_RATE).use { voc ->
                    VocalSeparator(KaraokeExport.modelPath(context)).use { separator ->
                        separator.separate(left, right, onProgress) { il, ir, vl, vr, n ->
                            inst.write(il, ir, n)
                            voc.write(vl, vr, n)
                        }
                    }
                }
            }
            instTmp.renameTo(instrumentalFile(context, key)) && vocTmp.renameTo(vocalFile(context, key))
        } catch (t: Throwable) {
            instTmp.delete()
            vocTmp.delete()
            false
        }
    }
}
