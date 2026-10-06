package com.example.metrognome.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Width of one [HudIconAction]'s touch slot. */
val HUD_ACTION_WIDTH = 40.dp

/**
 * A small icon action inside a session bar (Practice timer, Speed Trainer HUD).
 *
 * The icon stays small, as the bar's design wants, but the touch slot is [HUD_ACTION_WIDTH]
 * wide and the bar's full height. The first cut made the 16 dp icon itself the clickable,
 * so back, skip and cancel sat 22 to 24 dp apart centre to centre and a slip on Skip
 * could end the session (UI audit U07). Put the slots side by side and they cannot overlap.
 */
@Composable
fun HudIconAction(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 16.dp,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .width(HUD_ACTION_WIDTH)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(iconSize))
    }
}
