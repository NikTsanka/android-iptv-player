package com.iptv.player.data

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** Tiny persistence: saved playlists, last playlist URL + favorite channel ids. */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    var lastUrl: String?
        get() = sp.getString("last_url", null)
        set(value) = sp.edit().putString("last_url", value).apply()

    /** User-managed list of playlist sources, persisted as JSON. */
    var playlists: List<Playlist>
        get() = sp.getString("playlists", null)
            ?.let { runCatching { Json.decodeFromString<List<Playlist>>(it) }.getOrNull() }
            ?: emptyList()
        set(value) = sp.edit().putString("playlists", Json.encodeToString(value)).apply()

    var volume: Int
        get() = sp.getInt("volume", 100)
        set(value) = sp.edit().putInt("volume", value).apply()

    fun loadFavorites(): MutableSet<String> =
        sp.getStringSet("favorites", emptySet())!!.toMutableSet()

    fun saveFavorites(ids: Set<String>) {
        sp.edit().putStringSet("favorites", ids).apply()
    }
}
