package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.dev.DevEasterEgg
import com.example.metrognome.points.EarnRule
import com.example.metrognome.points.EARN_RULES
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.ui.theme.AppColors

@Composable
fun EarnRulesDialog(onDismiss: () -> Unit) {
    // Same gate as every other dev tool: visible in a debug build OR when dev mode is unlocked.
    val context = LocalContext.current
    val isDevMode = remember { DevEasterEgg.isDevModeActive(context) }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.93f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = AppColors.surfaceDeep,
            border = BorderStroke(1.dp, AppColors.surfaceVariant),
        ) {
            Column {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(AppColors.gold.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Bolt,
                            contentDescription = null,
                            tint               = AppColors.gold,
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                    Column {
                        Text(
                            text       = "How to Earn ${PointsConfig.CURRENCY_NAME}",
                            color      = AppColors.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 16.sp,
                        )
                        Text(
                            text       = "Earn ${PointsConfig.CURRENCY_NAME} by using the app. Daily limits reward consistent practice.",
                            color      = AppColors.textMuted,
                            fontSize   = 10.sp,
                            lineHeight = 13.sp,
                            modifier   = Modifier.padding(top = 1.dp),
                        )
                    }
                }

                HorizontalDivider(color = AppColors.surfaceVariant)

                // ── Activity list ─────────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    EARN_RULES.filter { !it.hidden }.forEach { rule -> EarnRuleCard(rule) }

                    // ── Footer ───────────────────────────────────────────────
                    HorizontalDivider(color = AppColors.surfaceVariant, modifier = Modifier.padding(top = 4.dp))

                    if (isDevMode) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            Text(
                                text     = "Daily maximum total",
                                color    = AppColors.textMuted,
                                fontSize = 12.sp,
                            )
                            Text(
                                text       = "${EARN_RULES.sumOf { it.maxPerDay }} pts / day",
                                color      = AppColors.gold,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 12.sp,
                            )
                        }
                    }
                    Text(
                        text     = "All limits reset at midnight each day.",
                        color    = AppColors.textMuted,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(top = if (isDevMode) 1.dp else 8.dp, bottom = 4.dp),
                    )
                }

                // ── Close ─────────────────────────────────────────────────────
                HorizontalDivider(color = AppColors.surfaceVariant)
                Box(
                    modifier           = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment   = Alignment.CenterEnd,
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

@Composable
private fun EarnRuleCard(rule: EarnRule) {
    Surface(
        color    = AppColors.surface,
        shape    = RoundedCornerShape(12.dp),
        border   = BorderStroke(1.dp, AppColors.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(13.dp)) {

            // Icon + label + description
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(AppColors.gold.copy(alpha = 0.12f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector        = iconFor(rule.iconKey),
                        contentDescription = null,
                        tint               = AppColors.gold,
                        modifier           = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.padding(top = 1.dp)) {
                    Text(
                        text       = rule.label,
                        color      = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                    )
                    Text(
                        text       = rule.description,
                        color      = AppColors.textMuted,
                        fontSize   = 11.sp,
                        lineHeight = 15.sp,
                        modifier   = Modifier.padding(top = 2.dp),
                    )
                }
            }

            Spacer(Modifier.size(11.dp))

            // Three stat chips
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                StatChip(
                    label    = "Earns",
                    value    = "+${rule.pointsPerUnit} ${rule.ptLabel} / ${rule.rateUnit}",
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label    = "Daily limit",
                    value    = if (rule.inherentlyDaily) "Once daily"
                               else "${rule.dailyLimit} ${rule.limitUnit}",
                    modifier = Modifier.weight(1f),
                )
                StatChip(
                    label       = "Max / day",
                    value       = "${rule.maxPerDay} ${rule.maxPtLabel}",
                    highlighted = true,
                    modifier    = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun StatChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    Column(
        modifier = modifier
            .background(
                color = if (highlighted) AppColors.gold.copy(alpha = 0.10f)
                        else AppColors.surfaceDeep,
                shape = RoundedCornerShape(8.dp),
            )
            .padding(horizontal = 5.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text       = value,
            color      = if (highlighted) AppColors.gold else AppColors.textSecondary,
            fontWeight = FontWeight.Bold,
            fontSize   = 11.sp,
            lineHeight = 14.sp,
            textAlign  = TextAlign.Center,
        )
        Text(
            text          = label,
            color         = AppColors.textMuted,
            fontSize      = 9.sp,
            letterSpacing = 0.3.sp,
            textAlign     = TextAlign.Center,
            modifier      = Modifier.padding(top = 2.dp),
        )
    }
}

private fun iconFor(iconKey: String): ImageVector = when (iconKey) {
    "metronome" -> Icons.Filled.MusicNote
    "tuner"     -> Icons.Filled.GraphicEq
    "game"      -> Icons.Filled.Stars
    "practice"  -> Icons.Filled.Timer
    "speed"     -> Icons.Filled.Bolt
    "timing"    -> Icons.Filled.GraphicEq
    "feedback"  -> Icons.Filled.ThumbUp
    "loyalty"   -> Icons.Filled.EmojiEvents
    "ad"        -> Icons.Filled.PlayCircle
    else        -> Icons.Filled.MusicNote
}
