package com.example.metrognome.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

// ── Milestone definitions ─────────────────────────────────────────────────────

/**
 * A single loyalty milestone.
 *
 * Gnotes banner fires at exactly the moment the path node lights up.
 */
data class LoyaltyMilestone(
    val days: Int,
    val name: String,
    val icon: ImageVector,
)

/**
 * Ordered milestone path. Keep in ascending days order — the progress
 * line drawing logic assumes this. Icon and name live here so nothing is
 * scattered across the app.
 */
val LOYALTY_MILESTONES: List<LoyaltyMilestone> = listOf(
    LoyaltyMilestone(days = 7,   name = "Student",  icon = Icons.Filled.MusicNote),
    LoyaltyMilestone(days = 30,  name = "Player",   icon = Icons.Filled.Favorite),
    LoyaltyMilestone(days = 60,  name = "Musician", icon = Icons.Filled.LocalFireDepartment),
    LoyaltyMilestone(days = 100, name = "Maestro",  icon = Icons.Filled.EmojiEvents),
    LoyaltyMilestone(days = 365, name = "Legend",   icon = Icons.Filled.WorkspacePremium),
)

// ── Main composable ───────────────────────────────────────────────────────────

private val NODE_SIZE: Dp = 34.dp

/**
 * Horizontal milestone path showing loyalty progress.
 *
 * Five nodes (7 → 30 → 60 → 100 → 365 days) connected by a line that fills
 * gold as the user passes each milestone. The next unreached node pulses to
 * signal what the user is working toward.
 *
 * All milestone data (icon, name, thresholds) lives in [LOYALTY_MILESTONES].
 * [currentDays] comes from UsageDayTracker.distinctDaysCount.
 */
@Composable
fun LoyaltyMilestonePath(
    currentDays: Int,
    modifier: Modifier = Modifier,
) {
    val pulse by rememberInfiniteTransition(label = "loyalty_pulse")
        .animateFloat(
            initialValue  = 0.3f,
            targetValue   = 1f,
            animationSpec = infiniteRepeatable(
                tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulseAlpha",
        )

    val n = LOYALTY_MILESTONES.size
    val nextIdx = LOYALTY_MILESTONES.indexOfFirst { currentDays < it.days }
        .let { if (it == -1) n else it }  // n = all achieved

    Column(modifier = modifier) {

        // ── Connecting line (drawn behind the nodes) + nodes ─────────────────
        Box(modifier = Modifier.fillMaxWidth()) {

            // Line drawn as a background layer on the Row so it sits behind nodes
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(NODE_SIZE)
                    .drawBehind {
                        val nodeR = NODE_SIZE.toPx() / 2f
                        val gap   = 4.dp.toPx()   // clearance between line tip and circle edge
                        val segW  = size.width / n
                        val lineY = size.height / 2f
                        val lineH = 2.dp.toPx()
                        val dim   = Color(0x25FFFFFF)
                        val gold  = AppColors.gold

                        // Draw one segment between each adjacent pair of circles.
                        // Each segment starts after the left circle and stops before the right
                        // circle, so neither line passes through or behind any circle.
                        for (i in 0 until n - 1) {
                            val segStartX = segW * (i + 0.5f) + nodeR + gap
                            val segEndX   = segW * (i + 1.5f) - nodeR - gap
                            if (segStartX >= segEndX) continue

                            val leftAchieved  = currentDays >= LOYALTY_MILESTONES[i].days
                            val rightAchieved = currentDays >= LOYALTY_MILESTONES[i + 1].days

                            when {
                                rightAchieved -> {
                                    // Full segment is gold
                                    drawLine(gold, Offset(segStartX, lineY), Offset(segEndX, lineY), lineH, cap = StrokeCap.Round)
                                }
                                leftAchieved -> {
                                    // Partial gold up to interpolated position, dim remainder
                                    val prev = LOYALTY_MILESTONES[i].days
                                    val next = LOYALTY_MILESTONES[i + 1].days
                                    val frac = ((currentDays - prev).toFloat() / (next - prev)).coerceIn(0f, 1f)
                                    val splitX = segStartX + (segEndX - segStartX) * frac
                                    if (splitX > segStartX)
                                        drawLine(gold, Offset(segStartX, lineY), Offset(splitX, lineY), lineH, cap = StrokeCap.Round)
                                    if (splitX < segEndX)
                                        drawLine(dim,  Offset(splitX,    lineY), Offset(segEndX, lineY), lineH, cap = StrokeCap.Round)
                                }
                                else -> {
                                    // Full segment is dim
                                    drawLine(dim, Offset(segStartX, lineY), Offset(segEndX, lineY), lineH, cap = StrokeCap.Round)
                                }
                            }
                        }
                    },
            ) {
                LOYALTY_MILESTONES.forEachIndexed { i, milestone ->
                    val achieved = currentDays >= milestone.days
                    val isNext   = i == nextIdx

                    Box(
                        modifier         = Modifier
                            .weight(1f)
                            .height(NODE_SIZE),
                        contentAlignment = Alignment.Center,
                    ) {
                        MilestoneNode(
                            milestone = milestone,
                            achieved  = achieved,
                            isNext    = isNext,
                            pulse     = pulse,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(5.dp))

        // ── Labels row — aligned to match node centers ────────────────────────
        Row(modifier = Modifier.fillMaxWidth()) {
            LOYALTY_MILESTONES.forEach { milestone ->
                val achieved = currentDays >= milestone.days
                Column(
                    modifier              = Modifier.weight(1f),
                    horizontalAlignment   = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text      = "${milestone.days}d",
                        color     = if (achieved) AppColors.gold else AppColors.textSubtle,
                        fontSize  = 8.sp,
                        fontWeight = if (achieved) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text      = milestone.name,
                        color     = if (achieved) AppColors.textSecondary else AppColors.textSubtle.copy(alpha = 0.5f),
                        fontSize  = 8.sp,
                        textAlign = TextAlign.Center,
                        maxLines  = 1,
                        overflow  = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── Single milestone node ─────────────────────────────────────────────────────

@Composable
private fun MilestoneNode(
    milestone: LoyaltyMilestone,
    achieved: Boolean,
    isNext: Boolean,
    pulse: Float,
) {
    when {
        achieved -> {
            // Filled gold circle + icon
            Box(
                modifier = Modifier
                    .size(NODE_SIZE)
                    .background(
                        brush  = Brush.radialGradient(
                            listOf(Color(0xFFFDE68A), Color(0xFFF59E0B)),
                        ),
                        shape  = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = milestone.icon,
                    contentDescription = milestone.name,
                    tint               = Color(0xFF92400E),  // amber-dark, legible on gold
                    modifier           = Modifier.size(16.dp),
                )
            }
        }

        isNext -> {
            // Pulsing amber ring — signals "you're working toward this"
            Box(
                modifier = Modifier
                    .size(NODE_SIZE)
                    .background(Color(0x10F59E0B), CircleShape)
                    .border(1.5.dp, Color(0xFFF59E0B).copy(alpha = pulse), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = milestone.icon,
                    contentDescription = milestone.name,
                    tint               = Color(0xFFF59E0B).copy(alpha = 0.55f),
                    modifier           = Modifier.size(16.dp),
                )
            }
        }

        else -> {
            // Future — dim
            Box(
                modifier = Modifier
                    .size(NODE_SIZE)
                    .background(Color(0x10FFFFFF), CircleShape)
                    .border(1.dp, Color(0x18FFFFFF), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector        = milestone.icon,
                    contentDescription = milestone.name,
                    tint               = Color(0x30FFFFFF),
                    modifier           = Modifier.size(16.dp),
                )
            }
        }
    }
}
