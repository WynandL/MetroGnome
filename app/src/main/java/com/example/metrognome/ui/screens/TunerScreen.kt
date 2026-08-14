package com.example.metrognome.ui.screens

import android.Manifest
import android.content.Context
import androidx.core.content.edit
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.audio.tuner.AmbientLevel
import com.example.metrognome.audio.tuner.AmbientReport
import com.example.metrognome.audio.tuner.AmbientTuning
import com.example.metrognome.audio.tuner.ListeningState
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.tuner.Tuner
import com.example.metrognome.ui.components.CircleButton
import com.example.metrognome.ui.components.GoldPill
import com.example.metrognome.ui.components.LabelValueBadge
import com.example.metrognome.ui.components.PrimaryButton
import com.example.metrognome.ui.components.TunerFeedbackCard
import com.example.metrognome.ui.dialogs.CalibrationConfirmDialog
import com.example.metrognome.ui.dialogs.CalibrationDialog
import com.example.metrognome.ui.dialogs.ConfirmDestructiveDialog
import com.example.metrognome.ui.dialogs.InstrumentCalibrationDialog
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import com.example.metrognome.viewmodel.CalibrationInfo
import com.example.metrognome.viewmodel.CalibrationMode
import com.example.metrognome.viewmodel.CalibrationState
import com.example.metrognome.viewmodel.TunerViewModel
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/** Degrees of needle sweep mapped to ±50 cents of detuning. */
private const val GAUGE_SWEEP_DEG = 62f

/** Visual half-width (cents) of the green "in tune" zone painted on the dial. */
private const val IN_TUNE_BAND_CENTS = 6f

/**
 * Height of the gauge canvas — sized to just contain the arc and pivot circle.
 * The arc's topmost point sits ~17 dp from the top; the pivot circle base is at
 * GAUGE_HEIGHT * GAUGE_PIVOT_FRACTION + 8 dp.  No text lives inside this canvas.
 */
private val GAUGE_HEIGHT = 190.dp

/**
 * Pivot fraction chosen so the arc radius matches the old design while leaving
 * only a small margin below the pivot circle inside the canvas.
 * radius = min(screenInner * 0.44, pivot_y - 14 dp) ≈ 139 dp.
 */
private const val GAUGE_PIVOT_FRACTION = 0.82f

// ── Stateful entry point ─────────────────────────────────────────────────────────

/**
 * Instrument tuner screen.
 *
 * Owns the side effects: starts/stops the [Tuner] with the composition
 * lifecycle, requests the microphone permission, and surfaces the
 * feedback card overlay.  All rendering lives in the stateless
 * [TunerScreenContent], which the UI tests drive directly.
 */
@Composable
fun TunerScreen(
    vm: TunerViewModel,
    keepScreenOn: Boolean = false,
    onSetKeepScreenOn: (Boolean) -> Unit = {},
    isAdFree: Boolean = false,
) {
    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    var micPermanentlyDenied by remember { mutableStateOf(false) }

    val activity = LocalActivity.current
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (!granted && activity != null) {
            micPermanentlyDenied = !androidx.core.app.ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(micGranted) {
        if (micGranted) vm.startListening()
        onDispose { vm.stopListening() }
    }

    val reading by vm.reading.collectAsStateWithLifecycle()
    val amplitude by vm.amplitude.collectAsStateWithLifecycle()
    val ambient by vm.ambient.collectAsStateWithLifecycle()
    val referenceHz by vm.referenceHz.collectAsStateWithLifecycle()
    val calibration by vm.calibration.collectAsStateWithLifecycle()
    val calibrationInfo by vm.calibrationInfo.collectAsStateWithLifecycle()
    val feedbackPrompt by vm.feedbackPrompt.collectAsStateWithLifecycle()
    val ambientLevel by vm.ambientLevel.collectAsStateWithLifecycle()

    val prefs = remember { context.getSharedPreferences("metrognome_prefs", Context.MODE_PRIVATE) }
    var nudgeDismissed by remember { mutableStateOf(prefs.getBoolean("tuner_calibration_nudge_shown", false)) }

    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TunerScreenContent(
            reading = reading,
            amplitude = amplitude,
            ambient = ambient,
            referenceHz = referenceHz,
            calibration = calibration,
            calibrationInfo = calibrationInfo,
            micGranted = micGranted,
            micPermanentlyDenied = micPermanentlyDenied,
            keepScreenOn = keepScreenOn,
            onRequestMic = {
                if (micPermanentlyDenied) {
                    context.startActivity(
                        android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = android.net.Uri.fromParts("package", context.packageName, null)
                        }
                    )
                } else {
                    micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onNudgeReference = vm::nudgeReference,
            onSetReferenceHz = vm::setReferenceHz,
            onLoopbackCalibrate = vm::calibrate,
            onReferenceCalibrate = vm::calibrateFromReference,
            onDismissCalibration = vm::dismissCalibration,
            onClearCalibration = vm::clearCalibration,
            onCalibrateToNote = vm::calibrateToNote,
            onToggleScreenOn = { enabling ->
                onSetKeepScreenOn(enabling)
                val msg = if (enabling) "Screen will stay on" else "Screen timeout on"
                Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
            },
            isAdFree = isAdFree,
            showCalibrationNudge = micGranted && !nudgeDismissed && !calibrationInfo.calibrated,
            onDismissCalibrationNudge = {
                nudgeDismissed = true
                prefs.edit { putBoolean("tuner_calibration_nudge_shown", true) }
            },
            ambientLevel = ambientLevel,
            onSetAmbientLevel = vm::setAmbientLevel,
        )

        TunerFeedbackCard(
            visible = feedbackPrompt != null,
            snapshot = feedbackPrompt,
            onThumbsUp = { vm.submitFeedback(it, thumbsUp = true) },
            onThumbsDown = { snap, reason -> vm.submitFeedback(snap, thumbsUp = false, reason = reason) },
            onDismiss = vm::dismissFeedback,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

// ── Stateless content ────────────────────────────────────────────────────────────

/**
 * The full tuner UI as a pure function of its inputs — no ViewModel, no side
 * effects. Kept `internal` so the instrumented UI tests can drive every state.
 */
@Composable
internal fun TunerScreenContent(
    reading: Tuner.Reading?,
    amplitude: Float,
    ambient: AmbientReport,
    referenceHz: Float,
    calibration: CalibrationState,
    calibrationInfo: CalibrationInfo,
    micGranted: Boolean,
    micPermanentlyDenied: Boolean = false,
    keepScreenOn: Boolean = false,
    onRequestMic: () -> Unit,
    onNudgeReference: (Float) -> Unit,
    onSetReferenceHz: (Float) -> Unit,
    onLoopbackCalibrate: () -> Unit,
    onReferenceCalibrate: () -> Unit,
    onDismissCalibration: () -> Unit,
    onClearCalibration: () -> Unit,
    onCalibrateToNote: (Tuner.Reading) -> Unit = {},
    onToggleScreenOn: (Boolean) -> Unit = {},
    isAdFree: Boolean = false,
    showCalibrationNudge: Boolean = false,
    onDismissCalibrationNudge: () -> Unit = {},
    ambientLevel: AmbientTuning.Level = AmbientTuning.Level.MAX,
    onSetAmbientLevel: (AmbientTuning.Level) -> Unit = {},
) {
    val pendingReading = remember { mutableStateOf<Tuner.Reading?>(null) }
    var pendingConfirm by remember { mutableStateOf<CalibrationMode?>(null) }
    var showClearCalibrationDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        Spacer(Modifier.height(16.dp))
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            if (referenceHz != 440f) {
                ReferencePitchPill(
                    referenceHz = referenceHz,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (keepScreenOn) AppColors.darkPurple else Color.Transparent)
                    .border(1.dp, if (keepScreenOn) AppColors.gold else Color(0x33FFFFFF),
                        RoundedCornerShape(10.dp))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onToggleScreenOn(!keepScreenOn) },
            ) {
                Icon(
                    imageVector = if (keepScreenOn) Icons.Filled.LightMode else Icons.Filled.ModeNight,
                    contentDescription = "Keep screen on",
                    tint = if (keepScreenOn) AppColors.gold else Color(0x80FFFFFF),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        if (!micGranted) {
            MicPermissionPrompt(onRequestMic, isPermanentlyDenied = micPermanentlyDenied)
            Spacer(Modifier.height(10.dp))
        }

        AnimatedVisibility(
            visible = showCalibrationNudge,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut(),
        ) {
            Column {
                CalibrationNudgeBanner(onDismiss = onDismissCalibrationNudge)
                Spacer(Modifier.height(8.dp))
            }
        }

        TunerGauge(reading = reading)
        GaugeReadout(reading = reading, amplitude = amplitude)

        if (reading != null && calibration == CalibrationState.Idle) {
            Spacer(Modifier.height(8.dp))
            InstrumentCalibrationChip(reading = reading, onClick = { pendingReading.value = reading })
        }

        Spacer(Modifier.height(10.dp))
        AmbientPanel(ambient, ambientLevel, onSetAmbientLevel, micGranted = micGranted)

        Spacer(Modifier.height(12.dp))
        CalibrationCard(
            info = calibrationInfo,
            referenceHz = referenceHz,
            onLoopbackCalibrate = { pendingConfirm = CalibrationMode.LOOPBACK },
            onReferenceCalibrate = { pendingConfirm = CalibrationMode.REFERENCE },
            onClear = { showClearCalibrationDialog = true },
        )

        Spacer(Modifier.height(12.dp))
        ReferencePitchCard(
            referenceHz = referenceHz,
            onNudge = onNudgeReference,
            onSet = onSetReferenceHz,
        )
        Spacer(Modifier.height(24.dp))
    }

    if (!isAdFree) {
        AdBannerView(modifier = Modifier.fillMaxWidth())
    }

    if (calibration != CalibrationState.Idle) {
        CalibrationDialog(
            state = calibration,
            onDismiss = onDismissCalibration,
            onRetryLoopback = onLoopbackCalibrate,
            onRetryReference = onReferenceCalibrate,
        )
    }

    if (showClearCalibrationDialog) {
        ConfirmDestructiveDialog(
            title        = "Clear calibration?",
            body         = "Your saved calibration will be removed and the tuner will return to default accuracy.",
            dismissLabel = "Keep it",
            confirmLabel = "Clear",
            onConfirm = {
                showClearCalibrationDialog = false
                onClearCalibration()
            },
            onDismiss = { showClearCalibrationDialog = false },
        )
    }

    pendingReading.value?.let { r ->
        InstrumentCalibrationDialog(
            reading = r,
            onConfirm = { onCalibrateToNote(r); pendingReading.value = null },
            onDismiss = { pendingReading.value = null },
        )
    }

    pendingConfirm?.let { mode ->
        CalibrationConfirmDialog(
            mode = mode,
            referenceHz = referenceHz,
            onConfirm = {
                pendingConfirm = null
                when (mode) {
                    CalibrationMode.LOOPBACK -> onLoopbackCalibrate()
                    CalibrationMode.REFERENCE -> onReferenceCalibrate()
                    CalibrationMode.INSTRUMENT -> {}
                }
            },
            onDismiss = { pendingConfirm = null },
        )
    }
    }
}

// ── Calibration nudge ────────────────────────────────────────────────────────────

/**
 * One-time dismissible banner shown on the first Tuner tab visit when the device
 * has not yet been calibrated. Guides new users to the CalibrationCard below.
 * Dismissed state is persisted in SharedPreferences so it never reappears.
 */
@Composable
private fun CalibrationNudgeBanner(onDismiss: () -> Unit) {
    Surface(
        color = AppColors.gold.copy(alpha = 0.08f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Info,
                contentDescription = null,
                tint = AppColors.gold,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "For best accuracy on this device, scroll down and tap Microphone Calibration.",
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Got it",
                color = AppColors.gold,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onDismiss() },
            )
        }
    }
}

// ── Gauge ───────────────────────────────────────────────────────────────────────

/**
 * Canvas-only gauge: arc track, in-tune band, cent ticks, needle, pivot circle.
 * No text lives inside this composable — all readout text is in [GaugeReadout]
 * below, so the arc never overlaps the note name or cent offset.
 */
@Composable
private fun TunerGauge(reading: Tuner.Reading?) {
    val targetCents = (reading?.cents ?: 0f).coerceIn(-55f, 55f)
    val needleCents by animateFloatAsState(targetCents, tween(150), label = "needle")

    val accent = gaugeAccent(reading)

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(GAUGE_HEIGHT),
    ) {
        drawGauge(needleCents, accent, hasSignal = reading != null)
    }
}

/**
 * Everything below the gauge canvas: note name, octave, cents, tune direction,
 * frequency readout chips, and the mic level meter — in one tightly-spaced column.
 *
 * Layout is stable regardless of reading state: empty strings / transparent text
 * hold vertical space so nothing shifts as the tuner locks on.
 *
 * The "DETECTED" value is the tuner's best estimate of the true sounded pitch: the raw mic
 * reading after any stored calibration correction. The needle and SITS AT compare against
 * the ideal note frequency, so a green/in-tune result means in tune in the real world.
 */
@Composable
private fun GaugeReadout(reading: Tuner.Reading?, amplitude: Float) {
    val inTune = reading?.inTune == true
    val accent = gaugeAccent(reading)

    val centsText = reading?.let {
        val c = it.cents.roundToInt()
        if (c > 0) "+$c¢" else "$c¢"
    } ?: ""

    val statusText = when {
        reading == null -> ""
        inTune -> "IN TUNE"
        reading.cents < 0f -> "▲  TUNE UP"
        else -> "TUNE DOWN  ▼"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Note name + octave
        Row(verticalAlignment = Alignment.Top) {
            Text(
                reading?.noteName ?: "-",
                color = accent, fontSize = 52.sp, fontWeight = FontWeight.Black,
            )
            if (reading != null) {
                Text(
                    reading.octave.toString(),
                    color = accent.copy(alpha = 0.7f),
                    fontSize = 20.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, start = 2.dp),
                )
            }
        }

        // Cents offset + tune direction on one line; invisible placeholder when no signal.
        val infoLine = when {
            reading == null -> " "
            inTune -> statusText
            else -> "$centsText   $statusText"
        }
        Text(
            infoLine,
            color = if (reading == null) Color.Transparent else accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
        )

        // Frequency chips — DETECTED → D2 SITS AT — only when a note is detected.
        if (reading != null) {
            Spacer(Modifier.height(10.dp))
            val targetHz = reading.frequency / 2.0.pow(reading.cents / 1200.0)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LabelValueBadge("DETECTED", String.format(Locale.US, "%.1f Hz", reading.frequency))
                Text("→", color = AppColors.textDim, fontSize = 14.sp)
                LabelValueBadge(
                    "${reading.noteName}${reading.octave} SITS AT",
                    String.format(Locale.US, "%.1f Hz", targetHz),
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        LevelMeter(amplitude)
    }
}

private fun gaugeAccent(reading: Tuner.Reading?): Color = when {
    reading == null -> AppColors.textDim
    reading.inTune -> GameColors.good
    abs(reading.cents) < 15f -> AppColors.gold
    else -> GameColors.miss
}

/** Paint the dial: track arc, in-tune band, cent ticks and the needle. */
private fun DrawScope.drawGauge(cents: Float, accent: Color, hasSignal: Boolean) {
    val pivot = Offset(size.width / 2f, size.height * GAUGE_PIVOT_FRACTION)
    val radius = minOf(size.width * 0.44f, pivot.y - 14.dp.toPx())
    val track = 5.dp.toPx()
    val arcBox = Offset(pivot.x - radius, pivot.y - radius)
    val arcSize = Size(radius * 2, radius * 2)

    drawArc(
        color = AppColors.surfaceVariant,
        startAngle = 270f - GAUGE_SWEEP_DEG,
        sweepAngle = GAUGE_SWEEP_DEG * 2,
        useCenter = false,
        topLeft = arcBox, size = arcSize,
        style = Stroke(width = track, cap = StrokeCap.Round),
    )

    val bandHalf = GAUGE_SWEEP_DEG * (IN_TUNE_BAND_CENTS / 50f)
    drawArc(
        color = GameColors.good.copy(alpha = 0.55f),
        startAngle = 270f - bandHalf,
        sweepAngle = bandHalf * 2,
        useCenter = false,
        topLeft = arcBox, size = arcSize,
        style = Stroke(width = track, cap = StrokeCap.Round),
    )

    for (c in -50..50 step 10) {
        val rad = Math.toRadians((270f + (c / 50f) * GAUGE_SWEEP_DEG).toDouble())
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val isEnd = c == -50 || c == 50
        val isCentre = c == 0
        val len = when {
            isCentre -> 22.dp.toPx()
            isEnd -> 16.dp.toPx()
            else -> 10.dp.toPx()
        }
        val outerR = radius + track
        drawLine(
            color = if (isCentre) GameColors.good else AppColors.textDim,
            start = Offset(pivot.x + cosA * (outerR - len), pivot.y + sinA * (outerR - len)),
            end = Offset(pivot.x + cosA * outerR, pivot.y + sinA * outerR),
            strokeWidth = if (isCentre) 3.dp.toPx() else 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    val needleRad = Math.toRadians((270f + (cents / 50f) * GAUGE_SWEEP_DEG).toDouble())
    val tip = Offset(
        pivot.x + cos(needleRad).toFloat() * radius * 0.86f,
        pivot.y + sin(needleRad).toFloat() * radius * 0.86f,
    )
    val needleColor = if (hasSignal) accent else AppColors.textDim
    if (hasSignal) drawCircle(accent.copy(alpha = 0.16f), radius * 0.30f, tip)
    drawLine(needleColor, pivot, tip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(needleColor, 8.dp.toPx(), pivot)
    drawCircle(AppColors.background, 3.5.dp.toPx(), pivot)
}

@Composable
private fun LevelMeter(amplitude: Float) {
    val fill = (amplitude * 6f).coerceIn(0f, 1f)
    Box(
        modifier = Modifier
            .width(120.dp)
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppColors.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill)
                .fillMaxHeight()
                .background(AppColors.textAccent),
        )
    }
}

// ── Ambient environment panel ─────────────────────────────────────────────────────

/**
 * Collapsible environment panel below the gauge.
 *
 * Collapsed (default): a row of state icons, the candidate note, and the frequency
 * rail — all calm enough to glance at while playing.
 *
 * Tap to expand: a held plain-language status, the noise floor, signal steadiness,
 * and the room-hum filter. Interesting for curious musicians, invisible otherwise.
 */
@Composable
private fun AmbientPanel(
    report: AmbientReport,
    ambientLevel: AmbientTuning.Level = AmbientTuning.Level.MAX,
    onSetAmbientLevel: (AmbientTuning.Level) -> Unit = {},
    micGranted: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    val chevronDeg by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    // Calm narration: the engine's frame-state flips several times a second in a
    // noisy room. Hold the last *stable* reading for the header and detail panel so
    // it can actually be read — LOCKED commits instantly, other states after a short
    // dwell. The live needle and frequency rail still read the raw [report].
    var shown by remember { mutableStateOf(report) }
    LaunchedEffect(report.state) {
        if (report.state == ListeningState.LOCKED) shown = report
        else { delay(900.milliseconds); shown = report }
    }

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 8.dp)) {

            // ── Header: pulsing orb + state label + note name + expand chevron ───
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { expanded = !expanded },
            ) {
                AmbientStateIcons(state = shown.state)
                Spacer(Modifier.weight(1f))
                // Fixed-width slot — always reserves 36 dp so the chevron never shifts
                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterEnd) {
                    val cand = shown.candidateHz
                    val noteText = if (cand != null && shown.state != ListeningState.PROFILING)
                        NoteNames.label(cand) else ""
                    Text(
                        noteText,
                        color = if (shown.locked) GameColors.good else AppColors.gold,
                        fontSize = 14.sp, fontWeight = FontWeight.Black,
                    )
                }
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = AppColors.textDim,
                    modifier = Modifier.size(18.dp).rotate(chevronDeg),
                )
            }

            Spacer(Modifier.height(10.dp))

            // ── Frequency rail ────────────────────────────────────────────────────
            // Slim pitch ruler. The old vertical axis (room-noise energy per band)
            // was frozen after profiling and carried no tuning value, so it is gone;
            // only the live marker position — where the pitch sits — remains.
            FrequencyRail(
                candidateHz = report.candidateHz,
                locked = report.locked,
                profiling = report.state == ListeningState.PROFILING,
            )

            // ── Ambient suppression — always visible, even when collapsed ─────────
            // No divider here: a 1dp rule directly under the rail's labels reads as
            // a floating x-axis. The caps section header + whitespace separate it.
            Spacer(Modifier.height(18.dp))
            // Heading + live caption on the left; tap-to-cycle level control on the
            // right of the same row, so the control adds no extra row and never
            // leaves the right side empty.
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "AMBIENT SUPPRESSION",
                        color = AppColors.textDim,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(3.dp))
                    Crossfade(
                        targetState = ambientLevel,
                        animationSpec = tween(200),
                        label = "suppressionCaption",
                    ) { level ->
                        Text(
                            suppressionCaption(level),
                            color = AppColors.textMuted,
                            fontSize = 11.sp,
                            lineHeight = 14.sp,
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                SuppressionLevelToggle(
                    level = ambientLevel,
                    onCycle = { onSetAmbientLevel(ambientLevel.next()) },
                )
            }

            // ── Expanded environment detail ───────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    // Whitespace + caps header separate this revealed detail, matching
                    // the divider-free treatment of the suppression section above.
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "ENVIRONMENT",
                        color = AppColors.textDim,
                        fontSize = 10.sp, fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(10.dp))

                    // Held status — what the tuner is seeing and what to do, in plain
                    // words. Fed only by the debounced [shown], so it sits still long
                    // enough to read; it crossfades when the words actually change.
                    // Without mic permission the engine never leaves its idle report
                    // ("Getting ready…", implying it's about to start) - that reads as
                    // stuck rather than blocked. Swap in a plain informational line
                    // instead; the fix action itself lives in the banner above, not here.
                    val statusText = if (!micGranted)
                        "Microphone access needed" to "Grant access above to see live room analysis."
                    else
                        shown.headline to shown.guidance
                    Crossfade(
                        targetState = statusText,
                        animationSpec = tween(250),
                        label = "ambientStatus",
                    ) { (headline, guidance) ->
                        Column {
                            Text(
                                headline,
                                color = AppColors.textSecondary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                guidance,
                                color = AppColors.textMuted,
                                fontSize = 11.sp,
                                lineHeight = 15.sp,
                                minLines = 2,
                                maxLines = 2,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    // All three rows share a fixed meter column (same width and left
                    // edge) and a fixed value column (text left-aligned), so the
                    // indicators and words line up and never shift as readings change.
                    // The old dots became a 3-segment meter to match the bar footprint.
                    val dotColor = noiseColor(shown.ambientLevel)
                    AmbientDetailRow("Noise floor") {
                        DetailSegmentMeter(filled = noiseDots(shown.ambientLevel), segments = 3, color = dotColor)
                        Spacer(Modifier.width(8.dp))
                        DetailValue(noiseLabel(shown.ambientLevel), dotColor)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Steadiness — how still the detected pitch is held. A fuller, greener
                    // bar means a steadier read; "--" until there is a tone to judge.
                    val hasSteady = !shown.stabilityCents.isNaN() && shown.stabilityCents >= 0f &&
                            shown.state in listOf(
                                ListeningState.ACQUIRING,
                                ListeningState.LOCKED,
                                ListeningState.UNSTABLE,
                            )
                    val cents = shown.stabilityCents
                    val steadyFrac = if (hasSteady) (1f - cents / 50f).coerceIn(0f, 1f) else 0f
                    val (steadyWord, steadyColor) = when {
                        !hasSteady  -> "--" to AppColors.textDim
                        cents < 5f  -> "Steady" to GameColors.good
                        cents < 18f -> "Wavering" to AppColors.gold
                        else        -> "Jumpy" to AppColors.warning
                    }
                    AmbientDetailRow("Steadiness") {
                        DetailBarMeter(frac = steadyFrac, color = steadyColor)
                        Spacer(Modifier.width(8.dp))
                        DetailValue(steadyWord, steadyColor)
                    }

                    Spacer(Modifier.height(8.dp))

                    // Room hum — the steady tone (mains buzz, a fan) the engine learned
                    // during profiling and is actively filtering out. Quiet proof of work.
                    val humActive = shown.humHz > 0f
                    AmbientDetailRow("Room hum") {
                        DetailBarMeter(
                            frac = if (humActive) 1f else 0f,
                            color = if (humActive) GameColors.good else AppColors.surfaceVariant,
                        )
                        Spacer(Modifier.width(8.dp))
                        DetailValue(
                            if (humActive) "${shown.humHz.roundToInt()} Hz" else "None",
                            if (humActive) GameColors.good else AppColors.textDim,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

private val ambientStateIconEntries = listOf(
    ListeningState.PROFILING to Icons.Filled.Search,
    ListeningState.QUIET     to Icons.Filled.Hearing,
    ListeningState.NOISE     to Icons.Filled.GraphicEq,
    ListeningState.UNSTABLE  to Icons.Filled.RecordVoiceOver,
    ListeningState.ACQUIRING to Icons.Filled.MusicNote,
    ListeningState.LOCKED    to Icons.Filled.Lock,
)

/**
 * Always-visible row of six state indicator icons.
 * The active state lights up in its own [ambientStateColor]; all others are dimmed.
 * Avoids fast-changing text that is hard to read in a noisy rehearsal environment.
 */
@Composable
private fun AmbientStateIcons(state: ListeningState) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ambientStateIconEntries.forEach { (s, icon) ->
            val isActive = state == s
            val activeColor = ambientStateColor(s)
            val tint by animateColorAsState(
                targetValue = if (isActive) activeColor else AppColors.textDim.copy(alpha = 0.28f),
                animationSpec = tween(220),
                label = "ambientIconTint",
            )
            val bgAlpha by animateFloatAsState(
                targetValue = if (isActive) 0.15f else 0f,
                animationSpec = tween(220),
                label = "ambientIconBg",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(26.dp)
                    .background(activeColor.copy(alpha = bgAlpha), CircleShape),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/**
 * Slim pitch rail that replaces the old room-noise histogram.
 *
 * The vertical axis used to show learned background-noise energy per band — but
 * that froze once profiling finished and told a musician nothing about whether a
 * note is in tune. What remains is the only thing they act on: *where* the
 * detected pitch sits along the frequency axis.
 *
 * Octave anchors (A2..A6) are drawn at their true log-frequency positions so the
 * labels line up with the marker (the old evenly-spaced labels did not). The
 * marker is a glowing thumb — gold normally, green on lock — echoing the
 * reference-pitch slider. While profiling, a soft blue bloom sweeps the rail to
 * show the room is being measured.
 */
@Composable
private fun FrequencyRail(
    candidateHz: Float?,
    locked: Boolean,
    profiling: Boolean,
) {
    val anchors = listOf(110f to "A2", 220f to "A3", 440f to "A4", 880f to "A5", 1760f to "A6")
    val lo = ln(AmbientReport.DISPLAY_FREQ_LO.toDouble())
    val hi = ln(AmbientReport.DISPLAY_FREQ_HI.toDouble())
    fun frac(hz: Float) = (((ln(hz.toDouble()) - lo) / (hi - lo)).coerceIn(0.0, 1.0)).toFloat()

    val markerColor = if (locked) GameColors.good else AppColors.gold

    // Marker glow blooms on each new candidate, then settles — same spring DNA as
    // the reference-pitch thumb.
    val haloAlpha = remember { Animatable(0.18f) }
    LaunchedEffect(candidateHz, locked) {
        if (candidateHz != null) {
            haloAlpha.snapTo(0.5f)
            haloAlpha.animateTo(0.18f, spring(dampingRatio = 0.7f, stiffness = 80f))
        }
    }

    // Profiling sweep travels left → right; -1f when idle so it is not drawn.
    val sweepX = if (profiling) {
        val transition = rememberInfiniteTransition(label = "railSweep")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
            label = "railSweepX",
        ).value
    } else -1f

    Column(modifier = Modifier.fillMaxWidth()) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp),
        ) {
            val cy = size.height / 2f

            drawLine(
                color = AppColors.surfaceVariant,
                start = Offset(0f, cy),
                end = Offset(size.width, cy),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Octave ticks; A4 (concert pitch) emphasised in gold.
            anchors.forEach { (hz, _) ->
                val x = frac(hz) * size.width
                val isConcert = hz == 440f
                drawLine(
                    color = if (isConcert) AppColors.gold.copy(alpha = 0.55f)
                            else AppColors.textDim.copy(alpha = 0.5f),
                    start = Offset(x, cy - 5.dp.toPx()),
                    end = Offset(x, cy + 5.dp.toPx()),
                    strokeWidth = if (isConcert) 2.dp.toPx() else 1.5.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }

            // Profiling sweep bloom.
            if (sweepX >= 0f) {
                val sx = sweepX * size.width
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(GameColors.rangeBlue.copy(alpha = 0.30f), Color.Transparent),
                        center = Offset(sx, cy),
                        radius = 16.dp.toPx(),
                    ),
                    radius = 16.dp.toPx(),
                    center = Offset(sx, cy),
                )
            }

            // Detected / locked pitch marker — halo + solid thumb.
            if (candidateHz != null) {
                val mx = frac(candidateHz) * size.width
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(markerColor.copy(alpha = haloAlpha.value), Color.Transparent),
                        center = Offset(mx, cy),
                        radius = 13.dp.toPx(),
                    ),
                    radius = 13.dp.toPx(),
                    center = Offset(mx, cy),
                )
                drawCircle(markerColor, 6.dp.toPx(), Offset(mx, cy))
            }
        }

        // Octave labels pinned under their true tick positions.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val railWidth = maxWidth
            anchors.forEach { (hz, label) ->
                Text(
                    label,
                    color = if (hz == 440f) AppColors.gold.copy(alpha = 0.6f) else AppColors.textDim,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.offset(x = railWidth * frac(hz) - 6.dp),
                )
            }
        }
    }
}

private fun ambientStateColor(state: ListeningState): Color = when (state) {
    ListeningState.LOCKED                          -> GameColors.good
    ListeningState.ACQUIRING                       -> AppColors.gold
    ListeningState.UNSTABLE, ListeningState.NOISE  -> AppColors.warning
    ListeningState.QUIET, ListeningState.PROFILING -> GameColors.rangeBlue
}

private fun noiseColor(level: AmbientLevel): Color = when (level) {
    AmbientLevel.QUIET    -> GameColors.good
    AmbientLevel.MODERATE -> AppColors.gold
    AmbientLevel.NOISY    -> AppColors.warning
}

private fun noiseLabel(level: AmbientLevel): String = when (level) {
    AmbientLevel.QUIET    -> "quiet"
    AmbientLevel.MODERATE -> "moderate"
    AmbientLevel.NOISY    -> "noisy"
}

private fun noiseDots(level: AmbientLevel): Int = when (level) {
    AmbientLevel.QUIET    -> 1
    AmbientLevel.MODERATE -> 2
    AmbientLevel.NOISY    -> 3
}

/** Label + right-aligned indicator in a single row. */
@Composable
private fun AmbientDetailRow(label: String, indicator: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.textSubtle, fontSize = 10.sp)
        Spacer(Modifier.weight(1f))
        indicator()
    }
}

// Shared geometry for the environment detail rows so every meter and value column
// lines up to the same edges, keeping the panel static regardless of the readings.
private val DETAIL_METER_WIDTH = 72.dp
private val DETAIL_METER_HEIGHT = 5.dp
private val DETAIL_VALUE_WIDTH = 64.dp

/** Continuous fill meter (steadiness, hum). */
@Composable
private fun DetailBarMeter(frac: Float, color: Color) {
    Box(
        modifier = Modifier
            .width(DETAIL_METER_WIDTH)
            .height(DETAIL_METER_HEIGHT)
            .clip(RoundedCornerShape(2.5.dp))
            .background(AppColors.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(frac.coerceIn(0f, 1f))
                .fillMaxHeight()
                .background(color),
        )
    }
}

/** Discrete variant of the same footprint — [filled] of [segments] lit (noise floor). */
@Composable
private fun DetailSegmentMeter(filled: Int, segments: Int, color: Color) {
    Row(
        modifier = Modifier
            .width(DETAIL_METER_WIDTH)
            .height(DETAIL_METER_HEIGHT),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(segments) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.5.dp))
                    .background(if (i < filled) color else AppColors.surfaceVariant),
            )
        }
    }
}

/** Value text in the shared fixed-width column, left-aligned so all rows line up.
 *  Pinned to one line so an unexpectedly long value can never wrap and shift the row. */
@Composable
private fun DetailValue(text: String, color: Color) {
    Box(modifier = Modifier.width(DETAIL_VALUE_WIDTH)) {
        Text(text, color = color, fontSize = 10.sp, maxLines = 1)
    }
}

// ── Ambient suppression level toggle ───────────────────────────────────────────────

/** One-line description of what each suppression level does, kept honest: even
 *  STANDARD is not "off" (the proven layers are always on). */
private fun suppressionCaption(level: AmbientTuning.Level): String = when (level) {
    AmbientTuning.Level.STANDARD -> "Proven noise handling"
    AmbientTuning.Level.ENHANCED -> "Stronger lock in noisy rooms"
    AmbientTuning.Level.MAX      -> "Maximum noise fighting"
}

/**
 * Compact tap-to-cycle control for [AmbientTuning.Level], docked on the
 * suppression heading row. Three ascending bars light up gold to the current
 * level (1 = Standard, 2 = Enhanced, 3 = Max); each tap advances one step and
 * wraps Max → Standard. The rounded, bordered surface reads as a control; the
 * rising bar heights read as an increasing-strength scale.
 */
@Composable
private fun SuppressionLevelToggle(
    level: AmbientTuning.Level,
    onCycle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onCycle,
        color = AppColors.surfaceVariant,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = modifier.semantics { contentDescription = "Ambient suppression, ${level.label}" },
    ) {
        val litCount = level.ordinal + 1
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
        ) {
            listOf(8.dp, 12.dp, 16.dp).forEachIndexed { i, barHeight ->
                val barColor by animateColorAsState(
                    targetValue = if (i < litCount) AppColors.gold
                                  else AppColors.textDim.copy(alpha = 0.35f),
                    animationSpec = tween(220),
                    label = "suppressionBar$i",
                )
                Box(
                    Modifier
                        .width(4.dp)
                        .height(barHeight)
                        .clip(RoundedCornerShape(2.dp))
                        .background(barColor),
                )
            }
        }
    }
}

// ── Reference pitch ──────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReferencePitchCard(
    referenceHz: Float,
    onNudge: (Float) -> Unit,
    onSet: (Float) -> Unit,
) {
    val isStandard = referenceHz.roundToInt() == 440
    val fraction = ((referenceHz - TunerViewModel.MIN_REFERENCE) /
                    (TunerViewModel.MAX_REFERENCE - TunerViewModel.MIN_REFERENCE)).coerceIn(0f, 1f)
    val standardFraction = (440f - TunerViewModel.MIN_REFERENCE) /
                           (TunerViewModel.MAX_REFERENCE - TunerViewModel.MIN_REFERENCE)

    // Halo brightens instantly on any value change, springs back to resting glow when idle
    val haloAlpha = remember { Animatable(0.15f) }
    LaunchedEffect(referenceHz) {
        haloAlpha.snapTo(0.45f)
        haloAlpha.animateTo(
            targetValue = 0.15f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 80f),
        )
    }

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {

            // Label + current value on one row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "REFERENCE PITCH", color = AppColors.textSubtle,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "A4 = ${referenceHz.roundToInt()} Hz",
                    color = if (isStandard) AppColors.textSecondary else AppColors.gold,
                    fontSize = 14.sp, fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Slider row
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−") { onNudge(-1f) }
                Spacer(Modifier.width(10.dp))
                Slider(
                    value = referenceHz,
                    onValueChange = onSet,
                    valueRange = TunerViewModel.MIN_REFERENCE..TunerViewModel.MAX_REFERENCE,
                    steps = (TunerViewModel.MAX_REFERENCE - TunerViewModel.MIN_REFERENCE).toInt() - 1,
                    modifier = Modifier.weight(1f),
                    thumb = {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(24.dp),
                        ) {
                            Box(Modifier.size(24.dp).background(AppColors.gold.copy(alpha = haloAlpha.value), CircleShape))
                            Box(Modifier.size(12.dp).background(AppColors.gold, CircleShape))
                        }
                    },
                    track = { _ ->
                        Canvas(modifier = Modifier.fillMaxWidth().height(14.dp)) {
                            val trackH = 2.dp.toPx()
                            val cy = center.y
                            val thumbX = size.width * fraction

                            // Inactive rail — full width underneath
                            drawLine(
                                color = AppColors.surfaceVariant,
                                start = Offset(0f, cy),
                                end = Offset(size.width, cy),
                                strokeWidth = trackH,
                                cap = StrokeCap.Round,
                            )

                            // Active portion — gradient from dim at origin to bright at thumb
                            if (fraction > 0f) {
                                drawRoundRect(
                                    brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            AppColors.gold.copy(alpha = 0.25f),
                                            AppColors.gold.copy(alpha = 0.85f),
                                        ),
                                        startX = 0f,
                                        endX = thumbX,
                                    ),
                                    topLeft = Offset(0f, cy - trackH / 2),
                                    size = Size(thumbX, trackH),
                                    cornerRadius = CornerRadius(trackH / 2),
                                )

                                // Radial glow bloom where track meets the thumb
                                drawCircle(
                                    brush = Brush.radialGradient(
                                        colors = listOf(
                                            AppColors.gold.copy(alpha = 0.35f),
                                            Color.Transparent,
                                        ),
                                        center = Offset(thumbX, cy),
                                        radius = 12.dp.toPx(),
                                    ),
                                    radius = 12.dp.toPx(),
                                    center = Offset(thumbX, cy),
                                )
                            }

                            // 440 Hz reference tick — glows gold when at standard pitch
                            val markerX = size.width * standardFraction
                            drawLine(
                                color = if (isStandard) AppColors.gold.copy(alpha = 0.6f)
                                        else AppColors.textDim.copy(alpha = 0.4f),
                                start = Offset(markerX, cy - 5.dp.toPx()),
                                end = Offset(markerX, cy + 5.dp.toPx()),
                                strokeWidth = 1.5.dp.toPx(),
                                cap = StrokeCap.Round,
                            )
                        }
                    },
                )
                Spacer(Modifier.width(10.dp))
                StepButton("+") { onNudge(1f) }
            }

            // Range labels below the slider
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 38.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "${TunerViewModel.MIN_REFERENCE.toInt()}",
                    color = AppColors.textDim, fontSize = 9.sp,
                )
                Text(
                    "440",
                    color = if (isStandard) AppColors.gold.copy(alpha = 0.65f) else AppColors.textDim,
                    fontSize = 9.sp,
                    fontWeight = if (isStandard) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    "${TunerViewModel.MAX_REFERENCE.toInt()}",
                    color = AppColors.textDim, fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) = CircleButton(label, onClick)

// ── Instrument calibration chip ─────────────────────────────────────────────────

/**
 * Pill-shaped prompt that appears below the gauge when a note is detected and no
 * calibration is running. Tapping it opens [InstrumentCalibrationDialog].
 */
@Composable
private fun InstrumentCalibrationChip(reading: Tuner.Reading, onClick: () -> Unit) {
    GoldPill(
        text = "My ${reading.noteName}${reading.octave} is in tune",
        leadingIcon = Icons.Filled.MusicNote,
        onClick = onClick,
    )
}

// ── Calibration ──────────────────────────────────────────────────────────────────

@Composable
private fun CalibrationCard(
    info: CalibrationInfo,
    referenceHz: Float,
    onLoopbackCalibrate: () -> Unit,
    onReferenceCalibrate: () -> Unit,
    onClear: () -> Unit,
) {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "CALIBRATION", color = AppColors.gold, fontSize = 12.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(14.dp))
            IdleCalibration(info, referenceHz, onLoopbackCalibrate, onReferenceCalibrate, onClear)
        }
    }
}

@Composable
private fun IdleCalibration(
    info: CalibrationInfo,
    referenceHz: Float,
    onLoopbackCalibrate: () -> Unit,
    onReferenceCalibrate: () -> Unit,
    onClear: () -> Unit,
) {
    CalibrationStatus(info)

    Spacer(Modifier.height(12.dp))
    CalibrationOptionButton(
        icon = {
            Icon(
                Icons.Filled.Mic, contentDescription = null,
                tint = AppColors.gold, modifier = Modifier.size(24.dp),
            )
        },
        title = "Microphone Calibration",
        instruction = "Auto calibration · quiet room · increase phone volume",
        onClick = onLoopbackCalibrate,
    )
    if (info.mode == CalibrationMode.REFERENCE || info.mode == CalibrationMode.INSTRUMENT) {
        val warnText = if (info.mode == CalibrationMode.REFERENCE)
            "Will replace your tuning fork calibration"
        else
            "Will replace your instrument calibration"
        Spacer(Modifier.height(5.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp),
        ) {
            Icon(Icons.Filled.Warning, null, tint = AppColors.warning,
                modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(5.dp))
            Text(warnText, color = AppColors.warning, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(10.dp))
    CalibrationOptionButton(
        icon = {
            TuningForkIcon(modifier = Modifier.size(24.dp), tint = AppColors.gold)
        },
        title = "Tuning Fork",
        instruction = "Strike A${referenceHz.roundToInt()} Hz fork · hold near mic",
        onClick = onReferenceCalibrate,
    )

    if (info.calibrated) {
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onClear,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textMuted),
        ) { Text("Clear Calibration", fontSize = 13.sp) }
    }
}

@Composable
private fun CalibrationOptionButton(
    icon: @Composable () -> Unit,
    title: String,
    instruction: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        color = AppColors.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Color(0x33FFFFFF)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .padding(end = 0.dp),
            ) { icon() }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title, color = Color.White,
                    fontWeight = FontWeight.Bold, fontSize = 15.sp,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    instruction, color = AppColors.textMuted,
                    fontSize = 11.sp,
                    lineHeight = 14.sp,
                )
            }
        }
    }
}

/**
 * Canvas-drawn tuning fork icon — two prongs with a U-curve at the top,
 * joined by a short arc at the base, and a vertical handle below.
 * Geometry is proportional so the icon scales cleanly at any size.
 */
@Composable
private fun TuningForkIcon(
    modifier: Modifier = Modifier,
    tint: Color = AppColors.gold,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 2.4.dp.toPx()
        val stroke = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)

        val cx        = w / 2f
        val spread    = w * 0.26f   // half-gap between prong centre lines
        val prongTop  = h * 0.09f
        val prongBot  = h * 0.50f
        val handleBot = h * 0.95f

        // Left prong
        drawLine(tint, Offset(cx - spread, prongBot), Offset(cx - spread, prongTop), sw, StrokeCap.Round)
        // Right prong
        drawLine(tint, Offset(cx + spread, prongBot), Offset(cx + spread, prongTop), sw, StrokeCap.Round)

        // U-curve at top (cubic Bézier: prong tips curve over the top)
        val arcPath = Path().apply {
            moveTo(cx - spread, prongTop)
            cubicTo(
                cx - spread, h * -0.06f,
                cx + spread, h * -0.06f,
                cx + spread, prongTop,
            )
        }
        drawPath(arcPath, tint, style = stroke)

        // Junction curve connecting prong bottoms
        val juncPath = Path().apply {
            moveTo(cx - spread, prongBot)
            quadraticTo(cx, prongBot + h * 0.08f, cx + spread, prongBot)
        }
        drawPath(juncPath, tint, style = stroke)

        // Handle (stem)
        drawLine(tint, Offset(cx, prongBot + h * 0.04f), Offset(cx, handleBot), sw, StrokeCap.Round)
    }
}

@Composable
private fun CalibrationStatus(info: CalibrationInfo) {
    val accent = if (info.calibrated) GameColors.good else AppColors.textDim
    var showInfo by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = if (info.calibrated) 0.05f else 0f))
            .border(1.dp, accent.copy(alpha = if (info.calibrated) 0.28f else 0.16f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(34.dp)
                    .background(accent.copy(alpha = 0.12f), CircleShape),
            ) {
                when {
                    info.calibrated && info.mode == CalibrationMode.LOOPBACK ->
                        Icon(Icons.Filled.Mic, null, tint = accent, modifier = Modifier.size(16.dp))
                    info.calibrated && info.mode == CalibrationMode.REFERENCE ->
                        TuningForkIcon(Modifier.size(16.dp), tint = accent)
                    info.calibrated ->
                        Icon(Icons.Filled.MusicNote, null, tint = accent, modifier = Modifier.size(16.dp))
                    else ->
                        Icon(Icons.Filled.MusicNote, null, tint = AppColors.textDim.copy(alpha = 0.30f), modifier = Modifier.size(16.dp))
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                if (info.calibrated) {
                    val modeLabel = when (info.mode) {
                        CalibrationMode.LOOPBACK   -> "MICROPHONE"
                        CalibrationMode.REFERENCE  -> "TUNING FORK"
                        CalibrationMode.INSTRUMENT -> "INSTRUMENT"
                        null                       -> "CALIBRATED"
                    }
                    Text(
                        modeLabel,
                        color = AppColors.textSubtle,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "±${fmtCents(info.accuracyCents)}",
                        color = accent,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Not calibrated",
                            color = AppColors.textMuted,
                            fontSize = 13.sp,
                        )
                        Spacer(Modifier.width(5.dp))
                        Box(
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { showInfo = !showInfo }
                                .padding(3.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Info,
                                contentDescription = "About calibration",
                                tint = if (showInfo) AppColors.textSecondary
                                       else AppColors.textDim.copy(alpha = 0.55f),
                                modifier = Modifier.size(13.dp),
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
        }

        AnimatedVisibility(
            visible = !info.calibrated && showInfo,
            enter = expandVertically() + fadeIn(tween(180)),
            exit  = shrinkVertically() + fadeOut(tween(180)),
        ) {
            Column {
                Spacer(Modifier.height(10.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(AppColors.surfaceVariant),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    "Calibration fine-tunes Metro's pitch detection for your specific phone and environment. Without it, results are still accurate enough for most practice and performance tuning.",
                    color = AppColors.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "It is not strictly required, but if you want the sharpest possible accuracy, particularly for critical intonation work, running a quick calibration is worth the extra minute.",
                    color = AppColors.textMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}


@Composable
private fun MicPermissionPrompt(onRequest: () -> Unit, isPermanentlyDenied: Boolean = false) {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (isPermanentlyDenied)
                    "Microphone access was blocked. Enable it in App Settings to use the tuner."
                else
                    "Microphone access needed to hear your instrument.",
                color = AppColors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            PrimaryButton(
                if (isPermanentlyDenied) "Open App Settings" else "Grant Microphone Access",
                onRequest,
            )
        }
    }
}

// ── Reference pitch pill ─────────────────────────────────────────────────────────

/** Informational-only pill shown in the header when reference pitch is not 440 Hz. */
@Composable
private fun ReferencePitchPill(referenceHz: Float, modifier: Modifier = Modifier) {
    GoldPill(text = "A4 = ${referenceHz.roundToInt()} Hz", modifier = modifier)
}

// ── Formatting helpers ───────────────────────────────────────────────────────────

private fun fmtCents(cents: Float): String =
    if (cents.isNaN()) "?" else String.format(Locale.US, "%.2f¢", cents)

// ── Previews ─────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360, heightDp = 380)
@Composable
private fun TunerGaugePreview() {
    val reading = Tuner.Reading(
        frequency = 442.3f, noteName = "A", octave = 4,
        cents = 9f, clarity = 0.97f, inTune = false,
    )
    Column(
        modifier = Modifier.padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TunerGauge(reading = reading)
        GaugeReadout(reading = reading, amplitude = 0.08f)
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun AmbientPanelPreview() {
    AmbientPanel(
        report = AmbientReport(
            state = ListeningState.ACQUIRING,
            headline = "Found a tone near A4",
            guidance = "Holding steady, confirming the note...",
            ambientLevel = AmbientLevel.MODERATE,
            candidateHz = 440f,
            stabilityCents = 5f,
            locked = false,
            humHz = 50f,
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun ReferencePitchCardPreview() {
    ReferencePitchCard(referenceHz = 440f, onNudge = {}, onSet = {})
}
