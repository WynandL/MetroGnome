package com.example.metrognome.ui.overlays

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Shared all-caps "eyebrow" label sitting above a result-overlay title.
 *
 * Centralises the typographic treatment so every overlay header reads as the same
 * design-system element; only [text] and [color] vary per overlay (e.g. muted for the
 * Speed Trainer label, gold for the celebratory Practice header).
 */
@Composable
fun OverlayEyebrow(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.5.sp,
        lineHeight = 14.sp,
        modifier = modifier,
    )
}
