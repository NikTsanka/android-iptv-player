package com.iptv.player.ui

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import com.iptv.player.data.AppPrefs
import com.iptv.player.data.Channel
import com.iptv.player.data.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class UiState(
    val groups: List<String> = emptyList(),
    val selectedGroup: String = MainViewModel.ALL,
    val search: String = "",
    val favoritesOnly: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val filtered: List<Channel> = emptyList(),
    val current: Channel? = null,
    val status: String = "Ready",
    val playlistUrl: String = "",
    val isBuffering: Boolean = false,
)

@OptIn(UnstableApi::class)
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = PlaylistRepository()
    private val prefs = AppPrefs(app)

    private var allChannels: List<Channel> = emptyList()
    private val favorites: MutableSet<String> = prefs.loadFavorites()
    private var retryCount = 0

    // IPTV streams often carry AC-3/E-AC-3/MP2/DTS audio that the platform has no decoder
    // for ("video plays but no sound"). NextRenderersFactory bundles FFmpeg software decoders;
    // EXTENSION_RENDERER_MODE_ON keeps hardware decoding for supported codecs (e.g. AAC) and
    // only falls back to FFmpeg for the ones the device can't handle.
    private val renderersFactory = NextRenderersFactory(app).apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        setEnableDecoderFallback(true)
    }

    val player: ExoPlayer = ExoPlayer.Builder(app, renderersFactory).build().apply {
        playWhenReady = true
        volume = prefs.volume / 100f
        // Route through the media stream and request audio focus so sound actually plays.
        setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) retryCount = 0   // recovered
                _state.update {
                    it.copy(
                        isBuffering = playbackState == Player.STATE_BUFFERING,
                        status = when (playbackState) {
                            Player.STATE_BUFFERING -> "Buffering…"
                            Player.STATE_READY -> it.current?.let { c -> "▶ ${c.name}" } ?: it.status
                            Player.STATE_ENDED -> "Ended"
                            else -> it.status
                        }
                    )
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // Live IPTV streams hit transient drops (network blips, decoder hiccups).
                // Re-prepare to reconnect at the live edge; give up after a few tries.
                if (retryCount < MAX_RETRIES) {
                    retryCount++
                    _state.update { it.copy(status = "Reconnecting… ($retryCount)") }
                    player.prepare()
                } else {
                    _state.update { it.copy(status = "Playback error: ${error.errorCodeName}") }
                }
            }
        })
    }

    private val _state = MutableStateFlow(
        UiState(playlistUrl = prefs.lastUrl ?: DEFAULT_URL, favorites = favorites.toSet())
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        _state.value.playlistUrl.takeIf { it.isNotBlank() }?.let { loadUrl(it) }
    }

    fun setPlaylistUrl(url: String) = _state.update { it.copy(playlistUrl = url) }

    fun loadUrl(url: String = _state.value.playlistUrl) {
        if (url.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(status = "Loading…") }
            try {
                val channels = repo.loadFromUrl(url.trim())
                allChannels = channels
                prefs.lastUrl = url.trim()
                val groups = buildList {
                    add(ALL)
                    addAll(channels.map { it.group }.distinct().sortedBy { it.lowercase() })
                }
                _state.update {
                    it.copy(
                        groups = groups,
                        selectedGroup = ALL,
                        status = "${channels.size} channels",
                        playlistUrl = url.trim()
                    )
                }
                recompute()
            } catch (e: Exception) {
                _state.update { it.copy(status = "Load failed: ${e.message}") }
            }
        }
    }

    fun selectGroup(group: String) { _state.update { it.copy(selectedGroup = group) }; recompute() }
    fun setSearch(text: String) { _state.update { it.copy(search = text) }; recompute() }
    fun toggleFavoritesOnly() { _state.update { it.copy(favoritesOnly = !it.favoritesOnly) }; recompute() }

    fun play(channel: Channel) {
        retryCount = 0
        _state.update { it.copy(current = channel, status = "▶ ${channel.name}") }
        player.setMediaItem(MediaItem.fromUri(channel.streamUrl))
        player.prepare()
        player.play()
    }

    /** Zap to the next channel in the currently filtered list (wraps around). */
    fun playNext() = step(+1)

    /** Zap to the previous channel in the currently filtered list (wraps around). */
    fun playPrevious() = step(-1)

    private fun step(delta: Int) {
        val list = _state.value.filtered
        if (list.isEmpty()) return
        val idx = _state.value.current?.let { cur -> list.indexOfFirst { it.id == cur.id } } ?: -1
        val next = if (idx < 0) (if (delta > 0) 0 else list.lastIndex)
                   else ((idx + delta) % list.size + list.size) % list.size
        play(list[next])
    }

    fun toggleFavorite(channel: Channel) {
        if (!favorites.add(channel.id)) favorites.remove(channel.id)
        prefs.saveFavorites(favorites)
        _state.update { it.copy(favorites = favorites.toSet()) }
        recompute()
    }

    fun isFavorite(channel: Channel): Boolean = favorites.contains(channel.id)

    fun setVolume(percent: Int) {
        val clamped = percent.coerceIn(0, 100)
        prefs.volume = clamped
        player.volume = clamped / 100f
    }

    private fun recompute() {
        val s = _state.value
        val q = s.search.trim()
        val list = allChannels.filter { c ->
            (!s.favoritesOnly || favorites.contains(c.id)) &&
                (s.selectedGroup == ALL || c.group.equals(s.selectedGroup, ignoreCase = true)) &&
                (q.isEmpty() || c.name.contains(q, ignoreCase = true))
        }
        _state.update { it.copy(filtered = list) }
    }

    override fun onCleared() {
        player.release()
    }

    companion object {
        const val ALL = "All / ყველა / Все"
        private const val MAX_RETRIES = 3
        // Prefilled on first run so testing is one tap; overwritten once the user loads a URL.
        const val DEFAULT_URL =
            "http://5b986ba1727b.yourlistbest.net/playlists/uplist/c496ae799a5281d5a6861ddbd86886c7/playlist.m3u8"
    }
}
