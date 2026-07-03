package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * A static, always-identical copy of Metro for use outside the main GnomeCanvas — icons,
 * dialogs, badges, previews. Draws through the same [drawGnome] function the animated
 * metronome screen uses, so any future change to Metro's likeness applies here too, with
 * zero risk of the two drifting apart.
 *
 * No animation, no cosmetic items: this is the plain reference likeness. Caller controls
 * size and position via [modifier] (e.g. `Modifier.size(96.dp)`); Metro is laid out the
 * same fraction of the box (u = height / 17, centered, feet at 97% height) as on the main
 * screen, so proportions match regardless of the box size used.
 */
@Composable
fun MetroAvatar(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val u = size.height / 17f
        val cx = size.width / 2f
        val baseY = size.height * 0.97f
        drawGnome(u = u, cx = cx, baseY = baseY)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF1A1040, widthDp = 160, heightDp = 220)
@Composable
private fun MetroAvatarPreview() {
    MetroAvatar(modifier = Modifier.size(width = 160.dp, height = 220.dp))
}
