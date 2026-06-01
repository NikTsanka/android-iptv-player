package com.iptv.player.data

/** One IPTV channel parsed from an M3U playlist. */
data class Channel(
    val number: Int = 0,                 // 1-based position in the playlist
    val name: String,
    val streamUrl: String,
    val group: String = "Uncategorized",
    val logoUrl: String? = null,
    val tvgId: String? = null,
) {
    /** Stream URL is the stable identity used for favorites. */
    val id: String get() = streamUrl
}
