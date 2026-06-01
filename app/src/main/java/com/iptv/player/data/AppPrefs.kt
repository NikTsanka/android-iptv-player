package com.iptv.player.data

import android.content.Context

/** Tiny persistence: last playlist URL + favorite channel ids (SharedPreferences). */
class AppPrefs(context: Context) {

    private val sp = context.getSharedPreferences("iptv", Context.MODE_PRIVATE)

    var lastUrl: String?
        get() = sp.getString("last_url", null)
        set(value) = sp.edit().putString("last_url", value).apply()

    var volume: Int
        get() = sp.getInt("volume", 100)
        set(value) = sp.edit().putInt("volume", value).apply()

    fun loadFavorites(): MutableSet<String> =
        sp.getStringSet("favorites", emptySet())!!.toMutableSet()

    fun saveFavorites(ids: Set<String>) {
        sp.edit().putStringSet("favorites", ids).apply()
    }
}
