package com.example.metrognome.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * The app's one big action: a raised [AppColors.primaryPurple] key (see [RaisedControl]),
 * [ActionButtonHeight] tall, the same face as the play and TAP keys. Dialogs and overlays use
 * it for the single confirm/start/dismiss action; a pair of choices uses [GhostButton] with
 * [GoldButton] instead.
 *
 * Pass [modifier] to control width (e.g. fillMaxWidth or weight). [icon] adds a leading glyph.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
) {
    RaisedControl(
        onClick = { if (enabled) onClick() },
        shape = ActionButtonShape,
        tint = AppColors.primaryPurple,
        modifier = modifier.height(ActionButtonHeight).alpha(if (enabled) 1f else 0.4f),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp, lineHeight = 18.sp, letterSpacing = 1.sp)
        }
    }
}
