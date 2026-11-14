package com.metrolist.music.bcmix.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Normalises a YouTube data-sync id. Approximation: keeps the part before the
 * `||` separator. NOT an exact port of BitChord's rule.
 */
fun normalizeDataSyncId(raw: String?): String? =
    raw?.trim()?.substringBefore("||")?.takeIf { it.isNotEmpty() }

/**
 * Preference store for BitChord's source settings. NOT PORTED: BitChord's
 * store is encrypted; this is a plain SharedPreferences file.
 */
object EncryptedPrefs {
    fun open(context: Context, name: String, plainName: String): SharedPreferences =
        context.getSharedPreferences(name, Context.MODE_PRIVATE)
}
