package com.iptv.player.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColors = darkColorScheme(
    primary = Color(0xFF3D6FB4),
    onPrimary = Color.White,
    secondary = Color(0xFF2E6BD6),
    background = Color(0xFF101216),
    onBackground = Color(0xFFEDEDED),
    surface = Color(0xFF15181F),
    onSurface = Color(0xFFEDEDED),
    surfaceVariant = Color(0xFF20242E),
)

@Composable
fun IptvTheme(content: @Composable () -> Unit) {
    // App is dark-first (TV friendly); ignore system light/dark for a consistent look.
    @Suppress("UNUSED_EXPRESSION") isSystemInDarkTheme()
    MaterialTheme(colorScheme = DarkColors, content = content)
}
