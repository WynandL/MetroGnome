package com.example.metrognome.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.ItemPalette

/**
 * Shared "watch a clip for bonus Gnotes" row - the exact same nudge appears in
 * [com.example.metrognome.ui.dialogs.GnotesInfoDialog] and the Rhythm page's expanded
 * PointsCard, so it lives here once rather than as two drifting copies.
 *
 * Reworked 2026-08-17: the previous version was a plain "Metro's daily bonus" label with
 * a purple "+15" beside it - low-contrast against the dark background and phrased as a
 * statement of fact ("you got a bonus") rather than an instruction, so almost nobody
 * tapped it. Copy now opens with the action ("Watch a clip..."), and the reward lives
 * inside a solid, pulsing round badge - unmistakably a button, not a stat.
 */
@Composable
fun DailyBonusCta(
    canWatchToday: Boolean,
    adReady: Boolean,
    remainingToday: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val canTap = canWatchToday && adReady
    val earn = minOf(PointsConfig.REWARDED_GNOTES_PER_WATCH, remainingToday)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (canTap) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication        = null,
                    onClick           = onClick,
                ) else Modifier
            )
            .padding(vertical = 4.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier              = Modifier.weight(1f).padding(end = 12.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector        = Icons.Filled.PlayCircle,
                contentDescription = null,
                tint               = if (canTap) AppColors.gold else AppColors.textSubtle,
                modifier           = Modifier.size(18.dp),
            )
            Column {
                // lineHeight must be set alongside fontSize here - Material3's ambient
                // bodyLarge carries a fixed 24sp line height for its 16sp size, and
                // overriding only fontSize leaves that behind. Invisible on one line, but
                // this headline is long enough to wrap on a narrow screen or larger text
                // scale, and without this it'd open a gap exactly like the one found in
                // MicTimingNudge (see CLAUDE.md's Typography trap note).
                Text(
                    text = when {
                        canTap        -> "Watch a clip for bonus ${PointsConfig.CURRENCY_NAME}"
                        canWatchToday -> "Bonus clip loading…"
                        else          -> "Today's bonus claimed"
                    },
                    color      = if (canTap) AppColors.textPrimary else AppColors.textSubtle,
                    fontSize   = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = if (canTap) FontWeight.SemiBold else FontWeight.Normal,
                )
                Text(
                    text     = if (canWatchToday) "Free · up to 3 times a day" else "More tomorrow",
                    color    = AppColors.textMuted,
                    fontSize = 10.sp,
                    modifier = Modifier.padding(top = 1.dp),
                )
            }
        }

        DailyBonusBadge(canTap = canTap, canWatchToday = canWatchToday, earn = earn)
    }
}

@Composable
private fun DailyBonusBadge(canTap: Boolean, canWatchToday: Boolean, earn: Int) {
    val badgeSize = 42.dp
    // Room around the badge for the ping ring to expand into. A border drawn AT the
    // badge's own edge (tried first) sat right on top of the gold fill it was supposed
    // to stand out from - gold-on-gold, all but invisible regardless of alpha. Drawing a
    // ring that expands OUTWARD past the fill, into the dark background around it, is
    // what actually reads: same idea as a notification "ping", high-contrast by
    // construction since it never overlaps its own fill color.
    val haloSize = 62.dp

    // Repeating 0→1 ramp (no reverse): each cycle is one outward ping - radius grows
    // while alpha fades to zero, then resets. Kept outside the `if` below so the ring's
    // phase doesn't reset every time canTap flips (e.g. ad finishes loading mid-pulse).
    val ping by rememberInfiniteTransition(label = "daily_bonus_ping")
        .animateFloat(
            initialValue  = 0f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
            label = "dailyBonusPingProgress",
        )

    Box(
        modifier         = Modifier.size(haloSize),
        contentAlignment = Alignment.Center,
    ) {
        if (canTap) {
            Canvas(modifier = Modifier.matchParentSize()) {
                val innerRadius = badgeSize.toPx() / 2f
                val outerRadius = haloSize.toPx() / 2f
                drawCircle(
                    color  = AppColors.gold.copy(alpha = (1f - ping) * 0.65f),
                    radius = innerRadius + (outerRadius - innerRadius) * ping,
                    center = center,
                    style  = Stroke(width = 2.5.dp.toPx()),
                )
            }
        }

        if (!canWatchToday) {
            Box(
                modifier         = Modifier.size(badgeSize).background(AppColors.surfaceVariant, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = Icons.Filled.Check,
                    contentDescription = null,
                    tint               = AppColors.textSubtle,
                    modifier           = Modifier.size(18.dp),
                )
            }
        } else {
            Box(
                modifier = Modifier
                    .size(badgeSize)
                    .background(
                        brush = if (canTap)
                            Brush.radialGradient(listOf(ItemPalette.goldLight, AppColors.gold))
                        else
                            Brush.radialGradient(listOf(AppColors.surfaceVariant, AppColors.surfaceVariant)),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text       = "+$earn",
                    color      = if (canTap) AppColors.background else AppColors.textSubtle,
                    fontSize   = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }
        }
    }
}

/**
 * Confirmation shown when [DailyBonusCta] is tapped - shared for the same reason as the
 * row itself, so the copy only needs fixing in one place. Reworded 2026-08-17: "Three
 * clips per day. Come back tomorrow for more." read as curt/scolding out of context: an
 * imperative sentence fragment with no connecting warmth. Now framed as one sentence
 * about what the user gets, not a policy notice about what they can't have.
 */
@Composable
fun DailyBonusConfirmDialog(
    earn: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest  = onDismiss,
        containerColor    = AppColors.surfaceDeep,
        titleContentColor = AppColors.gold,
        textContentColor  = AppColors.textSecondary,
        title = { Text("Metro's Daily Bonus", fontWeight = FontWeight.Bold) },
        text  = {
            Text(
                text = "Watch a short clip and Metro rewards you with $earn ${PointsConfig.CURRENCY_NAME} " +
                       "right away. You can do this up to 3 times a day, resetting again tomorrow.",
                fontSize   = 13.sp,
                lineHeight = 19.sp,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Claim Reward", color = AppColors.primaryPurple, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not now", color = AppColors.textMuted)
            }
        },
    )
}
