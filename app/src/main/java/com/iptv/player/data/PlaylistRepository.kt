package com.iptv.player.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/** Loads playlist text from an HTTP(S) URL and parses it. */
class PlaylistRepository {

    suspend fun loadFromUrl(url: String): List<Channel> = withContext(Dispatchers.IO) {
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
}
