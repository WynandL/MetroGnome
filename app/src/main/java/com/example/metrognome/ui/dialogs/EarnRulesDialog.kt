package com.example.metrognome.ui.dialogs

import com.example.metrognome.ui.theme.AppRadius
import com.example.metrognome.ui.components.AppGlyph
import com.example.metrognome.ui.components.AppIcons
import com.example.metrognome.ui.components.AppIcon
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.example.metrognome.ui.components.AppCardDefaults
import com.example.metrognome.ui.components.PrimaryButton
import com.example.metrognome.ui.components.AppInset
import com.example.metrognome.ui.components.AppDivider
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
            shape = AppCardDefaults.DialogShape,
            color = AppColors.surfaceDeep,
            border = AppCardDefaults.Border,
        ) {
            Column {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    AppIcon(
                            glyph        = AppIcons.Bolt,
                            contentDescription = null,
                            tint               = AppColors.gold,
                            modifier           = Modifier.size(24.dp),
                        )
                    Column {
                        DialogTitle("How to Earn ${PointsConfig.CURRENCY_NAME}", textAlign = TextAlign.Start)
                        Text(
                            text       = "Earn ${PointsConfig.CURRENCY_NAME} by using the app. Daily limits reward consistent practice.",
                            color      = AppColors.textMuted,
                            fontSize   = 10.sp,
                            lineHeight = 13.sp,
                            modifier   = Modifier.padding(top = 1.dp),
                        )
                    }
                }

                AppDivider()

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
                    AppDivider(modifier = Modifier.padding(top = 4.dp))

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
                                lineHeight = 16.sp,
                            )
                            Text(
                                text       = "${EARN_RULES.sumOf { it.maxPerDay }} pts / day",
                                color      = AppColors.gold,
                                fontWeight = FontWeight.Bold,
                                fontSize   = 12.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }
                    Text(
                        text     = "All limits reset at midnight each day.",
                        color    = AppColors.textMuted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(top = if (isDevMode) 1.dp else 8.dp, bottom = 4.dp),
                    )
                }

                // ── Close ─────────────────────────────────────────────────────
                AppDivider()
                Box(
                    modifier           = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment   = Alignment.CenterEnd,
                ) {
                    PrimaryButton("Got it", onDismiss, Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
private fun EarnRuleCard(rule: EarnRule) {
    AppInset(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(13.dp)) {

            // Icon + label + description
            Row(verticalAlignment = Alignment.Top) {
                AppIcon(
                        glyph        = iconFor(rule.iconKey),
                        contentDescription = null,
                        tint               = AppColors.gold,
                        modifier           = Modifier.size(24.dp),
                    )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.padding(top = 1.dp)) {
                    Text(
                        text       = rule.label,
                        color      = AppColors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize   = 13.sp,
                        lineHeight = 18.sp,
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
                color = if (highlighted) AppColors.goldTint
                        else AppColors.surfaceDeep,
                shape = AppRadius.Small,
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
            lineHeight    = 12.sp,
            letterSpacing = 0.3.sp,
            textAlign     = TextAlign.Center,
            modifier      = Modifier.padding(top = 2.dp),
        )
    }
}

private fun iconFor(iconKey: String): AppGlyph = when (iconKey) {
    "metronome" -> AppIcons.MusicNote
    "tuner"     -> AppIcons.Equalizer
    "drone"     -> AppIcons.Waves
    "chords"    -> AppIcons.Piano
    "game"      -> AppIcons.Pulse
    "practice"  -> AppIcons.Timer
    "speed"     -> AppIcons.Bolt
    "timing"    -> AppIcons.Equalizer
    "feedback"  -> AppIcons.ThumbUp
    "loyalty"   -> AppIcons.Trophy
    "ad"        -> AppIcons.PlayCircle
    else        -> AppIcons.MusicNote
}
