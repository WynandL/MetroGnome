package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * The mic-mode opt-in row. Styled like the plain settings switch rows (e.g. Flash on Beat):
 * a heading, a one-line description, and the toggle. A small info icon next to the heading
 * expands the full disclosure (what the mic is used for, and the Classic-click override) so the
 * row stays clean while the detail is one tap away. One fixed copy here - no per-caller text -
 * so it never drifts.
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
    var showInfo by remember { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Groove Check",
                        color = AppColors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.width(5.dp))
                    Box(
                        modifier = Modifier
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { showInfo = !showInfo }
                            .padding(3.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Info,
                            contentDescription = "About Groove Check",
                            tint = if (showInfo) AppColors.textSecondary
                                   else AppColors.textDim.copy(alpha = 0.55f),
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
                Text(
                    "Stay on the beat to earn bonus Gnotes.",
                    color = AppColors.textMuted,
                    fontSize = 12.sp,
                )
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

        AnimatedVisibility(
            visible = showInfo,
            enter = expandVertically() + fadeIn(tween(180)),
            exit = shrinkVertically() + fadeOut(tween(180)),
        ) {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.surfaceVariant),
                )
                Spacer(Modifier.height(10.dp))
                // Only relevant if a caller offers this without permission already granted;
                // the Settings toggle handles permission via the check dialog, so it stays hidden.
                if (!hasMicPermission && !enabled) {
                    Text(
                        if (isPermanentlyDenied)
                            "Permission blocked. Tap the switch above to open App Settings."
                        else
                            "Microphone permission required to use this feature.",
                        color = if (isPermanentlyDenied) AppColors.gold.copy(alpha = 0.8f)
                                else AppColors.textSubtle,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Metro listens through the microphone to measure how closely your claps or " +
                        "taps land on the beat in the Speed Trainer, Practice, and Rhythm Game, " +
                        "and rewards staying in time with bonus Gnotes.\n\n" +
                        "While it is on, those features play the Classic click so the detector can " +
                        "hear your hits clearly. The sound you pick above still applies to the " +
                        "regular metronome.",
                    color = AppColors.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
    }
}
