package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.points.PointsSnapshot
import com.example.metrognome.ui.theme.AppColors

@Composable
fun GnotesInfoDialog(
    snapshot: PointsSnapshot,
    canWatchAdToday: Boolean = false,
    adReady: Boolean = false,
    remainingToday: Int = 0,
    onWatchRewardedAd: (onDone: () -> Unit) -> Unit = { it() },
    onDismiss: () -> Unit,
) {
    var showWatchAdConfirm by remember { mutableStateOf(false) }
    val canTap = canWatchAdToday && adReady

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val maxDialogHeight = (LocalConfiguration.current.screenHeightDp * 0.85f).dp
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .heightIn(max = maxDialogHeight),
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
                            text       = "You have ${snapshot.total} ${if (snapshot.total == 1) PointsConfig.CURRENCY_NAME_SINGULAR else PointsConfig.CURRENCY_NAME}",
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

                // ── Body (scrolls independently so a long breakdown never pushes
                //   the footer off-screen on a small device) ───────────────────
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp, vertical = 16.dp),
                ) {
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

                    if (snapshot.contributions.isNotEmpty()) {
                        Spacer(Modifier.height(14.dp))
                        HorizontalDivider(color = AppColors.surfaceVariant.copy(alpha = 0.6f))
                        Spacer(Modifier.height(10.dp))
                        snapshot.contributions.forEach { c ->
                            Row(
                                modifier              = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text     = "${c.label}  ·  ${c.rawValue} ${c.rawUnit}",
                                    color    = AppColors.textMuted,
                                    fontSize = 12.sp,
                                )
                                Text(
                                    text       = "+${c.points}",
                                    color      = AppColors.textSecondary,
                                    fontSize   = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }

                    // ── Daily bonus teaser ────────────────────────────────────
                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = AppColors.surfaceVariant.copy(alpha = 0.6f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (canTap) Modifier.clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication        = null,
                                    onClick           = { showWatchAdConfirm = true },
                                ) else Modifier
                            )
                            .padding(top = 10.dp),
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(
                                imageVector        = Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint               = if (canTap) AppColors.mediumPurple.copy(alpha = 0.75f) else AppColors.textSubtle,
                                modifier           = Modifier.size(12.dp),
                            )
                            Text(
                                text  = "Metro's daily bonus",
                                color = if (canTap) AppColors.textSecondary else AppColors.textSubtle,
                                fontSize = 12.sp,
                            )
                        }
                        val earn = minOf(PointsConfig.REWARDED_GNOTES_PER_WATCH, remainingToday)
                        Text(
                            text       = when {
                                canTap          -> "+$earn  →"
                                canWatchAdToday -> "+$earn"
                                else            -> "✓  Today"
                            },
                            color      = if (canTap) AppColors.mediumPurple.copy(alpha = 0.8f) else AppColors.textSubtle,
                            fontSize   = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
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

    if (showWatchAdConfirm) {
        val earn = minOf(PointsConfig.REWARDED_GNOTES_PER_WATCH, remainingToday)
        AlertDialog(
            onDismissRequest  = { showWatchAdConfirm = false },
            containerColor    = AppColors.surfaceDeep,
            titleContentColor = AppColors.gold,
            textContentColor  = AppColors.textSecondary,
            title = { Text("Metro's Daily Bonus", fontWeight = FontWeight.Bold) },
            text  = {
                Text(
                    text = "Watch a short clip and Metro rewards you with $earn ${PointsConfig.CURRENCY_NAME}. " +
                           "Three clips per day. Come back tomorrow for more.",
                    fontSize   = 13.sp,
                    lineHeight = 19.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showWatchAdConfirm = false
                    onWatchRewardedAd {}
                }) {
                    Text("Claim Reward", color = AppColors.primaryPurple, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showWatchAdConfirm = false }) {
                    Text("Not now", color = AppColors.textMuted)
                }
            },
        )
    }
}
