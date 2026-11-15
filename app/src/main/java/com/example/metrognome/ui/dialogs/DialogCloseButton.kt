package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.clickable
import com.example.metrognome.ui.components.AppIcons
import com.example.metrognome.ui.components.AppIcon
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/** Small circular close button used in dialogs and overlays. */
@Composable
fun DialogCloseButton(onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
    ) {
        AppIcon(
            glyph = AppIcons.Close,
            contentDescription = "Close",
            tint = AppColors.textMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}
