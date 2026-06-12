package com.example.metrognome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.audio.selftest.MicCalibration
import com.example.metrognome.ui.theme.AppColors

/**
 * Small reminder that the app-wide microphone timing feature is currently active.
 *
 * Shown in the three mic-scoring surfaces (Speed Trainer, Practice, Rhythm Game) so a
 * user always has an in-app signal that the mic is in use here — not only the system
 * mic icon in the status bar. It is informational: the single control lives in
 * Settings, and this just points there.
 *
 * Reads the one master flag [MicCalibration.isActive] (the user's opt-in AND a passing
 * calibration). Renders nothing when the feature is off, so callers drop it in
 * unconditionally; the leading spacing is only emitted when it actually shows.
 *
 * Rendered as a full-width banner so it reads as a deliberate status strip in the
 * Rhythm card, Practice setup, and Speed Trainer dialog rather than a stray pill.
 */
@Composable
fun MicTimingNudge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (!MicCalibration.read(context).isActive) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.gold.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = AppColors.gold,
            modifier = Modifier.size(14.dp),
        )
        Text(
            "Groove Check is on",
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "· change in Settings",
            color = AppColors.textMuted,
            fontSize = 10.sp,
        )
    }
}
