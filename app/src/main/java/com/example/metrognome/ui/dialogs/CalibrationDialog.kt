package com.example.metrognome.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.graphics.vector.ImageVector
import kotlin.math.roundToInt
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.CalibrationMode
import com.example.metrognome.viewmodel.CalibrationState

/**
 * Overlay dialog that tracks calibration progress and shows results.
 * The main TunerScreen UI stays still underneath — this handles all state
 * transitions (running, done, failed) without touching the card layout.
 *
 *
 * Not dismissible while a run is in progress; OK/Retry appear once done.
 */
@Composable
fun CalibrationDialog(
    state: CalibrationState,
    onDismiss: () -> Unit,
    onRetryLoopback: () -> Unit,
    onRetryReference: () -> Unit,
) {
    val running = state is CalibrationState.Running
    val cardScale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Dialog(
        onDismissRequest = { if (!running) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.surfaceDeep,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 360.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (state) {
                    is CalibrationState.Running -> RunningContent(state)

                    is CalibrationState.DoneLoopback -> DoneLoopbackContent(
                        state = state,
                        onDismiss = onDismiss,
                        onRetry = onRetryLoopback,
                    )

                    is CalibrationState.DoneReference -> DoneReferenceContent(
                        state = state,
                        onDismiss = onDismiss,
                        onRetry = onRetryReference,
                    )

                    is CalibrationState.Failed -> FailedContent(
                        state = state,
                        onDismiss = onDismiss,
                        onRetry = when (state.mode) {
                            CalibrationMode.REFERENCE -> onRetryReference
                            else -> onRetryLoopback
                        },
                    )

                    CalibrationState.Idle -> { /* dialog not shown in idle */ }
                }
            }
        }
    }
}

// ── Running ─────────────────────────────────────────────────────────────────

@Composable
private fun RunningContent(state: CalibrationState.Running) {
    val title = when (state.mode) {
        CalibrationMode.LOOPBACK   -> "Calibrating Microphone"
        CalibrationMode.REFERENCE  -> "Tuning Fork"
        CalibrationMode.INSTRUMENT -> "Calibrating"
    }
    val subtitle = when (state.mode) {
        CalibrationMode.LOOPBACK -> state.currentToneHz
            ?.let { "Playing ${fmtHz(it.toDouble())}" }
            ?: "Starting…"
        CalibrationMode.REFERENCE  -> "Listening for your fork…"
        CalibrationMode.INSTRUMENT -> "Applying…"
    }

    Text(
        text = title,
        color = Color.White,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )

    Spacer(Modifier.height(16.dp))

    val animatedProgress by animateFloatAsState(
        targetValue = state.progress,
        animationSpec = tween(durationMillis = 300),
        label = "calibration_progress",
    )
    LinearProgressIndicator(
        progress = { animatedProgress },
        modifier = Modifier
            .fillMaxWidth()
            .height(6.dp),
        color = AppColors.primaryPurple,
        trackColor = AppColors.primaryPurple.copy(alpha = 0.2f),
        strokeCap = StrokeCap.Round,
    )

    Spacer(Modifier.height(10.dp))

    Text(
        text = subtitle,
        color = AppColors.textSecondary,
        fontSize = 13.sp,
        textAlign = TextAlign.Center,
    )

    if (state.mode == CalibrationMode.LOOPBACK) {
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Remain quiet",
            color = AppColors.textMuted,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Done — loopback ──────────────────────────────────────────────────────────

@Composable
private fun DoneLoopbackContent(
    state: CalibrationState.DoneLoopback,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val result = state.result
    val (iconColor, bgColor, headline, detail) = if (result.confident) {
        val acc = if (result.accuracyCents.isNaN()) "<1" else "%.1f".format(result.accuracyCents)
        Quad(
            AppColors.gold,
            AppColors.gold.copy(alpha = 0.12f),
            "Verified",
            "Accuracy ±${acc}¢",
        )
    } else {
        Quad(
            AppColors.textMuted,
            AppColors.textMuted.copy(alpha = 0.1f),
            "Could not verify",
            "Turn up volume or move to a quieter room",
        )
    }

    ResultIcon(iconColor, bgColor, confirmed = result.confident)
    Spacer(Modifier.height(12.dp))
    Text(headline, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(detail, color = AppColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    ActionRow(onDismiss = onDismiss, onRetry = onRetry)
}

// ── Done — reference fork ────────────────────────────────────────────────────

@Composable
private fun DoneReferenceContent(
    state: CalibrationState.DoneReference,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    val result = state.result
    val (iconColor, bgColor, headline, detail) = if (result.confident) {
        val spread = if (result.spreadCents.isNaN()) "<1" else "%.1f".format(result.spreadCents)
        Quad(
            AppColors.gold,
            AppColors.gold.copy(alpha = 0.12f),
            "Calibration set",
            "Spread ±${spread}¢  ·  ${fmtHz(result.measuredHz.toDouble())} detected",
        )
    } else {
        Quad(
            AppColors.textMuted,
            AppColors.textMuted.copy(alpha = 0.1f),
            "Not confident",
            "Strike the fork cleanly and hold it close",
        )
    }

    ResultIcon(iconColor, bgColor, confirmed = result.confident)
    Spacer(Modifier.height(12.dp))
    Text(headline, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(detail, color = AppColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    ActionRow(onDismiss = onDismiss, onRetry = onRetry)
}

// ── Failed ───────────────────────────────────────────────────────────────────

@Composable
private fun FailedContent(
    state: CalibrationState.Failed,
    onDismiss: () -> Unit,
    onRetry: () -> Unit,
) {
    ResultIcon(AppColors.danger, AppColors.danger.copy(alpha = 0.12f), confirmed = false)
    Spacer(Modifier.height(12.dp))
    Text("Calibration failed", color = Color.White, fontSize = 18.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(state.message, color = AppColors.textSecondary, fontSize = 13.sp,
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    ActionRow(onDismiss = onDismiss, onRetry = onRetry)
}

// ── Shared components ────────────────────────────────────────────────────────

@Composable
private fun ResultIcon(tint: Color, bg: Color, confirmed: Boolean) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(52.dp)
            .background(bg, CircleShape),
    ) {
        Icon(
            imageVector = if (confirmed) Icons.Filled.Check else Icons.Filled.Warning,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun ActionRow(onDismiss: () -> Unit, onRetry: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onRetry,
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)),
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Retry", color = AppColors.textSecondary, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.width(10.dp))

        Surface(
            onClick = onDismiss,
            shape = RoundedCornerShape(14.dp),
            color = AppColors.gold.copy(alpha = 0.10f),
            border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.75f)),
            modifier = Modifier
                .weight(1f)
                .height(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("Done", color = AppColors.gold, fontSize = 14.sp,
                    fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Pre-calibration confirmation ─────────────────────────────────────────────

/**
 * Shown before either calibration method starts, so the user knows what will
 * happen and can prepare (volume up, quiet room, fork ready).
 * Dismissible at any time; confirming fires the actual calibration lambda.
 */
@Composable
fun CalibrationConfirmDialog(
    mode: CalibrationMode,
    referenceHz: Float,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val iconVector: ImageVector
    val title: String
    val body: String
    when (mode) {
        CalibrationMode.LOOPBACK -> {
            iconVector = Icons.Filled.Mic
            title = "Microphone Calibration"
            body = "Metro plays test tones through your speaker and listens back through the mic to measure and correct any pitch bias. The whole process takes about 10 seconds.\n\nTurn your phone volume up and find a quiet spot before you start, background noise will skew the result."
        }
        CalibrationMode.REFERENCE -> {
            iconVector = Icons.Filled.MusicNote
            title = "Tuning Fork"
            body = "Strike your ${referenceHz.roundToInt()} Hz fork and hold it close to the mic. Metro will lock on to the pitch and store a correction that sharpens accuracy across every note you tune.\n\nCalibration has a bigger impact than most people expect, even a small offset accumulates. A well-calibrated tuner is noticeably more accurate."
        }
        CalibrationMode.INSTRUMENT -> return
    }

    val cardScale = remember { Animatable(0.2f) }
    LaunchedEffect(Unit) {
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.surfaceDeep,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 360.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .background(AppColors.gold.copy(alpha = 0.12f), CircleShape),
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = AppColors.gold,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    title,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    body,
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(22.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Cancel",
                                color = AppColors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.gold.copy(alpha = 0.10f),
                        border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.75f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Start",
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
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun fmtHz(hz: Double): String =
    if (hz >= 1000.0) "%.2f kHz".format(hz / 1000.0) else "%.0f Hz".format(hz)

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
