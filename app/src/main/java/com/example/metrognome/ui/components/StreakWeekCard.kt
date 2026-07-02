package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.practice.PracticeSessionManager
import com.example.metrognome.ui.theme.AppColors
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.sin

/**
 * Collapsible streak card for MetronomeScreen.
 *
 * Collapsed: a compact 40dp pill showing streak text + animated chevron.
 * Expanded: the pill header + the full seven-day circle grid below.
 *
 * [expanded] and [onToggle] are owned by the ViewModel so state persists
 * across sessions. The circles are drawn by [StreakWeekCard], which is also
 * used stand-alone in SettingsScreen — no duplication.
 */
@Composable
fun CollapsibleStreakCard(
    streak: Int,
    bestStreak: Int,
    practicedEpochDays: Set<Long>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronAngle by animateFloatAsState(
        targetValue   = if (expanded) 180f else 0f,
        animationSpec = tween(220),
        label         = "chevron",
    )

    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(AppColors.surfaceDim)
            .border(1.dp, AppColors.primaryPurple.copy(alpha = 0.25f), shape)
            .clickable(onClick = onToggle),
    ) {
        // ── Collapsed header (always visible) ───────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StreakIcon(Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            StreakHeaderText(streak = streak, bestStreak = bestStreak, modifier = Modifier.weight(1f))
            Icon(
                imageVector        = Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse streak" else "Expand streak",
                tint               = AppColors.textSubtle,
                modifier           = Modifier
                    .size(18.dp)
                    .rotate(chevronAngle),
            )
        }

        // ── Expandable body ─────────────────────────────────────────────────
        AnimatedVisibility(
            visible = expanded,
            enter   = expandVertically() + fadeIn(tween(180)),
            exit    = shrinkVertically() + fadeOut(tween(120)),
        ) {
            StreakWeekCard(
                streak             = streak,
                bestStreak         = bestStreak,
                practicedEpochDays = practicedEpochDays,
                modifier           = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                showHeader         = false,
            )
        }
    }
}

/**
 * Full always-expanded week card. Used in SettingsScreen's PracticeStreakCard.
 *
 * Pass [showHeader] = false when embedding inside [CollapsibleStreakCard] to
 * avoid rendering the header twice.
 */
@Composable
fun StreakWeekCard(
    streak: Int,
    bestStreak: Int,
    practicedEpochDays: Set<Long>,
    modifier: Modifier = Modifier,
    showHeader: Boolean = true,
) {
    // Must use the same 2 AM-local day definition the streak data is stored with,
    // otherwise the week dots misalign in every timezone except the one where the
    // 2 AM shift happens to equal the UTC offset. Derive the weekday from the same
    // shifted instant so "today" and its column agree even between midnight and 2 AM.
    val today = PracticeSessionManager.currentEpochDay()
    val cal = Calendar.getInstance().apply {
        timeInMillis = System.currentTimeMillis() - 2L * 60 * 60 * 1000
    }
    val daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val mondayEpoch = today - daysFromMonday

    val shimmer by rememberInfiniteTransition(label = "streak_shimmer")
        .animateFloat(
            initialValue  = 0f,
            targetValue   = 360f,
            animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
            label         = "shimmerAngle",
        )

    val pulse by rememberInfiniteTransition(label = "streak_pulse")
        .animateFloat(
            initialValue  = 0.35f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )

    // Slow spin for the scalloped halo on practiced days - much slower than the shimmer,
    // a lazy drift rather than a glint, on the seal asset's shared app-wide period. One
    // transition shared by all dots; each dot adds its own phase offset so the rosettes
    // don't rotate in lockstep.
    val sealSpin by rememberInfiniteTransition(label = "streak_seal_spin")
        .animateFloat(
            initialValue  = 0f,
            targetValue   = 360f,
            animationSpec = infiniteRepeatable(tween(SEAL_DRIFT_PERIOD_MS, easing = LinearEasing)),
            label         = "sealSpinAngle",
        )

    Column(modifier = modifier) {
        if (showHeader) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StreakIcon(Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                StreakHeaderText(streak = streak, bestStreak = bestStreak)
            }
            Spacer(Modifier.height(9.dp))
        }

        // ── Seven day circles ────────────────────────────────────────────────
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            // Sparkle angles (degrees clockwise from top, all in top-right quadrant).
            // Subtle variation keeps dots recognisably the same element but not identical.
            val sparkleAngles = listOf(45f, 35f, 55f, 40f, 50f, 38f, 48f)

            listOf("M", "T", "W", "T", "F", "S", "S").forEachIndexed { i, label ->
                val epochDay    = mondayEpoch + i
                val isPracticed = epochDay in practicedEpochDays
                val isToday     = epochDay == today
                val isFuture    = epochDay > today

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    StreakDayDot(
                        isPracticed        = isPracticed,
                        isToday            = isToday,
                        isFuture           = isFuture,
                        shimmerAngle       = shimmer,
                        shimmerPhaseOffset = i * (360f / 7f),
                        sparkleAngleDeg    = sparkleAngles[i],
                        todayPulse         = pulse,
                        sealSpinAngle      = sealSpin + i * (360f / 7f),
                        modifier           = Modifier.size(26.dp),
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text       = label,
                        color      = if (isToday) AppColors.gold else AppColors.textSubtle,
                        fontSize   = 9.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}

// ── Shared header text — single source of truth ──────────────────────────────

@Composable
private fun StreakHeaderText(
    streak: Int,
    bestStreak: Int,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        when {
            streak > 0 -> {
                Text("Day $streak", color = AppColors.gold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(" streak", color = AppColors.textMuted, fontSize = 13.sp)
                if (bestStreak > streak) {
                    Text("  ·  best $bestStreak", color = AppColors.textSubtle, fontSize = 11.sp)
                }
            }
            bestStreak > 0 -> {
                Text(
                    text     = "Best: $bestStreak day${if (bestStreak != 1) "s" else ""}",
                    color    = AppColors.textMuted,
                    fontSize = 13.sp,
                )
                Text(
                    text     = "  ·  practice today to restart",
                    color    = AppColors.textSubtle,
                    fontSize = 11.sp,
                )
            }
            else -> {
                Text("Start your streak", color = AppColors.textMuted, fontSize = 13.sp)
            }
        }
    }
}

// ── Day dot Canvas ────────────────────────────────────────────────────────────

/** The seal's halo form in the streak's amber, at the flat halo circle's old alpha (0x28). */
private val STREAK_HALO_STYLE = SealStyle.halo(color = Color(0xFFF59E0B), alpha = 0.16f)

@Composable
private fun StreakDayDot(
    isPracticed: Boolean,
    isToday: Boolean,
    isFuture: Boolean,
    shimmerAngle: Float,
    todayPulse: Float,
    modifier: Modifier = Modifier,
    shimmerPhaseOffset: Float = 0f,
    sparkleAngleDeg: Float = 45f,    // degrees clockwise from top (12 o'clock)
    sealSpinAngle: Float = 0f,       // slow rotation of the scalloped halo, phase included
) {
    Canvas(modifier = modifier) {
        val r      = size.minDimension / 2f
        val center = Offset(r, r)

        when {
            isPracticed -> {
                // The halo band (disc edge r to r*1.4) carries the app-wide seal in its
                // translucent halo form, drifting slowly - an achieved day reads as a tiny
                // certification mark. Same soft alpha the flat halo circle had; only the
                // silhouette (and its lazy drift) is new.
                drawSeal(
                    style       = STREAK_HALO_STYLE,
                    center      = center,
                    radius      = r * 1.4f,
                    rotationDeg = sealSpinAngle,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
                        center = center, radius = r,
                    ),
                    radius = r,
                    center = center,
                )
                // Shimmer arc — each dot starts at a different phase so they
                // all glint independently rather than in lockstep.
                drawArc(
                    color      = Color(0x55FFFFFF),
                    startAngle = shimmerAngle + shimmerPhaseOffset,
                    sweepAngle = 60f,
                    useCenter  = false,
                    topLeft    = Offset(r * 0.18f, r * 0.18f),
                    size       = Size(r * 1.64f, r * 1.64f),
                    style      = Stroke(width = r * 0.3f, cap = StrokeCap.Round),
                )
                // 4-pointed star glint at a per-dot angle (all in top-right
                // quadrant, but each dot's star sits at a slightly different spot).
                val rad = Math.toRadians(sparkleAngleDeg.toDouble())
                val sc  = Offset(
                    center.x + r * 0.46f * sin(rad).toFloat(),
                    center.y - r * 0.46f * cos(rad).toFloat(),
                )
                val sr = r * 0.23f
                val si = sr * 0.26f
                drawPath(
                    path = Path().apply {
                        moveTo(sc.x,      sc.y - sr); lineTo(sc.x + si, sc.y - si)
                        lineTo(sc.x + sr, sc.y     ); lineTo(sc.x + si, sc.y + si)
                        lineTo(sc.x,      sc.y + sr); lineTo(sc.x - si, sc.y + si)
                        lineTo(sc.x - sr, sc.y     ); lineTo(sc.x - si, sc.y - si)
                        close()
                    },
                    color = Color.White.copy(alpha = 0.9f),
                )
            }

            isToday -> {
                drawCircle(color = Color(0x18F59E0B), radius = r, center = center)
                drawCircle(
                    color  = Color(0xFFF59E0B).copy(alpha = todayPulse),
                    radius = r - 1.dp.toPx(),
                    center = center,
                    style  = Stroke(width = 1.5f.dp.toPx()),
                )
            }

            isFuture -> {
                drawCircle(color = Color(0x08FFFFFF), radius = r, center = center)
                drawCircle(
                    color  = Color(0x12FFFFFF),
                    radius = r,
                    center = center,
                    style  = Stroke(width = 0.8f.dp.toPx()),
                )
            }

            else -> {
                drawCircle(color = Color(0x15FFFFFF), radius = r, center = center)
                drawCircle(
                    color  = Color(0x28FFFFFF),
                    radius = r,
                    center = center,
                    style  = Stroke(width = 0.8f.dp.toPx()),
                )
            }
        }
    }
}
