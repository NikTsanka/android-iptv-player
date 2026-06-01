package com.iptv.player.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.iptv.player.data.Channel
import com.iptv.player.player.PlayerSurface

/**
 * Fullscreen "live TV" layout: the video fills the screen and a channel panel slides in
 * over it. D-pad up/down zaps channels when the panel is closed; center/menu opens it.
 */
@Composable
fun MainScreen(vm: MainViewModel) {
    val state by vm.state.collectAsState()

    var panelOpen by remember { mutableStateOf(true) }
    var urlDialog by remember { mutableStateOf(false) }
    val rootFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { rootFocus.requestFocus() }

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
        ) {
            PlayerSurface(vm.player, Modifier.fillMaxSize())
        }

        if (state.isBuffering) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
        }

        // Status line (bottom) — hidden while the panel is open to keep it clean.
        if (!panelOpen) {
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

        // Sliding channel panel.
        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally(initialOffsetX = { -it }),
            exit = slideOutHorizontally(targetOffsetX = { -it }),
            modifier = Modifier.align(Alignment.CenterStart)
        ) {
            ChannelPanel(
                vm = vm,
                onOpenUrlDialog = { urlDialog = true },
                onPlay = { vm.play(it) }
            )
        }
    }

    if (urlDialog) {
        UrlDialog(
            initial = state.playlistUrl,
            onDismiss = { urlDialog = false },
            onLoad = { url -> vm.setPlaylistUrl(url); vm.loadUrl(url); urlDialog = false }
        )
    }
}

@Composable
private fun ChannelPanel(
    vm: MainViewModel,
    onOpenUrlDialog: () -> Unit,
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
                IconButton(onClick = onOpenUrlDialog) {
                    Icon(Icons.Filled.Link, contentDescription = "Load URL")
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
                        FilterChip(
                            selected = g == state.selectedGroup,
                            onClick = { vm.selectGroup(g) },
                            label = {
                                Text(g, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
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
private fun UrlDialog(
    initial: String,
    onDismiss: () -> Unit,
    onLoad: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Playlist URL") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = false,
                placeholder = { Text("http://…/playlist.m3u") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onLoad(text.trim()) }) {
                Text("Load")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
