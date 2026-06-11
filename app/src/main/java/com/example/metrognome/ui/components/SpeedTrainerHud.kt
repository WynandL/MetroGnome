package com.example.metrognome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.TrainerSessionState

/**
 * In-session HUD — visually matches the practice bar.
 *
 * A single gradient-filled bar (deepPurple → gold) advances from left to right as
 * steps complete. Step boundaries appear as thin tick marks on the empty track.
 * Shimmer and leading-edge glow keep it feeling alive. The overlay row shows
 * step number, retreat/skip controls, and a cancel button.
 *
 * Overall progress = (completedSteps + barProgressWithinCurrentStep) / totalSteps,
 * giving a smooth continuous fill across all steps and all bars within each step.
 */
@Composable
fun SpeedTrainerHud(
    state: TrainerSessionState.Running,
    onSkip: () -> Unit,
    onRetreat: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(10.dp)
    val totalSteps = state.steps.size.coerceAtLeast(1)
    val totalBars = state.config.totalBarsPerStep.coerceAtLeast(1)
    val currentBpm = state.steps.getOrElse(state.currentStepIndex) { state.config.targetBpm }

    // Discrete per-bar fill: the bar currently playing counts as filled, so the bar
    // reads "current bar / total" and reaches 100% while the final bar of the final
    // step plays. Updates once per bar (on downbeats), with a gentle tween between.
    val currentBar = (totalBars - state.barsRemainingThisStep + 1).coerceIn(1, totalBars)
    val barProgress = currentBar.toFloat() / totalBars
    val overallTarget = ((state.currentStepIndex + barProgress) / totalSteps).coerceIn(0f, 1f)
    val animatedProgress by animateFloatAsState(
        targetValue = overallTarget,
        animationSpec = tween(durationMillis = 300),
        label = "trainerFill",
    )

    val transition = rememberInfiniteTransition(label = "trainerShimmer")
    val shimmer by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )

    val fillStart = AppColors.deepPurple
    val fillEnd = AppColors.gold
    val edgeColor = lerp(AppColors.gold, Color.White, 0.45f)

    // No live timing numbers are shown to the player: the mic measures quietly in the
    // background and the only feedback is the Gnotes bonus at the end of the session.

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(AppColors.surfaceDim)
            .border(1.dp, AppColors.gold.copy(alpha = 0.30f), shape),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val fillW = w * animatedProgress

            if (fillW > 0f) {
                // Main gradient fill
                drawRect(
                    brush = Brush.horizontalGradient(listOf(fillStart, fillEnd), 0f, w),
                    topLeft = Offset(0f, 0f),
                    size = Size(fillW, h),
                )

                // Glassy top highlight
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.White.copy(alpha = 0.18f),
                        0.55f to Color.Transparent,
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(fillW, h),
                )

                // Bottom shade
                drawRect(
                    brush = Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.18f),
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(fillW, h),
                )

                // Shimmer band sweeping left to right
                val bandHalf = 36f
                val bandX = shimmer * (fillW + bandHalf * 2) - bandHalf
                if (bandX in -bandHalf..(fillW + bandHalf)) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            listOf(Color.Transparent, Color.White.copy(alpha = 0.11f), Color.Transparent),
                            bandX - bandHalf, bandX + bandHalf,
                        ),
                        topLeft = Offset(0f, 0f),
                        size = Size(fillW, h),
                    )
                }

                // Leading glow trailing back into the fill
                val glowR = 22f
                val glowStart = (fillW - glowR).coerceAtLeast(0f)
                drawRect(
                    brush = Brush.horizontalGradient(
                        listOf(edgeColor.copy(alpha = 0f), edgeColor.copy(alpha = 0.35f)),
                        glowStart, fillW,
                    ),
                    topLeft = Offset(glowStart, 0f),
                    size = Size(fillW - glowStart, h),
                )

                // Bright leading edge line
                if (fillW < w - 0.5f) {
                    drawLine(
                        color = edgeColor,
                        start = Offset(fillW, h * 0.05f),
                        end = Offset(fillW, h * 0.95f),
                        strokeWidth = 2f,
                    )
                }
            }

            // Step dividers as ruler ticks at top and bottom edges, clear of the text row.
            for (i in 1 until totalSteps) {
                val x = w * (i.toFloat() / totalSteps)
                val tickColor = if (x <= fillW) Color.White.copy(alpha = 0.55f)
                                else AppColors.textSecondary.copy(alpha = 0.45f)
                drawLine(color = tickColor, start = Offset(x, 0f), end = Offset(x, h * 0.28f), strokeWidth = 1.5f)
                drawLine(color = tickColor, start = Offset(x, h * 0.72f), end = Offset(x, h), strokeWidth = 1.5f)
            }
        }

        // Overlay row: icon + step label + retreat/skip + cancel
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Bolt,
                contentDescription = null,
                tint = AppColors.gold,
                modifier = Modifier.size(13.dp),
            )
            Spacer(Modifier.width(6.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "$currentBpm",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        lineHeight = 14.sp,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        "BPM",
                        color = AppColors.textSubtle,
                        fontSize = 9.sp,
                        lineHeight = 14.sp,
                    )
                }
                Text(
                    "Bar $currentBar of $totalBars",
                    color = AppColors.textDim,
                    fontSize = 8.sp,
                    lineHeight = 9.sp,
                )
            }
            Icon(
                Icons.Filled.SkipPrevious,
                contentDescription = "Go back a step",
                tint = if (state.currentStepIndex > 0) AppColors.textSecondary else AppColors.textSubtle,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onRetreat,
                    ),
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.Filled.SkipNext,
                contentDescription = "Skip to next step",
                tint = AppColors.textSecondary,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onSkip,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Filled.Close,
                contentDescription = "Cancel session",
                tint = AppColors.textMuted,
                modifier = Modifier
                    .size(16.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onCancel,
                    ),
            )
        }
    }
}

// ── Countdown HUD ─────────────────────────────────────────────────────────────

/**
 * Two-bar count-in HUD. Shows a large beat number that pops on each click
 * and a bar indicator (Bar 1 of 2 / Bar 2 of 2) so the musician knows
 * exactly how long before the session begins.
 */
@Composable
fun SpeedTrainerCountdownHud(
    state: TrainerSessionState.Countdown,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val displayBeat = if (state.currentBeat < 0) null else state.currentBeat + 1

    // Pop scale on each new beat; skip until first click lands
    val beatScale = remember { Animatable(1f) }
    LaunchedEffect(state.currentBeat, state.currentBar) {
        if (state.currentBeat >= 0) {
            beatScale.snapTo(1.35f)
            beatScale.animateTo(1f, animationSpec = tween(durationMillis = 250))
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Get ready...",
                color = AppColors.gold,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = 16.sp,
            )
            if (displayBeat != null) {
                Text(
                    "Bar ${state.currentBar} of ${state.totalBars}",
                    color = AppColors.textSubtle,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
            }
        }

        // Large beat number; blank before first click.
        // Scale via graphicsLayer so the fixed layout slot never shifts other elements.
        Text(
            displayBeat?.toString() ?: "",
            color = AppColors.gold,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black,
            lineHeight = 40.sp,
            modifier = Modifier.graphicsLayer {
                scaleX = beatScale.value
                scaleY = beatScale.value
            },
        )

        // Cancel
        Spacer(Modifier.weight(1f))
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(AppColors.surfaceVariant)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCancel,
                ),
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Cancel",
                tint = AppColors.textMuted,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}
