package com.example.metrognome.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Every value that AppColors already names comes from there, so the Material scheme cannot
// drift from the app's own palette (UI audit U18). The on-colours stay literal: AppColors has
// no equivalents and they only tint Material defaults the app barely uses.
private val GnomeDarkColorScheme = darkColorScheme(
    primary          = Purple80,
    onPrimary        = Color(0xFF1A0040),
    primaryContainer = AppColors.primaryPurple,
    secondary        = AppColors.gold,
    onSecondary      = Color(0xFF1A1400),
    tertiary         = AppColors.danger,
    background       = AppColors.background,
    surface          = AppColors.surface,
    surfaceVariant   = AppColors.surfaceVariant,
    onBackground     = AppColors.textPrimary,
    onSurface        = AppColors.textPrimary,
    outline          = AppColors.mediumPurple,
)

@Composable
fun MetroGnomeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = GnomeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
