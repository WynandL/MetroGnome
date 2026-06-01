package com.example.metrognome.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.speedtrainer.SpeedTrainerConfig
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.StepStat
import com.example.metrognome.viewmodel.TrainerSessionState
import kotlin.math.abs

@Composable
fun SpeedTrainerResultOverlay(
    state: TrainerSessionState.Complete,
    onDismiss: () -> Unit,
) {
    val cardScale = remember { Animatable(0.15f) }
    LaunchedEffect(Unit) {
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.surfaceDeep,
            shadowElevation = 32.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 380.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = ((cardScale.value - 0.15f) / 0.85f).coerceIn(0f, 1f)
                },
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(42.dp)
                            .background(AppColors.gold.copy(alpha = 0.15f), CircleShape),
                    ) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = AppColors.gold,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            "SPEED TRAINER",
                            color = AppColors.textMuted,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp,
                            lineHeight = 11.sp,
                        )
                        Text(
                            if (state.config.micEnabled) "Timing result" else "Full range covered",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 18.sp,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Body ──────────────────────────────────────────────────────
                if (state.config.micEnabled) {
                    MicResultBody(state)
                } else {
                    NoMicResultBody(state)
                }

                Spacer(Modifier.height(20.dp))

                // ── Done ──────────────────────────────────────────────────────
                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, AppColors.gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "Done",
                            color = AppColors.gold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

// ── No-mic result ─────────────────────────────────────────────────────────────

@Composable
private fun NoMicResultBody(state: TrainerSessionState.Complete) {
    val config = state.config
    val steps = state.steps
    val totalBars = steps.size * config.totalBarsPerStep

    CompletedRampArc(steps = steps, config = config, modifier = Modifier.fillMaxWidth())

    Spacer(Modifier.height(4.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${config.startBpm} BPM",
            color = AppColors.textMutedBlue,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
        Text(
            "${config.targetBpm} BPM",
            color = AppColors.gold,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )
    }

    Spacer(Modifier.height(14.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatCard(label = "TEMPOS", value = "${steps.size}", modifier = Modifier.weight(1f))
        StatCard(label = "BARS", value = "$totalBars", modifier = Modifier.weight(1f))
        StatCard(label = "STEP UP", value = config.stepSizeLabel(), modifier = Modifier.weight(1f))
    }
}

// ── Mic result ────────────────────────────────────────────────────────────────

@Composable
private fun MicResultBody(state: TrainerSessionState.Complete) {
    val reachedBpm = state.steps.getOrNull(state.reachedStepIndex) ?: state.config.targetBpm

    Text("Locked in at", color = AppColors.textSecondary, fontSize = 12.sp)
    Spacer(Modifier.height(4.dp))
    Text(
        "$reachedBpm BPM",
        color = AppColors.gold,
        fontSize = 40.sp,
        fontWeight = FontWeight.Black,
        letterSpacing = (-1).sp,
    )

    state.previousReachedBpm?.let { prev ->
        val delta = reachedBpm - prev
        if (delta != 0) {
            Spacer(Modifier.height(4.dp))
            val (label, colour) = if (delta > 0) ("↑ +$delta BPM from last time" to AppColors.gold)
                                   else ("↓ ${abs(delta)} BPM from last time" to AppColors.textMuted)
            Text(label, color = colour, fontSize = 11.sp)
        }
    }

    Spacer(Modifier.height(16.dp))

    StepAccuracySection(
        steps = state.steps,
        reachedIndex = state.reachedStepIndex,
        stats = state.stepStats,
        modifier = Modifier.fillMaxWidth(),
    )
}

// ── Completed ramp arc ────────────────────────────────────────────────────────

@Composable
private fun CompletedRampArc(
    steps: List<Int>,
    config: SpeedTrainerConfig,
    modifier: Modifier = Modifier,
) {
    val stepCount = steps.size
    val totalRange = (config.targetBpm - config.startBpm).coerceAtLeast(1).toFloat()

    Box(
        modifier = modifier
            .height(64.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val padH = 8.dp.toPx()
                val endX = w - padH
                val bottomY = h * 0.84f
                val topY = h * 0.08f
                val ctrlX = endX * 0.55f

                val arcPath = Path().apply {
                    moveTo(padH, bottomY)
                    quadraticTo(ctrlX, bottomY, endX, topY)
                }

                // Dim frame track slightly wider so gradient sits on top
                drawPath(
                    path = arcPath,
                    color = AppColors.surfaceVariant.copy(alpha = 0.30f),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )

                // Gradient fill: deepPurple → gold matches the HUD bar
                drawPath(
                    path = arcPath,
                    brush = Brush.linearGradient(
                        colors = listOf(AppColors.deepPurple, AppColors.gold),
                        start = Offset(padH, bottomY),
                        end = Offset(endX, topY),
                    ),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )

                // Step dots — all gold, endpoints with a glow halo
                steps.forEachIndexed { i, bpm ->
                    val t = ((bpm - config.startBpm).toFloat() / totalRange).coerceIn(0f, 1f)
                    val x = (1 - t) * (1 - t) * padH + 2 * (1 - t) * t * ctrlX + t * t * endX
                    val y = (1 - t) * (1 - t) * bottomY + 2 * (1 - t) * t * bottomY + t * t * topY
                    val isEndpoint = i == 0 || i == steps.lastIndex
                    val radius = when {
                        isEndpoint -> 4.5.dp.toPx()
                        stepCount <= 16 -> 2.5.dp.toPx()
                        else -> 1.5.dp.toPx()
                    }
                    if (isEndpoint) {
                        drawCircle(
                            color = AppColors.gold.copy(alpha = 0.25f),
                            radius = radius * 2.5f,
                            center = Offset(x, y),
                        )
                    }
                    drawCircle(
                        color = if (isEndpoint) AppColors.gold else AppColors.gold.copy(alpha = 0.60f),
                        radius = radius,
                        center = Offset(x, y),
                    )
                }
            },
    )
}

// ── Stat card ─────────────────────────────────────────────────────────────────

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(10.dp),
        modifier = modifier.height(56.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                value,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                lineHeight = 18.sp,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                color = AppColors.textSubtle,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                lineHeight = 10.sp,
            )
        }
    }
}

// ── Step accuracy section (mic mode) ─────────────────────────────────────────

@Composable
private fun StepAccuracySection(
    steps: List<Int>,
    reachedIndex: Int,
    stats: List<StepStat>,
    modifier: Modifier = Modifier,
) {
    val statMap = stats.associateBy { it.bpm }
    val validStats = stats.filter { it.avgDeviationMs != null }
    val maxDev = validStats.mapNotNull { it.avgDeviationMs }.maxOrNull()?.coerceAtLeast(1f) ?: 1f
    val bestStat = validStats.minByOrNull { it.avgDeviationMs!! }
    val worstStat = validStats.maxByOrNull { it.avgDeviationMs!! }

    Column(modifier = modifier) {
        Text(
            "Timing per tempo",
            color = AppColors.textMuted,
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        // Variable-height bar chart: tall bar = tight timing, short bar = loose
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            steps.forEachIndexed { i, bpm ->
                val stat = statMap[bpm]
                val isReached = i <= reachedIndex
                val deviation = stat?.avgDeviationMs
                val heightFraction = when {
                    !isReached -> 0f
                    deviation == null -> 0.4f
                    else -> (1f - (deviation / maxDev).coerceIn(0f, 0.82f)).coerceAtLeast(0.18f)
                }
                val barColor = when {
                    !isReached -> Color.Transparent
                    deviation == null -> AppColors.primaryPurple.copy(alpha = 0.5f)
                    else -> accuracyColor(deviation)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    if (heightFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(heightFraction)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(barColor),
                        )
                    }
                }
            }
        }

        // Best and worst tempo callout — actionable: "your timing breaks down at X BPM"
        if (bestStat != null && worstStat != null) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(accuracyColor(bestStat.avgDeviationMs!!)),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "Best  ${bestStat.bpm} BPM · ±${bestStat.avgDeviationMs.toInt()}ms",
                        color = AppColors.textSubtle,
                        fontSize = 9.sp,
                    )
                }
                if (bestStat.bpm != worstStat.bpm) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${worstStat.bpm} BPM · ±${worstStat.avgDeviationMs!!.toInt()}ms",
                            color = AppColors.textSubtle,
                            fontSize = 9.sp,
                        )
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(accuracyColor(worstStat.avgDeviationMs)),
                        )
                    }
                }
            }
        }
    }
}

private fun accuracyColor(avgDeviationMs: Float): Color {
    return when {
        avgDeviationMs <= 15f -> AppColors.gold
        avgDeviationMs <= 30f -> Color(0xFF7BC47B)
        avgDeviationMs <= 60f -> Color(0xFFE0A84B)
        else -> Color(0xFFD05555)
    }
}
