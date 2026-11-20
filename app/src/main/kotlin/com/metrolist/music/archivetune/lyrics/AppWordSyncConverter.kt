package com.metrolist.music.archivetune.lyrics

private val LRC_LINE_START_REGEX = Regex("""^(\[\d\d:\d\d\.\d{2,3}\])""")
private val APP_WORD_LINE_REGEX = Regex("""^<(.+)>$""")

/**
 * The app stores word-level lyrics as an LRC line followed by a line of `word:start:end` items
 * separated by `|` (start/end in seconds), wrapped in `<...>`. The ported parser reads the
 * `[mm:ss.xx] <mm:ss.xx>word` form instead, so convert the app form into that before parsing.
 */
internal fun convertAppWordSyncLines(text: String): String {
    if (!text.contains("\n<")) return text
    val lines = text.lines()
    val out = ArrayList<String>(lines.size)
    var i = 0
    while (i < lines.size) {
        val line = lines[i]
        val lineStart = LRC_LINE_START_REGEX.find(line.trim())?.value
        val wordLine = lines.getOrNull(i + 1)?.trim()?.let { APP_WORD_LINE_REGEX.matchEntire(it) }
        if (lineStart != null && wordLine != null) {
            val words =
                wordLine.groupValues[1].split("|").mapNotNull { item ->
                    val parts = item.split(":")
                    if (parts.size < 3) return@mapNotNull null
                    val wordText = parts.dropLast(2).joinToString(":")
                    val start = parts[parts.size - 2].toDoubleOrNull() ?: return@mapNotNull null
                    if (wordText.isBlank()) null else "<${formatWordTime(start)}>$wordText"
                }
            if (words.isNotEmpty()) {
                out += lineStart + words.joinToString("")
                i += 2
                continue
            }
        }
        out += line
        i++
    }
    return out.joinToString("\n")
}

private fun formatWordTime(seconds: Double): String {
    val ms = (seconds * 1000).toLong()
    val minutes = (ms / 60_000).toString().padStart(2, '0')
    val secs = ((ms % 60_000) / 1000).toString().padStart(2, '0')
    val centis = ((ms % 1000) / 10).toString().padStart(2, '0')
    return "$minutes:$secs.$centis"
}
