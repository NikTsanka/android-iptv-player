package com.iptv.player.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.iptv.player.data.Channel
import com.iptv.player.data.Playlist
import com.iptv.player.player.PlayerSurface
import kotlinx.coroutines.delay
import kotlin.math.abs

/**
 * Fullscreen "live TV" layout: the video fills the screen and a channel panel slides in
 * over it. D-pad up/down zaps channels when the panel is closed; center/menu opens it.
 */
@Composable
fun MainScreen(vm: MainViewModel, inPipMode: Boolean = false) {
    val state by vm.state.collectAsState()

    var panelOpen by remember { mutableStateOf(true) }

    // In Picture-in-Picture the window is tiny — show only the video, no overlays.
    LaunchedEffect(inPipMode) { if (inPipMode) panelOpen = false }
    var playlistDialog by remember { mutableStateOf(false) }
    var volumeOsd by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }

    // Flash the volume OSD for a moment whenever the volume changes, then auto-hide.
    LaunchedEffect(state.volumeNonce) {
        if (state.volumeNonce > 0) {
            volumeOsd = true
            delay(1500)
            volumeOsd = false
        }
    }

    // Android 13+ routes Back through the predictive-back dispatcher, not as a key event,
    // so close the panel here. When the panel is closed, Back falls through and exits.
    BackHandler(enabled = panelOpen) { panelOpen = false }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(rootFocus)
            .focusable()
            .onPreviewKeyEvent { e ->
                if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (e.key) {
                    Key.Menu -> { panelOpen = !panelOpen; true }
                    Key.DirectionUp -> if (!panelOpen) { vm.playPrevious(); true } else false
                    Key.DirectionDown -> if (!panelOpen) { vm.playNext(); true } else false
                    Key.DirectionLeft -> if (!panelOpen) { vm.adjustVolume(-5); true } else false
                    Key.DirectionRight -> if (!panelOpen) { vm.adjustVolume(+5); true } else false
                    Key.DirectionCenter, Key.Enter ->
                        if (!panelOpen) { panelOpen = true; true } else false
                    else -> false
                }
            }
    ) {
        // Video surface — fills the screen behind everything.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { panelOpen = !panelOpen }
                }
                // Swipe gestures (only when the panel is closed):
                //   vertical   → zap channel (up = next, down = previous)
                //   horizontal → volume (right = louder, left = quieter), continuous
                .pointerInput(panelOpen) {
                    if (panelOpen) return@pointerInput
                    val slop = 28.dp.toPx()
                    var dx = 0f
                    var dy = 0f
                    var axis = 0 // 0 = undecided, 1 = horizontal, 2 = vertical
                    var startVolume = 0
                    detectDragGestures(
                        onDragStart = { dx = 0f; dy = 0f; axis = 0; startVolume = vm.state.value.volume },
                        onDrag = { change, amount ->
                            change.consume()
                            dx += amount.x
                            dy += amount.y
                            if (axis == 0 && (abs(dx) > slop || abs(dy) > slop)) {
                                axis = if (abs(dy) > abs(dx)) 2 else 1
                            }
                            if (axis == 1) {
                                val pct = (dx / size.width * 100f).toInt()
                                vm.setVolume(startVolume + pct)
                            }
                        },
                        onDragEnd = {
                            if (axis == 2) {
                                if (dy < 0) vm.playNext() else vm.playPrevious()
                            }
                        }
                    )
                }
        ) {
            PlayerSurface(vm.player, Modifier.fillMaxSize())
        }

        if (state.isBuffering && !inPipMode) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Status line (bottom) — hidden while the panel is open to keep it clean.
        if (!panelOpen && !inPipMode) {
            Text(
                text = state.status,
                color = Color.White.copy(alpha = 0.85f),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .safeDrawingPadding()
                    .padding(16.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }

        // Transient volume OSD (D-pad ←/→).
        AnimatedVisibility(
            visible = volumeOsd && !panelOpen && !inPipMode,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(bottom = 56.dp)
        ) {
            VolumeOsd(volume = state.volume)
        }

        // Sliding channel panel.
        AnimatedVisibility(
            visible = panelOpen && !inPipMode,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChannelPanel(
                vm = vm,
                onOpenPlaylists = { playlistDialog = true },
                onPlay = { vm.play(it) }
            )
        }
    }

    if (playlistDialog) {
        // SAF picker for importing a local .m3u/.m3u8 file (any type — m3u MIME is unreliable).
        val importLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri ->
            if (uri != null) { vm.importPlaylistFromUri(uri); playlistDialog = false }
        }
        PlaylistDialog(
            playlists = state.playlists,
            currentUrl = state.playlistUrl,
            onSelect = { vm.selectPlaylist(it); playlistDialog = false },
            onAdd = { name, url -> vm.addPlaylist(name, url); playlistDialog = false },
            onRemove = { vm.removePlaylist(it) },
            onImportFile = { importLauncher.launch(arrayOf("*/*")) },
            onDismiss = { playlistDialog = false }
        )
    }
}

@Composable
private fun ChannelPanel(
    vm: MainViewModel,
    onOpenPlaylists: () -> Unit,
    onPlay: (Channel) -> Unit,
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    // Keep the playing channel in view whenever it changes (e.g. D-pad zapping).
    LaunchedEffect(state.current?.id, state.filtered.size) {
        val idx = state.filtered.indexOfFirst { it.id == state.current?.id }
        if (idx >= 0) listState.scrollToItem(idx)
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        modifier = Modifier
            .fillMaxHeight()
            .width(380.dp)
            .safeDrawingPadding()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {

            // Header: search + favorites + load-url actions.
            OutlinedTextField(
                value = state.search,
                onValueChange = vm::setSearch,
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                placeholder = { Text("Search / ძებნა") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.favoritesOnly,
                    onClick = { vm.toggleFavoritesOnly() },
                    label = { Text("★ Favorites") }
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onOpenPlaylists) {
                    Icon(Icons.Filled.PlaylistPlay, contentDescription = "Playlists")
                }
                Spacer(Modifier.width(4.dp))
                Text(
                    text = state.status,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            // Group filter chips.
            if (state.groups.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    contentPadding = PaddingValues(vertical = 2.dp)
                ) {
                    items(state.groups) { g ->
                        // The "All" group label is long (trilingual); shorten it on the chip.
                        val name = if (g == MainViewModel.ALL) "All" else g
                        val count = state.groupCounts[g]
                        FilterChip(
                            selected = g == state.selectedGroup,
                            onClick = { vm.selectGroup(g) },
                            label = {
                                Text(
                                    if (count != null) "$name  $count" else name,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors()
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            // Channel list.
            LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(state.filtered, key = { it.id }) { ch ->
                    ChannelRow(
                        channel = ch,
                        isCurrent = state.current?.id == ch.id,
                        isFavorite = state.favorites.contains(ch.id),
                        onPlay = { onPlay(ch) },
                        onToggleFavorite = { vm.toggleFavorite(ch) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: Channel,
    isCurrent: Boolean,
    isFavorite: Boolean,
    onPlay: () -> Unit,
    onToggleFavorite: () -> Unit,
) {
    val bg = if (isCurrent)
        MaterialTheme.colorScheme.primary.copy(alpha = 0.30f)
    else
        Color.Transparent

    Surface(
        onClick = onPlay,
        color = bg,
        // With a transparent/primary background Material can't infer a readable content
        // colour, so set it explicitly — otherwise the channel name renders near-black.
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = channel.number.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.width(30.dp)
            )
            // Channel logo (reserve the slot even when absent, so names stay aligned).
            // A faint TV glyph sits behind as the placeholder/fallback; the logo crossfades
            // in over it and replaces it on error.
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Filled.LiveTv,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                    modifier = Modifier.size(22.dp)
                )
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(channel.logoUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleFavorite) {
                if (isFavorite)
                    Icon(Icons.Filled.Star, contentDescription = "Unfavorite",
                        tint = MaterialTheme.colorScheme.primary)
                else
                    Icon(Icons.Outlined.StarBorder, contentDescription = "Favorite",
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            }
        }
    }
}

@Composable
private fun PlaylistDialog(
    playlists: List<Playlist>,
    currentUrl: String,
    onSelect: (Playlist) -> Unit,
    onAdd: (name: String, url: String) -> Unit,
    onRemove: (Playlist) -> Unit,
    onImportFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlists") },
        text = {
            // Whole body scrolls so the Add/Import controls stay reachable above the keyboard.
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (playlists.isEmpty()) {
                    Text(
                        "No playlists yet. Add a URL or import a file below.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                } else {
                    // Saved playlists: tap to load, trash to remove.
                    playlists.forEach { p ->
                        val selected = p.url == currentUrl
                        Surface(
                            onClick = { onSelect(p) },
                            color = if (selected)
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                            else Color.Transparent,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        p.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        p.url,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(onClick = { onRemove(p) }) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Remove",
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(Modifier.padding(vertical = 10.dp))

                // Add a new playlist by URL — inline button so it stays visible with the keyboard up.
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    label = { Text("Name (optional)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        singleLine = true,
                        label = { Text("M3U / M3U8 URL") },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { if (url.isNotBlank()) onAdd(name, url.trim()) },
                        enabled = url.isNotBlank()
                    ) { Text("Add") }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = onImportFile, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Import .m3u file")
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun VolumeOsd(volume: Int) {
    Surface(
        color = Color.Black.copy(alpha = 0.65f),
        contentColor = Color.White,
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(
                if (volume == 0) Icons.AutoMirrored.Filled.VolumeOff
                else Icons.AutoMirrored.Filled.VolumeUp,
                contentDescription = null
            )
            Spacer(Modifier.width(12.dp))
            LinearProgressIndicator(
                progress = { volume / 100f },
                modifier = Modifier.width(160.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = Color.White.copy(alpha = 0.25f),
            )
            Spacer(Modifier.width(12.dp))
            Text("$volume%", style = MaterialTheme.typography.labelLarge)
        }
    }
}
