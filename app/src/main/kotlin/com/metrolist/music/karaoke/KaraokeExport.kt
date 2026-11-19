/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.karaoke

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Test path: separate an audio file and write the instrumental as a 16-bit stereo WAV. */
object KaraokeExport {

    private const val MODEL_NAME = "umx_vocals_int8.onnx"

    /** Copies the model out of assets once; the native runtime needs a real file path. */
    fun modelPath(context: Context): String {
        val target = File(context.filesDir, "karaoke/$MODEL_NAME")
        if (!target.exists() || target.length() == 0L) {
            target.parentFile?.mkdirs()
            context.assets.open("karaoke/$MODEL_NAME").use { input ->
                target.outputStream().use { input.copyTo(it) }
            }
        }
        return target.absolutePath
    }

    /** Returns the written file, or null when the input has no audio track. */
    fun export(context: Context, uri: Uri, name: String): File? {
        val (left, right) = KaraokeDecoder.decode(context, uri) ?: return null
        val instrumental = VocalSeparator(modelPath(context)).use { it.instrumental(left, right) }
        val dir = context.getExternalFilesDir("Karaoke") ?: context.filesDir
        dir.mkdirs()
        val out = File(dir, "${name.substringBeforeLast('.')}_instrumental.wav")
        writeWav(out, instrumental.first, instrumental.second, VocalSeparator.SAMPLE_RATE)
        return out
    }

    private fun writeWav(file: File, left: FloatArray, right: FloatArray, sampleRate: Int) {
        val frames = left.size
        val dataBytes = frames * 2 * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + dataBytes); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(2)
            putInt(sampleRate); putInt(sampleRate * 2 * 2); putShort(4); putShort(16)
            put("data".toByteArray()); putInt(dataBytes)
        }
        RandomAccessFile(file, "rw").use { raf ->
            raf.setLength(0)
            raf.write(header.array())
            val chunk = ByteBuffer.allocate(4 * 8192).order(ByteOrder.LITTLE_ENDIAN)
            var i = 0
            while (i < frames) {
                chunk.clear()
                val end = minOf(frames, i + 8192)
                for (f in i until end) {
                    chunk.putShort(toPcm(left[f]))
                    chunk.putShort(toPcm(right[f]))
                }
                raf.write(chunk.array(), 0, chunk.position())
                i = end
            }
        }
    }

    private fun toPcm(x: Float): Short = (x.coerceIn(-1f, 1f) * 32767f).toInt().toShort()
}
