package com.iptv.player.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Loads playlist text from an HTTP(S) URL or a local content/file URI and parses it. */
class PlaylistRepository(private val context: Context) {

    /** Loads from any supported source string (http(s):// URL or content://, file:// URI). */
    suspend fun load(source: String): List<Channel> =
        if (source.startsWith("content://") || source.startsWith("file://"))
            loadFromUri(source)
        else
            loadFromUrl(source)

    private suspend fun loadFromUrl(url: String): List<Channel> = withContext(Dispatchers.IO) {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "IptvPlayer/1.0")
            instanceFollowRedirects = true
        }
        try {
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                .let(M3UParser::parse)
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun loadFromUri(uri: String): List<Channel> = withContext(Dispatchers.IO) {
        context.contentResolver.openInputStream(Uri.parse(uri))
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            ?.let(M3UParser::parse)
            ?: emptyList()
    }
}
