package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.components.GhostButton
import com.example.metrognome.ui.components.DangerButton
import com.example.metrognome.ui.theme.AppColors

/**
 * Generic destructive-action confirmation dialog.
 *
 * Springs in via [AppDialog]. The confirm button is tinted [AppColors.stopRed] to signal
 * irreversibility; the dismiss button is a neutral outline so it reads as the safe choice.
 *
 * Use for any one-way action that loses data: stopping a session, clearing calibration, etc.
 */
@Composable
fun ConfirmDestructiveDialog(
    title: String,
    body: String,
    dismissLabel: String,
    confirmLabel: String,
    icon: ImageVector = Icons.Filled.Bolt,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(onDismiss = onDismiss, minWidth = 260.dp, maxWidth = 340.dp) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .background(AppColors.stopRed.copy(alpha = 0.15f), CircleShape),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AppColors.stopRed,
                modifier = Modifier.size(24.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        DialogTitle(title)

        Spacer(Modifier.height(8.dp))

        Text(
            text = body,
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(22.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            GhostButton(dismissLabel, onDismiss, Modifier.weight(1f))

            Spacer(Modifier.width(10.dp))

            DangerButton(confirmLabel, onConfirm, Modifier.weight(1f))
        }
    }
}
