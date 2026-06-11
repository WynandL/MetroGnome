package com.example.metrognome.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * The "Let Metro listen (Beta)" opt-in row. Styled to match the plain settings switch
 * rows (e.g. Flash on Beat) so it reads as just another setting. One fixed heading and
 * subheading - no per-caller text - so the copy never drifts.
 *
 * The caller is responsible for showing this only when the toggle is appropriate; this
 * composable just renders the control.
 */
@Composable
fun MicOptIn(
    enabled: Boolean,
    hasMicPermission: Boolean,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    modifier: Modifier = Modifier,
    isPermanentlyDenied: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Timing Feedback",
                color = AppColors.textPrimary,
                fontWeight = FontWeight.Medium,
            )
            Text(
                "Uses the mic to measure your timing in the Speed Trainer, Practice, " +
                    "and Rhythm Game. Stay on the beat to earn bonus Gnotes.",
                color = AppColors.textMuted,
                lineHeight = 16.sp,
                fontSize = 12.sp,
            )
            // Only relevant if a caller offers this without permission already granted;
            // the Settings toggle handles permission via the check dialog, so it stays hidden.
            if (!hasMicPermission && !enabled) {
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isPermanentlyDenied)
                        "Permission blocked. Tap the switch to open App Settings."
                    else
                        "Microphone permission required",
                    color = if (isPermanentlyDenied) AppColors.gold.copy(alpha = 0.8f)
                            else AppColors.textSubtle,
                    fontSize = 9.sp,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = enabled,
            onCheckedChange = {
                if (!hasMicPermission && !enabled) onRequestPermission() else onToggle()
            },
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.gold,
                checkedTrackColor = AppColors.primaryPurple,
                uncheckedThumbColor = AppColors.controlInactive,
                uncheckedTrackColor = AppColors.surfaceVariant,
            ),
        )
    }
}
