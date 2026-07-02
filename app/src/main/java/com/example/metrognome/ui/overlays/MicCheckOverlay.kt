package com.example.metrognome.ui.overlays

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.audio.selftest.CheckStatus
import com.example.metrognome.audio.selftest.MicSelfTest
import com.example.metrognome.audio.selftest.SelfTestPhase
import com.example.metrognome.audio.selftest.SelfTestThresholds
import com.example.metrognome.analytics.AnalyticsTracker
import com.example.metrognome.cloud.MicCheckReporter
import com.example.metrognome.ui.components.Seal
import com.example.metrognome.ui.components.SealStyle
import com.example.metrognome.ui.components.rememberMediaVolumeFraction
import com.example.metrognome.ui.dialogs.DialogCloseButton
import com.example.metrognome.ui.theme.AppColors

/**
 * User-facing microphone check.
 *
 * A friendly front-end over [MicSelfTest]: the device plays a few sounds, listens
 * back, and proves it can follow the player's timing. The musician does nothing
 * but stay quiet with the volume up. This deliberately hides every engineering
 * number (the detailed report lives in the dev-only `MicDiagnosticsOverlay`); here
 * the run resolves to one of three plain outcomes:
 *
 *  - **Ready** (PASS): the device saved its latency constant; mic features can be used.
 *  - **Almost there** (ABORT): something the user can fix (noise, volume) - retry.
 *  - **Good to go** (FAIL): we aren't confident enough to enable mic features here, so
 *    we leave them off. The copy stays positive and blames neither the user nor the
 *    device - the rest of the app is unaffected, and they are told they miss nothing.
 *
 * On PASS the engine itself persists the constant via SelfTestCalibrationStore, so
 * "enabling the feature" needs nothing here beyond dismissing - the Settings entry
 * re-reads calibrated state when this closes.
 */
@SuppressLint("MissingPermission")
@Composable
fun MicCheckOverlay(
    onDismiss: () -> Unit,
    /** Fired once per resolved run (any verdict). Lets the caller credit the run toward
     *  item unlocks without this overlay knowing anything about the item system. */
    onRunCompleted: () -> Unit = {},
) {
    val context = LocalContext.current
    val selfTest = remember { MicSelfTest(context) }
    val ui by selfTest.state.collectAsStateWithLifecycle()
    var permissionDenied by remember { mutableStateOf(false) }

    fun hasMic() = ContextCompat.checkSelfPermission(
        context, Manifest.permission.RECORD_AUDIO
    ) == PackageManager.PERMISSION_GRANTED

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) { permissionDenied = false; selfTest.run() }
        else permissionDenied = true
    }

    fun start() {
        permissionDenied = false
        if (hasMic()) selfTest.run() else permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    DisposableEffect(Unit) { onDispose { selfTest.cancel() } }

    val running = ui.running
    val report = ui.report
    val showingIntro = !permissionDenied && !running && report == null

    // Report each completed run's outcome anonymously so we can see pass/fail across
    // devices. Keyed on the report instance, so it fires once per run (retries included).
    // All online logic lives in the cloud package; this is just the trigger.
    LaunchedEffect(report) {
        report?.let {
            MicCheckReporter.submit(it)
            AnalyticsTracker.logMicCheckCompleted(it.verdict.name, it.grade.name)
            onRunCompleted()
        }
    }

    // Gate Start on media volume via the shared loopback volume gate - a loopback needs the
    // speaker up, so we nudge rather than fail the run after the fact. Only polls on the intro.
    val volumeFraction = rememberMediaVolumeFraction(active = showingIntro)

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
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Explicit close, same as the premium dialogs — a tap-outside isn't obvious.
                Row(modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.weight(1f))
                    DialogCloseButton(onClick = onDismiss)
                }

                when {
                    permissionDenied -> FixableContent(
                        message = "Metro needs microphone access to run this check. " +
                            "Turn it on for Metro and try again.",
                        onRetry = { start() },
                        onCancel = onDismiss,
                    )

                    running -> RunningContent(ui.phase, ui.statusLine)

                    report == null -> IntroContent(volumeFraction = volumeFraction, onStart = { start() })

                    report.verdict == CheckStatus.PASS -> PassContent(onDone = onDismiss)

                    report.verdict == CheckStatus.FAIL -> IncapableContent(
                        onDismiss = onDismiss,
                        onRetry = { start() },
                    )

                    else -> FixableContent(   // ABORT / PENDING - user-fixable, coach and retry
                        message = report.notes.firstOrNull()
                            ?: "The check was interrupted. Find a quiet spot, turn the volume up, and try again.",
                        onRetry = { start() },
                        onCancel = onDismiss,
                    )
                }
            }
        }
    }
}

// ── Intro ─────────────────────────────────────────────────────────────────────

@Composable
private fun IntroContent(volumeFraction: Float, onStart: () -> Unit) {
    val volumeOk = volumeFraction >= SelfTestThresholds.MIN_VOLUME_FRACTION
    PulsingNote(AppColors.gold)
    Spacer(Modifier.height(16.dp))
    Text("Microphone check", color = Color.White, fontSize = 18.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(8.dp))
    Text(
        "This lets the Speed Trainer, Practice, and Rhythm Game use your microphone to " +
            "measure how well you keep time. Metro plays a few quick sounds and listens back " +
            "to check this phone can do it accurately. Find a quiet spot and turn the volume up.",
        color = AppColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(10.dp))
    Text(
        "It won't change your tuner's accuracy or performance.",
        color = AppColors.textMuted, fontSize = 12.sp, lineHeight = 16.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(16.dp))
    if (!volumeOk) {
        Text(
            "Turn your volume up to start.",
            color = AppColors.gold, fontSize = 12.sp, lineHeight = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
    }
    PrimaryButton("Start", onStart, Modifier.fillMaxWidth(), enabled = volumeOk)
}

// ── Running ───────────────────────────────────────────────────────────────────

@Composable
private fun RunningContent(phase: SelfTestPhase, status: String) {
    PulsingNote(AppColors.gold)
    Spacer(Modifier.height(16.dp))
    Text("Listening…", color = Color.White, fontSize = 18.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(16.dp))

    val animated by animateFloatAsState(
        targetValue = phaseProgress(phase),
        animationSpec = tween(durationMillis = 450),
        label = "mic_check_progress",
    )
    LinearProgressIndicator(
        progress = { animated },
        modifier = Modifier.fillMaxWidth().height(6.dp),
        color = AppColors.primaryPurple,
        trackColor = AppColors.primaryPurple.copy(alpha = 0.2f),
        strokeCap = StrokeCap.Round,
    )
    Spacer(Modifier.height(10.dp))
    Text(status, color = AppColors.textMuted, fontSize = 12.sp, textAlign = TextAlign.Center)
}

private fun phaseProgress(phase: SelfTestPhase): Float = when (phase) {
    SelfTestPhase.IDLE -> 0f
    SelfTestPhase.ENVIRONMENT -> 0.12f
    SelfTestPhase.SPEAKER_PATH -> 0.40f
    SelfTestPhase.DISCRIMINATION -> 0.68f
    SelfTestPhase.SCORING -> 0.90f
    SelfTestPhase.DONE -> 1f
}

// ── Results ─────────────────────────────────────────────────────────────────────

@Composable
private fun PassContent(onDone: () -> Unit) {
    // The pass gets the seal's full entrance - the one moment in the app where the
    // word is literal: the device has just been certified to follow the player's timing.
    Seal(modifier = Modifier.size(56.dp), style = SealStyle.Emblem, entrance = true)
    Spacer(Modifier.height(12.dp))
    Text("You're all set", color = Color.White, fontSize = 18.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
        "Metro can follow your timing on this phone. Mic features are ready to use.",
        color = AppColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(22.dp))
    PrimaryButton("Done", onDone, Modifier.fillMaxWidth())
}

@Composable
private fun FixableContent(message: String, onRetry: () -> Unit, onCancel: () -> Unit) {
    ResultIcon(AppColors.gold, AppColors.gold.copy(alpha = 0.12f), Icons.Filled.Warning)
    Spacer(Modifier.height(12.dp))
    Text("Almost there", color = Color.White, fontSize = 18.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(message, color = AppColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp,
        textAlign = TextAlign.Center)
    Spacer(Modifier.height(22.dp))
    TwoButtonRow(
        secondaryLabel = "Not now", onSecondary = onCancel,
        primaryLabel = "Try again", onPrimary = onRetry,
    )
}

@Composable
private fun IncapableContent(onDismiss: () -> Unit, onRetry: () -> Unit) {
    ResultIcon(AppColors.gold, AppColors.gold.copy(alpha = 0.12f), Icons.Filled.MusicNote)
    Spacer(Modifier.height(12.dp))
    Text("You're good to go", color = Color.White, fontSize = 18.sp,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(Modifier.height(6.dp))
    Text(
        "We'll leave the microphone features off for now, just to keep your experience " +
            "feeling its best. Everything else in Metro is right here and ready, so you're " +
            "not missing a thing. This one may be a great fit another time, so feel free to " +
            "check again whenever you like. For now, enjoy the app and keep making music!",
        color = AppColors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp,
        textAlign = TextAlign.Center,
    )
    Spacer(Modifier.height(22.dp))
    TwoButtonRow(
        secondaryLabel = "Try again", onSecondary = onRetry,
        primaryLabel = "Keep playing", onPrimary = onDismiss,
    )
}

// ── Shared pieces ───────────────────────────────────────────────────────────────

/** The pulsing music note from the sound preview - three soft rings radiating out. */
@Composable
private fun PulsingNote(tint: Color) {
    val pulse by rememberInfiniteTransition(label = "micPulse").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2000, easing = LinearEasing)),
        label = "micPulseT",
    )
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(120.dp)) {
            val ringCount = 3
            for (i in 0 until ringCount) {
                val phase = (pulse + i.toFloat() / ringCount) % 1f
                drawCircle(
                    color = tint.copy(alpha = (1f - phase) * 0.5f),
                    radius = size.minDimension * (0.16f + phase * 0.34f),
                    center = center,
                    style = Stroke(width = 2.2f),
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(52.dp).background(tint.copy(alpha = 0.14f), CircleShape),
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = tint,
                modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ResultIcon(tint: Color, bg: Color, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(52.dp).background(bg, CircleShape),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(26.dp))
    }
}

@Composable
private fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tint = if (enabled) AppColors.gold else AppColors.textMuted
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, tint.copy(alpha = if (enabled) 0.75f else 0.4f)),
        modifier = modifier.height(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = tint, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Tooling-only: the pass card as the user sees it, for exercising the VerifiedSeal
 * animation via Start Interactive Preview (an emulator cannot reach PASS - the
 * loopback needs a real speaker/mic path).
 */
@Preview
@Composable
private fun PassContentPreview() {
    Surface(shape = RoundedCornerShape(24.dp), color = AppColors.surfaceDeep) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PassContent(onDone = {})
        }
    }
}

@Composable
private fun TwoButtonRow(
    secondaryLabel: String, onSecondary: () -> Unit,
    primaryLabel: String, onPrimary: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = onSecondary,
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
            border = BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)),
            modifier = Modifier.weight(1f).height(44.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(secondaryLabel, color = AppColors.textSecondary, fontSize = 14.sp,
                    fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.width(10.dp))
        PrimaryButton(primaryLabel, onPrimary, Modifier.weight(1f))
    }
}
