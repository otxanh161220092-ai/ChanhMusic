/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.viewinterop.AndroidView
import com.metrolist.music.lyrics.LyricsEntry
import kotlinx.coroutines.isActive
import org.json.JSONArray
import timber.log.Timber
import org.json.JSONObject

/**
 * Word-by-word lyrics from Braccato (MIT, github.com/better-lyrics/braccato). The renderer runs in a
 * WebView, loaded from a pinned npm build on jsdelivr, and is driven from here: the lines go in once
 * per song and the clock is written every frame while the song plays.
 */
private const val BRACCATO_VERSION = "1.18.0"
private const val BRACCATO_CDN = "https://cdn.jsdelivr.net/npm/@braccato/core@$BRACCATO_VERSION"
private const val LAST_LINE_MS = 5_000L

private val braccatoPage =
    """
    <!doctype html>
    <html>
    <head>
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <link rel="stylesheet" href="$BRACCATO_CDN/dist/styles/variables.css">
    <link rel="stylesheet" href="$BRACCATO_CDN/dist/styles/lyrics.css">
    <link rel="stylesheet" href="$BRACCATO_CDN/dist/styles/instrumental.css">
    <style>
      /* Braccato's default colours use display-p3 and relative colour syntax, which older WebViews drop,
         leaving the text unreadable. Plain sRGB values keep it visible on any WebView. */
      :root {
        --blyrics-text-color: #ffffff;
        --blyrics-ui-text-color: #ffffff;
        --blyrics-lyric-active-color: #ffffff;
        --blyrics-lyric-inactive-color: rgba(255, 255, 255, 0.35);
        --blyrics-glow-color: rgba(255, 255, 255, 0.5);
      }
      html, body { margin: 0; height: 100%; overflow: hidden; background: transparent; }
      braccato-lyrics { display: block; height: 100%; }
    </style>
    </head>
    <body>
    <braccato-lyrics id="lyrics"></braccato-lyrics>
    <script type="module">
      import "$BRACCATO_CDN/dist/element.js";
      const view = document.getElementById("lyrics");
      view.addEventListener("braccato:line-click", event => {
        Android.onLineClick(Math.round(event.detail.timeS * 1000));
      });
      window.setLyrics = lines => { view.lyrics = lines; };
      window.setTime = (ms, playing) => { view.playing = playing; view.currentTime = ms / 1000; };
      customElements.whenDefined("braccato-lyrics").then(() => Android.onReady());
    </script>
    </body>
    </html>
    """.trimIndent()

/** Braccato's `Lyric` shape: each line runs until the next one starts. */
private fun linesToJson(lines: List<LyricsEntry>): String {
    val array = JSONArray()
    lines.forEachIndexed { index, line ->
        val next = lines.getOrNull(index + 1)
        val duration = if (next != null) (next.time - line.time).coerceAtLeast(1L) else LAST_LINE_MS
        array.put(
            JSONObject()
                .put("startTimeMs", line.time)
                .put("durationMs", duration)
                .put("words", line.text),
        )
    }
    return array.toString()
}

private class BraccatoBridge(
    private val onReady: () -> Unit,
    private val onLineClick: (Long) -> Unit,
) {
    @JavascriptInterface
    fun onReady() = onReady.invoke()

    @JavascriptInterface
    fun onLineClick(timeMs: Long) = onLineClick.invoke(timeMs)
}

/**
 * Shows [lines] with Braccato, following [positionMs] while [isPlaying]. A tap on a line calls
 * [onSeekMs] with the line's start.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun BraccatoLyrics(
    lines: List<LyricsEntry>,
    positionMs: () -> Long,
    isPlaying: Boolean,
    onSeekMs: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    var pageReady by remember { mutableStateOf(false) }
    val currentSeek by rememberUpdatedState(onSeekMs)
    val currentPosition by rememberUpdatedState(positionMs)
    // The bridge is called from WebView's thread, so state changes are posted to the main thread.
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val json = remember(lines) { linesToJson(lines) }

    LaunchedEffect(webView, pageReady, json) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        view.evaluateJavascript("setLyrics($json);", null)
    }

    // Written every frame while the song plays, so the highlight moves smoothly; once when paused.
    LaunchedEffect(webView, pageReady, isPlaying) {
        val view = webView ?: return@LaunchedEffect
        if (!pageReady) return@LaunchedEffect
        if (!isPlaying) {
            view.evaluateJavascript("setTime(${currentPosition()}, false);", null)
            return@LaunchedEffect
        }
        while (isActive) {
            view.evaluateJavascript("setTime(${currentPosition()}, true);", null)
            withFrameNanos { }
        }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(Color.TRANSPARENT)
                settings.javaScriptEnabled = true
                webViewClient = WebViewClient()
                webChromeClient =
                    object : WebChromeClient() {
                        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                            Timber.tag("Braccato").d("%s (%s:%d)", message.message(), message.sourceId(), message.lineNumber())
                            return true
                        }
                    }
                addJavascriptInterface(
                    BraccatoBridge(
                        onReady = { mainHandler.post { pageReady = true } },
                        onLineClick = { timeMs -> mainHandler.post { currentSeek(timeMs) } },
                    ),
                    "Android",
                )
                loadDataWithBaseURL("https://metrolist.local/", braccatoPage, "text/html", "utf-8", null)
                webView = this
            }
        },
        onRelease = { view ->
            view.removeJavascriptInterface("Android")
            view.destroy()
            webView = null
            pageReady = false
        },
    )
}
