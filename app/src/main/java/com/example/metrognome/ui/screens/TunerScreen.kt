package com.example.metrognome.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ads.AdBannerView
import com.example.metrognome.audio.AmbientLevel
import com.example.metrognome.audio.AmbientReport
import com.example.metrognome.audio.ListeningState
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.Tuner
import com.example.metrognome.ui.components.TunerFeedbackCard
import com.example.metrognome.ui.dialogs.CalibrationDialog
import com.example.metrognome.ui.dialogs.InstrumentCalibrationDialog
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import com.example.metrognome.viewmodel.CalibrationInfo
import com.example.metrognome.viewmodel.CalibrationMode
import com.example.metrognome.viewmodel.CalibrationState
import com.example.metrognome.viewmodel.TunerViewModel
import java.util.Locale
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
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> micGranted = granted }

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

    val activity = LocalActivity.current
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
            keepScreenOn = keepScreenOn,
            onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
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
) {
    val pendingReading = remember { mutableStateOf<Tuner.Reading?>(null) }

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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.size(36.dp))
            Text(
                "TUNER", color = AppColors.gold, fontSize = 22.sp,
                fontWeight = FontWeight.Black, letterSpacing = 3.sp,
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
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
            MicPermissionPrompt(onRequestMic)
            Spacer(Modifier.height(10.dp))
        }

        TunerGauge(reading = reading)
        GaugeReadout(reading = reading, amplitude = amplitude)

        if (reading != null && calibration == CalibrationState.Idle) {
            Spacer(Modifier.height(8.dp))
            InstrumentCalibrationChip(reading = reading, onClick = { pendingReading.value = reading })
        }

        Spacer(Modifier.height(10.dp))
        AmbientHistogram(ambient)

        Spacer(Modifier.height(12.dp))
        ReferencePitchCard(
            referenceHz = referenceHz,
            onNudge = onNudgeReference,
            onSet = onSetReferenceHz,
        )

        Spacer(Modifier.height(12.dp))
        CalibrationCard(
            info = calibrationInfo,
            referenceHz = referenceHz,
            onLoopbackCalibrate = onLoopbackCalibrate,
            onReferenceCalibrate = onReferenceCalibrate,
            onClear = onClearCalibration,
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

    pendingReading.value?.let { r ->
        InstrumentCalibrationDialog(
            reading = r,
            onConfirm = { onCalibrateToNote(r); pendingReading.value = null },
            onDismiss = { pendingReading.value = null },
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

        // Frequency chips — HEARD → D2 SITS AT — only when a note is detected.
        if (reading != null) {
            Spacer(Modifier.height(10.dp))
            val targetHz = reading.frequency / 2.0.pow(reading.cents / 1200.0)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ReadoutChip("HEARD", String.format(Locale.US, "%.1f Hz", reading.frequency))
                Text("→", color = AppColors.textDim, fontSize = 14.sp)
                ReadoutChip(
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
private fun ReadoutChip(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label, color = AppColors.textSubtle, fontSize = 9.sp,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
        Text(value, color = AppColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
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
 * Collapsed (default): a pulsing orb whose color communicates the engine state
 * without demanding attention, a calm label, the candidate note, and the
 * frequency histogram the musician already loves.
 *
 * Tap to expand: noise floor (3 dots), pitch-spread bar, and the engine's
 * guidance text.  Interesting for curious musicians, invisible for everyone else.
 */
@Composable
private fun AmbientHistogram(report: AmbientReport) {
    var expanded by remember { mutableStateOf(false) }
    val stateColor by animateColorAsState(ambientStateColor(report.state), label = "ambientColor")
    val chevronDeg by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {

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
                AmbientOrb(color = stateColor)
                Spacer(Modifier.width(10.dp))
                // Cross-fade the label text so rapid state flips don't flash
                AnimatedContent(
                    targetState = ambientStateLabel(report.state),
                    transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
                    label = "stateLabel",
                ) { label ->
                    Text(
                        label,
                        color = stateColor.copy(alpha = 0.85f),
                        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp,
                    )
                }
                Spacer(Modifier.weight(1f))
                // Fixed-width slot — always reserves 36 dp so the chevron never shifts
                Box(modifier = Modifier.width(36.dp), contentAlignment = Alignment.CenterEnd) {
                    val noteText = if (report.candidateHz != null &&
                            report.state != ListeningState.PROFILING)
                        NoteNames.label(report.candidateHz) else ""
                    Text(
                        noteText,
                        color = if (report.locked) GameColors.good else AppColors.gold,
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

            // ── Frequency histogram ───────────────────────────────────────────────
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
            ) {
                drawHistogramGrid()
                drawAmbientBars(
                    bins = report.ambientBins,
                    candidateHz = report.candidateHz,
                    stateColor = stateColor,
                    locked = report.locked,
                )
            }

            // Frequency axis labels — A2 A3 A4 A5 as anchor points
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                listOf(110f to "A2", 220f to "A3", 440f to "A4", 880f to "A5").forEach { (hz, label) ->
                    Text(
                        label,
                        color = if (hz == 440f) AppColors.gold.copy(alpha = 0.6f)
                                else AppColors.textDim,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    )
                }
            }

            // ── Expanded environment detail ───────────────────────────────────────
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit  = shrinkVertically() + fadeOut(),
            ) {
                Column {
                    Spacer(Modifier.height(12.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(AppColors.surfaceVariant),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "ENVIRONMENT",
                        color = AppColors.textDim,
                        fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp,
                    )
                    Spacer(Modifier.height(8.dp))

                    // Noise floor — 3 dots always present; only color changes.
                    // Fixed-width label box (56 dp) prevents dots from shifting.
                    val dotColor = noiseColor(report.ambientLevel)
                    AmbientDetailRow("Noise floor") {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(3) { i ->
                                Box(
                                    Modifier.size(7.dp).clip(CircleShape)
                                        .background(
                                            if (i < noiseDots(report.ambientLevel)) dotColor
                                            else AppColors.surfaceVariant
                                        ),
                                )
                            }
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.width(56.dp)) {
                            Text(noiseLabel(report.ambientLevel), color = dotColor, fontSize = 10.sp)
                        }
                    }

                    Spacer(Modifier.height(6.dp))

                    // Pitch spread — bar and value always present; grey/dash when no data.
                    // This prevents the guidance text from jumping as states change.
                    val hasSpread = !report.stabilityCents.isNaN() && report.stabilityCents >= 0f &&
                            report.state in listOf(
                                ListeningState.ACQUIRING,
                                ListeningState.LOCKED,
                                ListeningState.UNSTABLE,
                            )
                    val spreadFrac = if (hasSpread) (report.stabilityCents / 50f).coerceIn(0f, 1f) else 0f
                    val spreadColor = when {
                        !hasSpread                  -> AppColors.textDim
                        report.stabilityCents < 5f  -> GameColors.good
                        report.stabilityCents < 18f -> AppColors.gold
                        else                        -> AppColors.warning
                    }
                    AmbientDetailRow("Pitch spread") {
                        Box(
                            modifier = Modifier
                                .width(70.dp).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(AppColors.surfaceVariant),
                        ) {
                            Box(
                                Modifier.fillMaxWidth(spreadFrac).fillMaxHeight()
                                    .background(spreadColor),
                            )
                        }
                        Spacer(Modifier.width(6.dp))
                        Box(modifier = Modifier.width(28.dp)) {
                            Text(
                                if (hasSpread) "${report.stabilityCents.roundToInt()}¢" else "--",
                                color = if (hasSpread) spreadColor else AppColors.textDim,
                                fontSize = 10.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Text(
                        report.guidance,
                        color = AppColors.textMuted,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        minLines = 2,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/** Subtle horizontal reference lines at 25 / 50 / 75 / 100 % of bar height. */
private fun DrawScope.drawHistogramGrid() {
    val gridColor = AppColors.surfaceVariant.copy(alpha = 0.55f)
    val strokePx = 0.7.dp.toPx()
    for (fraction in listOf(0.25f, 0.5f, 0.75f, 1.0f)) {
        val y = size.height * (1f - fraction)
        drawLine(
            color = gridColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = strokePx,
        )
    }
}

private fun DrawScope.drawAmbientBars(
    bins: FloatArray,
    candidateHz: Float?,
    stateColor: Color,
    locked: Boolean,
) {
    if (bins.isEmpty()) return

    val count = bins.size
    val barW = size.width / count
    val gap = (barW * 0.18f).coerceAtLeast(1.5f)
    val netW = barW - gap
    val cornerR = CornerRadius(netW / 2f, netW / 2f)

    val barColor = stateColor.copy(alpha = 0.45f)
    val barColorFull = stateColor.copy(alpha = 0.75f)

    for (i in 0 until count) {
        val level = bins[i]
        val barH = (level * size.height * 0.90f).coerceAtLeast(if (level > 0f) 3.dp.toPx() else 0f)
        val x = i * barW + gap / 2f
        val y = size.height - barH
        if (barH > 0f) {
            drawRoundRect(
                color = if (level > 0.6f) barColorFull else barColor,
                topLeft = Offset(x, y),
                size = Size(netW, barH),
                cornerRadius = cornerR,
            )
        } else {
            // Baseline tick so the axis is always visible
            drawRoundRect(
                color = AppColors.surfaceVariant.copy(alpha = 0.5f),
                topLeft = Offset(x, size.height - 2.dp.toPx()),
                size = Size(netW, 2.dp.toPx()),
                cornerRadius = cornerR,
            )
        }
    }

    // Gold marker for the current candidate / locked frequency
    if (candidateHz != null) {
        val lo = ln(AmbientReport.BIN_FREQ_LO.toDouble())
        val hi = ln(AmbientReport.BIN_FREQ_HI.toDouble())
        val t = ((ln(candidateHz.toDouble()) - lo) / (hi - lo)).coerceIn(0.0, 1.0).toFloat()
        val cx = t * size.width
        val markerColor = if (locked) GameColors.good else AppColors.gold
        drawLine(
            color = markerColor.copy(alpha = 0.9f),
            start = Offset(cx, 0f),
            end = Offset(cx, size.height),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawCircle(markerColor, 5.dp.toPx(), Offset(cx, size.height * 0.3f))
    }
}

private fun ambientStateColor(state: ListeningState): Color = when (state) {
    ListeningState.LOCKED                          -> GameColors.good
    ListeningState.ACQUIRING                       -> AppColors.gold
    ListeningState.UNSTABLE, ListeningState.NOISE  -> AppColors.warning
    ListeningState.QUIET, ListeningState.PROFILING -> GameColors.rangeBlue
}

private fun ambientStateLabel(state: ListeningState): String = when (state) {
    ListeningState.PROFILING -> "learning the room"
    ListeningState.QUIET     -> "listening"
    ListeningState.NOISE     -> "background noise"
    ListeningState.UNSTABLE  -> "voice detected"
    ListeningState.ACQUIRING -> "note found"
    ListeningState.LOCKED    -> "signal locked"
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

/**
 * A three-layer pulsing orb whose color reflects the current [ListeningState].
 * The outer halo breathes outward; the core brightens at its peak.  The effect is
 * ambient enough to ignore while actively tuning, yet immediately readable at a glance.
 */
@Composable
private fun AmbientOrb(color: Color) {
    val transition = rememberInfiniteTransition(label = "ambientOrb")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue  = 1f,
        animationSpec = infiniteRepeatable(
            animation  = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "orbPulse",
    )
    Canvas(modifier = Modifier.size(20.dp)) {
        val r = size.minDimension / 2f
        drawCircle(color.copy(alpha = 0.07f + pulse * 0.10f), radius = r * (1.2f + pulse * 0.15f))
        drawCircle(color.copy(alpha = 0.18f + pulse * 0.22f), radius = r * (0.82f + pulse * 0.12f))
        drawCircle(color.copy(alpha = 0.60f + pulse * 0.40f), radius = r * 0.50f)
    }
}

// ── Reference pitch ──────────────────────────────────────────────────────────────

@Composable
private fun ReferencePitchCard(
    referenceHz: Float,
    onNudge: (Float) -> Unit,
    onSet: (Float) -> Unit,
) {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Text(
                "REFERENCE PITCH", color = AppColors.textSubtle,
                fontSize = 10.sp, letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "A4 = ${referenceHz.roundToInt()} Hz",
                color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black,
            )

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−") { onNudge(-1f) }
                Slider(
                    value = referenceHz,
                    onValueChange = onSet,
                    valueRange = TunerViewModel.MIN_REFERENCE..TunerViewModel.MAX_REFERENCE,
                    steps = (TunerViewModel.MAX_REFERENCE - TunerViewModel.MIN_REFERENCE).toInt() - 1,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.gold,
                        activeTrackColor = AppColors.primaryPurple,
                        inactiveTrackColor = AppColors.surfaceVariant,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                )
                StepButton("+") { onNudge(1f) }
            }
        }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = AppColors.surfaceVariant,
        modifier = Modifier.size(38.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = AppColors.gold, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ── Instrument calibration chip ─────────────────────────────────────────────────

/**
 * Pill-shaped prompt that appears below the gauge when a note is detected and no
 * calibration is running. Tapping it opens [InstrumentCalibrationDialog].
 */
@Composable
private fun InstrumentCalibrationChip(reading: Tuner.Reading, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50.dp),
        color = AppColors.gold.copy(alpha = 0.10f),
        border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.MusicNote, contentDescription = null,
                tint = AppColors.gold, modifier = Modifier.size(13.dp),
            )
            Text(
                "My ${reading.noteName}${reading.octave} is in tune",
                color = AppColors.gold, fontSize = 12.sp, fontWeight = FontWeight.Medium,
            )
        }
    }
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
                "Calibration", color = AppColors.gold, fontSize = 12.sp,
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
    if (info.calibrated) {
        StatusLine(GameColors.good, "Calibrated · ±${fmtCents(info.accuracyCents)}")
    } else {
        StatusLine(AppColors.textMuted, "Not calibrated")
    }

    Spacer(Modifier.height(14.dp))
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
private fun StatusLine(color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        Text(text, color = AppColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun GoldButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.height(46.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = AppColors.primaryPurple),
    ) {
        Text(label, fontWeight = FontWeight.Bold, fontSize = 14.sp, letterSpacing = 1.sp)
    }
}

@Composable
private fun MicPermissionPrompt(onRequest: () -> Unit) {
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
                "Microphone access needed to hear your instrument.",
                color = AppColors.textSecondary, fontSize = 12.sp, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            GoldButton("Grant Microphone Access", onRequest)
        }
    }
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
private fun AmbientHistogramPreview() {
    val bins = FloatArray(AmbientReport.BIN_COUNT) { i ->
        when {
            i < 4 -> 0f
            i in 6..8 -> 0.8f
            i == 12 -> 0.4f
            else -> 0f
        }
    }
    AmbientHistogram(
        report = AmbientReport(
            state = ListeningState.ACQUIRING,
            headline = "", guidance = "",
            ambientLevel = AmbientLevel.QUIET,
            candidateHz = 440f,
            stabilityCents = 5f,
            locked = false,
            ambientBins = bins,
        )
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0D0B1E, widthDp = 360)
@Composable
private fun ReferencePitchCardPreview() {
    ReferencePitchCard(referenceHz = 440f, onNudge = {}, onSet = {})
}
