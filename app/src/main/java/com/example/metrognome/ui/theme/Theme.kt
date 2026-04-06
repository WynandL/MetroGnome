package com.example.metrognome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val GnomeDarkColorScheme = darkColorScheme(
    primary          = Purple80,
    onPrimary        = Color(0xFF1A0040),
    primaryContainer = Color(0xFF5B2D8A),
    secondary        = Color(0xFFFFD700),
    onSecondary      = Color(0xFF1A1400),
    tertiary         = Color(0xFFCC2233),
    background       = Color(0xFF0D0B1E),
    surface          = Color(0xFF1E1B3A),
    surfaceVariant   = Color(0xFF2A2550),
    onBackground     = Color(0xFFEEEEFF),
    onSurface        = Color(0xFFEEEEFF),
    outline          = Color(0xFF7B4DB0),
)

@Composable
fun MetroGnomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GnomeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
