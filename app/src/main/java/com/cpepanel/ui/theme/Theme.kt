package com.cpepanel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 网络运维面板配色
val Bg      = Color(0xFF0B1220)
val Panel   = Color(0xFF131C2E)
val PanelHi = Color(0xFF1B2740)
val Stroke  = Color(0xFF2A3A57)

val Cyan    = Color(0xFF22D3EE)
val Mint    = Color(0xFF34D399)
val Blue    = Color(0xFF60A5FA)
val Amber   = Color(0xFFFBBF24)
val Rose    = Color(0xFFF87171)
val Violet  = Color(0xFFA78BFA)

val TextHi  = Color(0xFFE6EDF7)
val TextLo  = Color(0xFF8FA3BF)
val TextMu  = Color(0xFF5D7291)

private val Scheme = darkColorScheme(
    primary = Cyan,
    onPrimary = Color(0xFF04202A),
    primaryContainer = Color(0xFF0E3A46),
    onPrimaryContainer = Cyan,
    secondary = Mint,
    onSecondary = Color(0xFF04231A),
    background = Bg,
    onBackground = TextHi,
    surface = Panel,
    onSurface = TextHi,
    surfaceVariant = PanelHi,
    onSurfaceVariant = TextLo,
    outline = Stroke,
    error = Rose,
    onError = Color(0xFF2A0A0A)
)

@Composable
fun CpePanelTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Scheme, content = content)
}
