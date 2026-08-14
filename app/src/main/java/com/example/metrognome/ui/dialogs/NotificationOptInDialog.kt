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
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * The one strategic ask for notification permission: shown once, the first time the
 * user opens the app on a 2nd distinct calendar day (a real, if modest, sign of
 * intent to keep using the app), never on first launch. This is a soft pre-ask - a
 * plain custom dialog - shown before the real system permission dialog, so a
 * "Not now" here costs nothing (Android limits how many times the system dialog
 * itself can be shown before requiring App Settings, so this keeps that budget for
 * a user who has already signalled interest).
 *
 * After this one showing, [onDismiss] with no [onEnable] leaves the door open only via
 * the Notifications row in Settings - this dialog does not reappear automatically.
 */
@Composable
fun NotificationOptInDialog(
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(onDismiss = onDismiss) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).background(AppColors.gold.copy(alpha = 0.14f), CircleShape),
        ) {
            Icon(
                Icons.Filled.Notifications,
                contentDescription = null,
                tint = AppColors.gold,
                modifier = Modifier.size(26.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "Stay in the loop",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Get notified about new sounds, features, and the occasional update from Metro. " +
                "You can turn this off anytime in Settings.",
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = onDismiss,
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Not now", color = AppColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                onClick = onEnable,
                shape = RoundedCornerShape(14.dp),
                color = AppColors.gold.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.75f)),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Enable", color = AppColors.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
