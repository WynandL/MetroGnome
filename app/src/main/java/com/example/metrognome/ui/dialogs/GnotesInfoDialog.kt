package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.ui.theme.AppColors

@Composable
fun GnotesInfoDialog(gnoteCount: Int, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.surfaceDeep,
            border = BorderStroke(1.dp, AppColors.surfaceVariant),
        ) {
            Column {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .background(AppColors.gold.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint               = AppColors.gold,
                            modifier           = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text       = "You have $gnoteCount ${if (gnoteCount == 1) PointsConfig.CURRENCY_NAME_SINGULAR else PointsConfig.CURRENCY_NAME}",
                            color      = AppColors.gold,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 17.sp,
                        )
                        Text(
                            text       = "Your MetroGnome practice currency",
                            color      = AppColors.textMuted,
                            fontSize   = 11.sp,
                            modifier   = Modifier.padding(top = 2.dp),
                        )
                    }
                }

                HorizontalDivider(color = AppColors.surfaceVariant)

                // ── Body ──────────────────────────────────────────────────────
                Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
                    Text(
                        text = "${PointsConfig.CURRENCY_NAME} are earned by practising: running the metronome, tuning your instrument, playing the rhythm game, and completing practice sessions.",
                        color      = AppColors.textSecondary,
                        fontSize   = 13.sp,
                        lineHeight = 19.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Each activity has a daily limit, so consistent practice always pays off, even on short sessions.",
                        color      = AppColors.textMuted,
                        fontSize   = 12.sp,
                        lineHeight = 18.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "Head to the Settings tab to see your full breakdown and all the ways to earn.",
                        color      = AppColors.textMuted,
                        fontSize   = 12.sp,
                        lineHeight = 18.sp,
                    )
                }

                // ── Footer ────────────────────────────────────────────────────
                HorizontalDivider(color = AppColors.surfaceVariant)
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text       = "Got it",
                            color      = AppColors.gold,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
