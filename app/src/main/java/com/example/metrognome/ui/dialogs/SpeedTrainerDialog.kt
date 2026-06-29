package com.example.metrognome.ui.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.speedtrainer.SpeedTrainerConfig
import com.example.metrognome.ui.components.CircleButton
import com.example.metrognome.ui.components.MicTimingNudge
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun SpeedTrainerDialog(
    config: SpeedTrainerConfig,
    timeSig: Int,
    onConfigChange: (SpeedTrainerConfig.() -> SpeedTrainerConfig) -> Unit,
    onBeginTraining: () -> Unit,
    onDismiss: () -> Unit,
    onStartMicCheck: () -> Unit = {},
    micCheckRefresh: Int = 0,
) {
    val ascending = config.ascending
    val animatedStart by animateIntAsState(config.startBpm, label = "startBpm")
    val animatedTarget by animateIntAsState(config.targetBpm, label = "targetBpm")
    val swapRotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    AppDialog(onDismiss = onDismiss, minWidth = 300.dp, maxWidth = 420.dp, scrollable = true) {

        // ── Header ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Speed Trainer",
                color = AppColors.gold,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = (-0.3).sp,
                modifier = Modifier.weight(1f),
            )
            DialogCloseButton(onClick = onDismiss)
        }

        Spacer(Modifier.height(16.dp))

        // ── Live ramp arc ─────────────────────────────────────────────────────
        RampArc(config = config, modifier = Modifier.fillMaxWidth())

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
            val stepCount = remember(config) { config.stepsSequence().size }
            Text(
                "$stepCount steps",
                color = AppColors.textMuted,
                fontSize = 10.sp,
            )
            Text(
                "${config.targetBpm} BPM",
                color = AppColors.gold,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── Config tiles — 2-column grid ──────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ConfigTile(
                label = "START",
                value = "$animatedStart",
                unit = "BPM",
                onDecrement = {
                    onConfigChange {
                        copy(startBpm = if (ascending) (startBpm - 1).coerceAtLeast(20)
                                        else           (startBpm - 1).coerceAtLeast(targetBpm + 1))
                    }
                },
                onIncrement = {
                    onConfigChange {
                        copy(startBpm = if (ascending) (startBpm + 1).coerceAtMost(targetBpm - 1)
                                        else           (startBpm + 1).coerceAtMost(300))
                    }
                },
                modifier = Modifier.weight(1f),
            )

            // Swap button — rotates 180° each press, values animate to their new positions
            Surface(
                onClick = {
                    onConfigChange { copy(startBpm = targetBpm, targetBpm = startBpm) }
                    scope.launch { swapRotation.animateTo(swapRotation.value + 180f, tween(420)) }
                },
                color = Color.Transparent,
                modifier = Modifier.size(32.dp),
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Icon(
                        Icons.Filled.SwapHoriz,
                        contentDescription = "Swap start and target",
                        tint = AppColors.textSecondary,
                        modifier = Modifier
                            .size(22.dp)
                            .graphicsLayer { rotationZ = swapRotation.value },
                    )
                }
            }

            ConfigTile(
                label = "TARGET",
                value = "$animatedTarget",
                unit = "BPM",
                onDecrement = {
                    onConfigChange {
                        copy(targetBpm = if (ascending) (targetBpm - 1).coerceAtLeast(startBpm + 1)
                                         else           (targetBpm - 1).coerceAtLeast(20))
                    }
                },
                onIncrement = {
                    onConfigChange {
                        copy(targetBpm = if (ascending) (targetBpm + 1).coerceAtMost(300)
                                         else           (targetBpm + 1).coerceAtMost(startBpm - 1))
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val stepLabel = when (config.incrementMode) {
                SpeedTrainerConfig.IncrementMode.FIXED -> "BPM"
                SpeedTrainerConfig.IncrementMode.PERCENT -> "%"
            }
            val stepVal = when (config.incrementMode) {
                SpeedTrainerConfig.IncrementMode.FIXED -> "${config.stepSize.toInt()}"
                SpeedTrainerConfig.IncrementMode.PERCENT -> "${config.stepSize}"
            }
            ConfigTile(
                label = if (ascending) "STEP UP" else "STEP DOWN",
                value = stepVal,
                unit = stepLabel,
                onDecrement = {
                    onConfigChange {
                        val delta = if (incrementMode == SpeedTrainerConfig.IncrementMode.PERCENT) 0.5f else 1f
                        copy(stepSize = (stepSize - delta).coerceAtLeast(if (incrementMode == SpeedTrainerConfig.IncrementMode.PERCENT) 0.5f else 1f))
                    }
                },
                onIncrement = {
                    onConfigChange {
                        val delta = if (incrementMode == SpeedTrainerConfig.IncrementMode.PERCENT) 0.5f else 1f
                        copy(stepSize = (stepSize + delta).coerceAtMost(50f))
                    }
                },
                modifier = Modifier.weight(1f),
            )
            ConfigTile(
                label = "EVERY",
                value = "${config.barsPerStep}",
                unit = if (config.barsPerStep == 1) "bar" else "bars",
                onDecrement = { onConfigChange { copy(barsPerStep = (barsPerStep - 1).coerceAtLeast(1)) } },
                onIncrement = { onConfigChange { copy(barsPerStep = (barsPerStep + 1).coerceAtMost(32)) } },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConfigTile(
                label = "REPEAT",
                value = "${config.repeatsPerStep}×",
                unit = if (config.repeatsPerStep == 1) "time" else "times",
                onDecrement = { onConfigChange { copy(repeatsPerStep = (repeatsPerStep - 1).coerceAtLeast(1)) } },
                onIncrement = { onConfigChange { copy(repeatsPerStep = (repeatsPerStep + 1).coerceAtMost(8)) } },
                modifier = Modifier.weight(1f),
            )
            IncrementModeToggle(
                mode = config.incrementMode,
                onToggle = {
                    onConfigChange {
                        val newMode = if (incrementMode == SpeedTrainerConfig.IncrementMode.FIXED)
                            SpeedTrainerConfig.IncrementMode.PERCENT
                        else SpeedTrainerConfig.IncrementMode.FIXED
                        val newStep = if (newMode == SpeedTrainerConfig.IncrementMode.PERCENT) 5f else 5f
                        copy(incrementMode = newMode, stepSize = newStep)
                    }
                },
                modifier = Modifier.weight(1f),
            )
        }

        // Mic mode is now a single app-wide toggle in Settings (no per-feature opt-in).

        Spacer(Modifier.height(14.dp))

        Text(
            text = "Training session will be approximately ${
                formatTrainerDuration(config.estimatedDurationSeconds(timeSig))
            }.",
            color = AppColors.textMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )

        MicTimingNudge(onStartCheck = onStartMicCheck, refreshKey = micCheckRefresh)

        Spacer(Modifier.height(16.dp))

        // ── Begin button ──────────────────────────────────────────────────────
        Surface(
            onClick = onBeginTraining,
            shape = RoundedCornerShape(14.dp),
            color = AppColors.primaryPurple,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Bolt,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        "START TRAINING",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.5.sp,
                    )
                }
            }
        }
    }
}

/** "45 seconds" under a minute, otherwise rounded whole minutes ("1 minute" / "5 minutes"). */
private fun formatTrainerDuration(seconds: Int): String = when {
    seconds < 60 -> "$seconds seconds"
    else -> {
        val minutes = (seconds / 60f).roundToInt().coerceAtLeast(1)
        "$minutes ${if (minutes == 1) "minute" else "minutes"}"
    }
}

// ── Ramp arc Canvas ───────────────────────────────────────────────────────────

@Composable
private fun RampArc(config: SpeedTrainerConfig, modifier: Modifier = Modifier) {
    val steps = remember(config) { config.stepsSequence() }
    val stepCount = steps.size
    val ascending = config.ascending

    val blue = Color(0xFF5B78C8)
    val dotBlue = Color(0xFF8AA4EC)
    Box(
        modifier = modifier
            .height(66.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val padH = 12.dp.toPx()
                val endX = w - padH
                val bottomY = h * 0.84f
                val topY = h * 0.12f

                // startY/endY flip for descending so the curve falls left→right
                val startY = if (ascending) bottomY else topY
                val endY   = if (ascending) topY    else bottomY
                val cx = endX * 0.55f

                val curve = Path().apply {
                    moveTo(padH, startY)
                    quadraticTo(cx, startY, endX, endY)
                }

                // Soft area fill under the curve — gives the ramp body instead of a lone thin line.
                val fill = Path().apply {
                    moveTo(padH, startY)
                    quadraticTo(cx, startY, endX, endY)
                    lineTo(endX, bottomY)
                    lineTo(padH, bottomY)
                    close()
                }
                drawPath(
                    path = fill,
                    brush = Brush.verticalGradient(
                        colors = listOf(AppColors.gold.copy(alpha = 0.20f), AppColors.gold.copy(alpha = 0f)),
                        startY = topY,
                        endY   = bottomY,
                    ),
                )

                // Faint baseline grounds the curve.
                drawLine(
                    color = AppColors.textDim.copy(alpha = 0.22f),
                    start = Offset(padH, bottomY),
                    end   = Offset(endX, bottomY),
                    strokeWidth = 1.dp.toPx(),
                )

                // Glow beneath the stroke, then the crisp gradient stroke on top.
                drawPath(
                    path = curve,
                    color = AppColors.gold.copy(alpha = 0.16f),
                    style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round),
                )
                drawPath(
                    path = curve,
                    brush = Brush.linearGradient(
                        colors = if (ascending) listOf(blue, AppColors.gold)
                                 else           listOf(AppColors.gold, blue),
                        start = Offset(padH, startY),
                        end   = Offset(endX, endY),
                    ),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )

                // Step markers — t=0 is the start BPM (left), t=1 the target (right). Each gets a
                // subtle riser from the baseline plus a dot that reads distinctly off the line.
                val totalRange = abs(config.targetBpm - config.startBpm).toFloat().coerceAtLeast(1f)
                val showRisers = stepCount <= 24
                steps.forEachIndexed { i, bpm ->
                    val t = (abs(bpm - config.startBpm).toFloat() / totalRange).coerceIn(0f, 1f)
                    val x = (1-t)*(1-t)*padH + 2*(1-t)*t*cx + t*t*endX
                    val y = startY + (endY - startY) * t * t
                    val isEndpoint = i == 0 || i == steps.lastIndex

                    if (showRisers && !isEndpoint) {
                        drawLine(
                            color = dotBlue.copy(alpha = 0.22f),
                            start = Offset(x, bottomY),
                            end   = Offset(x, y),
                            strokeWidth = 1.dp.toPx(),
                        )
                    }

                    if (isEndpoint) {
                        drawCircle(AppColors.gold.copy(alpha = 0.25f), radius = 9.dp.toPx(), center = Offset(x, y))
                        drawCircle(AppColors.gold, radius = 5.dp.toPx(), center = Offset(x, y))
                        drawCircle(Color.White.copy(alpha = 0.85f), radius = 1.8.dp.toPx(), center = Offset(x, y))
                    } else {
                        val r = if (stepCount <= 20) 3.5.dp.toPx() else 2.5.dp.toPx()
                        drawCircle(dotBlue, radius = r, center = Offset(x, y))
                        drawCircle(Color.White.copy(alpha = 0.9f), radius = r * 0.42f, center = Offset(x, y))
                    }
                }
            },
    )
}

// ── Config tile ───────────────────────────────────────────────────────────────

@Composable
private fun ConfigTile(
    label: String,
    value: String,
    unit: String,
    onDecrement: () -> Unit,
    onIncrement: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.height(80.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Label / value / unit — equally spaced across full height
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceEvenly,
            ) {
                Text(
                    label,
                    color = AppColors.textSubtle,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    lineHeight = 11.sp,
                )
                Text(
                    value,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 20.sp,
                )
                Text(
                    unit,
                    color = AppColors.textMuted,
                    fontSize = 9.sp,
                    lineHeight = 11.sp,
                )
            }

            // − button — left edge, vertically centred
            CircleButton(
                label = "−",
                onClick = onDecrement,
                size = 32.dp,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp),
            )

            // + button — right edge, vertically centred
            CircleButton(
                label = "+",
                onClick = onIncrement,
                size = 32.dp,
                fontSize = 16.sp,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp),
            )
        }
    }
}

// ── Increment mode toggle tile ────────────────────────────────────────────────

@Composable
private fun IncrementModeToggle(
    mode: SpeedTrainerConfig.IncrementMode,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPercent = mode == SpeedTrainerConfig.IncrementMode.PERCENT
    val activeColor by animateColorAsState(
        if (isPercent) AppColors.gold else AppColors.primaryPurple,
        animationSpec = tween(200),
        label = "modeColor",
    )
    Surface(
        onClick = onToggle,
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, activeColor.copy(alpha = 0.5f)),
        modifier = modifier.height(80.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            Text(
                "MODE",
                color = AppColors.textSubtle,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                lineHeight = 11.sp,
            )
            Text(
                if (isPercent) "%" else "BPM",
                color = activeColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp,
            )
            Text(
                if (isPercent) "Musical" else "Fixed",
                color = AppColors.textMuted,
                fontSize = 9.sp,
                lineHeight = 11.sp,
            )
        }
    }
}

// MicOptIn moved to ui/components/MicOptIn.kt — shared by Speed Trainer + Practice.
