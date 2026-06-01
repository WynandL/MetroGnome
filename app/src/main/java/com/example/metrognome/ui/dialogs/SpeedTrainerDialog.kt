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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.dev.DevEasterEgg
import com.example.metrognome.speedtrainer.SpeedTrainerConfig
import com.example.metrognome.ui.components.CircleButton
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.ui.platform.LocalContext

@Composable
fun SpeedTrainerDialog(
    config: SpeedTrainerConfig,
    hasMicPermission: Boolean,
    isMicPermanentlyDenied: Boolean = false,
    onRequestMicPermission: () -> Unit,
    onConfigChange: (SpeedTrainerConfig.() -> SpeedTrainerConfig) -> Unit,
    onBeginTraining: () -> Unit,
    onDismiss: () -> Unit,
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

        // ── Mic opt-in (debug builds only — not ready for production) ────────
        if (DevEasterEgg.isDevModeActive(LocalContext.current)) {

        Spacer(Modifier.height(14.dp))

        MicOptIn(
            enabled = config.micEnabled,
            hasMicPermission = hasMicPermission,
            isPermanentlyDenied = isMicPermanentlyDenied,
            autoAdvanceWindowMs = config.autoAdvanceWindowMs,
            onToggle = { onConfigChange { copy(micEnabled = !micEnabled) } },
            onRequestPermission = onRequestMicPermission,
            onWindowDecrement = {
                onConfigChange { copy(autoAdvanceWindowMs = (autoAdvanceWindowMs - 5).coerceAtLeast(10)) }
            },
            onWindowIncrement = {
                onConfigChange { copy(autoAdvanceWindowMs = (autoAdvanceWindowMs + 5).coerceAtMost(100)) }
            },
        )

        } // end dev-only mic block

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
                        "BEGIN TRAINING",
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

// ── Ramp arc Canvas ───────────────────────────────────────────────────────────

@Composable
private fun RampArc(config: SpeedTrainerConfig, modifier: Modifier = Modifier) {
    val steps = remember(config) { config.stepsSequence() }
    val stepCount = steps.size
    val ascending = config.ascending

    Box(
        modifier = modifier
            .height(56.dp)
            .drawBehind {
                val w = size.width
                val h = size.height
                val padH = 12.dp.toPx()
                val endX = w - padH
                val bottomY = h * 0.82f
                val topY = h * 0.10f

                // startY/endY flip for descending so the curve falls left→right
                val startY = if (ascending) bottomY else topY
                val endY   = if (ascending) topY    else bottomY

                val path = Path().apply {
                    moveTo(padH, startY)
                    quadraticTo(endX * 0.55f, startY, endX, endY)
                }

                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = if (ascending) listOf(Color(0xFF5B78C8), AppColors.gold)
                                 else           listOf(AppColors.gold, Color(0xFF5B78C8)),
                        start = Offset(padH, startY),
                        end   = Offset(endX, endY),
                    ),
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
                )

                // Step dots — t=0 is always the start BPM (left), t=1 is target (right)
                val totalRange = abs(config.targetBpm - config.startBpm).toFloat().coerceAtLeast(1f)
                val cx = endX * 0.55f
                steps.forEachIndexed { i, bpm ->
                    val t = (abs(bpm - config.startBpm).toFloat() / totalRange).coerceIn(0f, 1f)
                    val x = (1-t)*(1-t)*padH + 2*(1-t)*t*cx + t*t*endX
                    val y = (1-t)*(1-t)*startY + 2*(1-t)*t*startY + t*t*endY
                    val isEndpoint = i == 0 || i == steps.lastIndex
                    val dotColor = if (isEndpoint) AppColors.gold else Color(0xFF6B8AE0).copy(alpha = 0.7f)
                    val radius = if (isEndpoint) 4.5.dp.toPx() else if (stepCount <= 20) 2.5.dp.toPx() else 1.5.dp.toPx()
                    drawCircle(color = dotColor, radius = radius, center = Offset(x, y))
                }
            },
    )
}

/** Demo ramp for the Speed Trainer enable dialog — fixed 60→120 BPM at +5 BPM steps. */
@Composable
internal fun SpeedTrainerRampPreview(modifier: Modifier = Modifier) {
    val demoConfig = remember { SpeedTrainerConfig(startBpm = 60, targetBpm = 120, stepSize = 5f) }
    RampArc(config = demoConfig, modifier = modifier)
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

// ── Mic opt-in section ────────────────────────────────────────────────────────

@Composable
private fun MicOptIn(
    enabled: Boolean,
    hasMicPermission: Boolean,
    isPermanentlyDenied: Boolean = false,
    autoAdvanceWindowMs: Int,
    onToggle: () -> Unit,
    onRequestPermission: () -> Unit,
    onWindowDecrement: () -> Unit,
    onWindowIncrement: () -> Unit,
) {
    val borderColor by animateColorAsState(
        if (enabled) AppColors.gold.copy(alpha = 0.4f) else AppColors.surfaceVariant,
        animationSpec = tween(300),
        label = "micBorder",
    )
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        // Outer Row spans the full card height so the icon centres against
        // everything — the text rows AND the optional auto-advance row below.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Filled.Mic,
                contentDescription = null,
                tint = AppColors.gold,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))

            // Content: text+switch row, optional auto-advance row, optional permission note
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Let Metro listen (Beta)",
                            color = if (enabled) AppColors.gold else AppColors.textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (enabled) "Metro will advance you when your timing is locked in."
                            else "Track your timing and auto-advance when you're ready.",
                            color = AppColors.textMuted,
                            fontSize = 10.sp,
                            lineHeight = 13.sp,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = enabled,
                        onCheckedChange = {
                            if (!hasMicPermission && !enabled) {
                                onRequestPermission()
                            } else {
                                onToggle()
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.gold,
                            checkedTrackColor = AppColors.gold.copy(alpha = 0.25f),
                            uncheckedThumbColor = AppColors.textDim,
                            uncheckedTrackColor = AppColors.surfaceVariant,
                        ),
                    )
                }

                if (enabled) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Auto-advance when within",
                            color = AppColors.textSecondary,
                            fontSize = 11.sp,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            CircleButton("−", onWindowDecrement, size = 22.dp, fontSize = 13.sp)
                            Text(
                                "±${autoAdvanceWindowMs}ms",
                                color = AppColors.gold,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            CircleButton("+", onWindowIncrement, size = 22.dp, fontSize = 13.sp)
                        }
                    }
                }

                if (!hasMicPermission && !enabled) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (isPermanentlyDenied)
                            "Permission blocked. Tap the switch to open App Settings."
                        else
                            "Microphone permission required",
                        color = if (isPermanentlyDenied) AppColors.gold.copy(alpha = 0.8f)
                                else AppColors.textSubtle,
                        fontSize = 9.sp,
                    )
                }
            }
        }
    }
}
