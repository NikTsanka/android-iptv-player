package com.iptv.player.data

import kotlinx.serialization.Serializable

/** A saved playlist source (M3U/M3U8 URL) the user can switch between. */
@Serializable
data class Playlist(
    val name: String,
    val url: String,
) {
    /** URL is the stable identity. */
    val id: String get() = url
}
