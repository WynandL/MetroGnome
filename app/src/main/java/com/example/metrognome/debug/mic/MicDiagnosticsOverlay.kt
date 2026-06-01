package com.example.metrognome.debug.mic

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.theme.AppColors
import kotlin.math.abs

private const val TIMELINE_WINDOW_MS = 4000L

private val colorAccepted   = Color(0xFF4CAF50)
private val colorSuppressed = Color(0xFFFF9800)
private val colorRejected   = Color(0xFFE53935)
private val colorCalSample  = Color(0xFF6B8AE0)
private val colorSuppressZone = Color(0xFF7B5EA7)

/**
 * Full-screen engineering overlay: live mic timing diagnostics.
 *
 * Shows a 4-second scrolling timeline with beat markers, suppression windows,
 * estimated click play time, onset events, amplitude waveform, and a full
 * event log. All data comes from [MicDiagnosticsBuffer].
 *
 * Debug-only. Delete this file and [MicDiagnosticsTrigger] call sites to remove.
 */
@Composable
fun MicDiagnosticsOverlay(onDismiss: () -> Unit) {
    val events       by MicDiagnosticsBuffer.events.collectAsStateWithLifecycle()
    val ampHistory   by MicDiagnosticsBuffer.ampHistory.collectAsStateWithLifecycle()
    val source       by MicDiagnosticsBuffer.source.collectAsStateWithLifecycle()
    val aecActive    by MicDiagnosticsBuffer.aecActive.collectAsStateWithLifecycle()
    val biasMs       by MicDiagnosticsBuffer.latencyBiasMs.collectAsStateWithLifecycle()
    val rawDev       by MicDiagnosticsBuffer.lastRawDevMs.collectAsStateWithLifecycle()
    val calDev       by MicDiagnosticsBuffer.lastCalDevMs.collectAsStateWithLifecycle()
    val calSamples   by MicDiagnosticsBuffer.calibrationSampleCount.collectAsStateWithLifecycle()
    val sessionStart by MicDiagnosticsBuffer.sessionStartMs.collectAsStateWithLifecycle()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
                .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
            ) {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⚙", fontSize = 16.sp, color = AppColors.gold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "MIC DIAGNOSTICS",
                        color = AppColors.gold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        source.uppercase(),
                        color = AppColors.textMuted,
                        fontSize = 9.sp,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(AppColors.surfaceVariant)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            ),
                    ) {
                        Text("✕", color = AppColors.textSecondary, fontSize = 12.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))

                // ── Stats row ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    DiagStatChip("BIAS",  if (biasMs > 0f) "${biasMs.toInt()}ms" else "--", AppColors.gold)
                    DiagStatChip("AEC",   if (aecActive) "ON" else "OFF",
                        if (aecActive) colorAccepted else colorRejected)
                    DiagStatChip("RAW",   rawDev?.let { fmtDev(it) } ?: "--", AppColors.textSecondary)
                    DiagStatChip("CAL",   calDev?.let { fmtDev(it) } ?: "--",
                        calDev?.let { if (abs(it) <= 30f) colorAccepted else colorSuppressed }
                            ?: AppColors.textDim)
                    DiagStatChip("SMPL",  "$calSamples", AppColors.textMuted)
                }

                Spacer(Modifier.height(8.dp))

                // ── Legend ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LegendDot(AppColors.gold,                        "Beat")
                    LegendDot(colorSuppressZone.copy(alpha = 0.7f),  "Suppress")
                    LegendDot(AppColors.gold.copy(alpha = 0.55f),    "Est. click")
                    LegendDot(colorAccepted,                         "Accepted")
                    LegendDot(colorSuppressed,                       "Suppressed")
                    LegendDot(colorRejected,                         "Rejected")
                    LegendDot(colorCalSample,                        "Cal")
                }

                Spacer(Modifier.height(6.dp))

                // ── Timeline ──────────────────────────────────────────────────
                Text(
                    "TIMELINE  ◀  ${TIMELINE_WINDOW_MS / 1000}s window  ▶  NOW",
                    color = AppColors.textDim,
                    fontSize = 8.sp,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                DiagnosticsTimeline(
                    events = events,
                    ampHistory = ampHistory,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(6.dp)),
                )

                Spacer(Modifier.height(10.dp))

                // ── Event log ─────────────────────────────────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "EVENTS  (${events.size}/${MicDiagnosticsBuffer.MAX_EVENTS})",
                        color = AppColors.textDim,
                        fontSize = 8.sp,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        "CLEAR",
                        color = AppColors.textDim,
                        fontSize = 8.sp,
                        letterSpacing = 0.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .clickable { MicDiagnosticsBuffer.clear() }
                            .padding(vertical = 4.dp, horizontal = 8.dp),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.surfaceDeep)
                        .padding(6.dp),
                ) {
                    LazyColumn(
                        reverseLayout = true,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(events.reversed()) { event ->
                            DiagEventRow(event, sessionStart)
                        }
                    }
                }
            }
        }
    }
}

// ── Timeline Canvas ───────────────────────────────────────────────────────────

@Composable
private fun DiagnosticsTimeline(
    events: List<MicDiagnosticsEvent>,
    ampHistory: List<Pair<Long, Float>>,
    modifier: Modifier = Modifier,
) {
    // Fast animation tick — forces Canvas recomposition so the timeline scrolls live.
    @Suppress("UNUSED_VARIABLE")
    val tick by rememberInfiniteTransition(label = "diagTick")
        .animateFloat(0f, 1f, infiniteRepeatable(tween(150, easing = LinearEasing)), label = "tick")

    Canvas(modifier = modifier.background(AppColors.surfaceDeep)) {
        val nowMs = SystemClock.elapsedRealtime()
        val windowStart = nowMs - TIMELINE_WINDOW_MS
        val w = size.width
        val h = size.height
        val ampH = h * 0.22f
        val eventH = h - ampH

        fun timeToX(ms: Long) = (ms - windowStart).toFloat() / TIMELINE_WINDOW_MS * w

        // ── Time axis tick marks every 500ms ──────────────────────────────────
        var gridMs = (windowStart / 500L + 1) * 500L
        while (gridMs <= nowMs) {
            val x = timeToX(gridMs)
            if (x in 0f..w) {
                drawLine(
                    color = AppColors.surfaceVariant.copy(alpha = 0.5f),
                    start = Offset(x, 0f), end = Offset(x, eventH), strokeWidth = 0.5f,
                )
            }
            gridMs += 500L
        }

        // ── Beat events: suppression zone, estimated play, beat tick ──────────
        events.filterIsInstance<MicDiagnosticsEvent.BeatFired>()
            .filter { it.timestampMs >= windowStart - 500 }
            .forEach { beat ->
                val beatX     = timeToX(beat.timestampMs)
                val suppressX = timeToX(beat.suppressUntilMs)
                val playX     = timeToX(beat.estimatedPlayMs)

                // Suppression zone (purple tint)
                val left  = beatX.coerceAtLeast(0f)
                val right = suppressX.coerceAtMost(w)
                if (right > left) {
                    drawRect(
                        color = colorSuppressZone.copy(alpha = 0.18f),
                        topLeft = Offset(left, 0f),
                        size = Size(right - left, eventH),
                    )
                }

                // Estimated click play time — dashed gold vertical
                if (playX in 0f..w) {
                    val dash = 4.dp.toPx()
                    var y = 0f
                    while (y < eventH) {
                        drawLine(
                            color = AppColors.gold.copy(alpha = 0.55f),
                            start = Offset(playX, y),
                            end = Offset(playX, (y + dash).coerceAtMost(eventH)),
                            strokeWidth = 1.dp.toPx(),
                        )
                        y += dash * 2f
                    }
                }

                // Beat marker — solid gold tick
                if (beatX in 0f..w) {
                    drawLine(
                        color = AppColors.gold,
                        start = Offset(beatX, 0f), end = Offset(beatX, eventH),
                        strokeWidth = 1.5.dp.toPx(),
                    )
                }
            }

        // ── Onset events ──────────────────────────────────────────────────────
        val midY      = eventH * 0.48f
        val upperY    = eventH * 0.28f
        val lowerY    = eventH * 0.68f

        events.filter { it.timestampMs >= windowStart }.forEach { event ->
            when (event) {
                is MicDiagnosticsEvent.OnsetAccepted -> {
                    val x = timeToX(event.timestampMs)
                    if (x in 0f..w) {
                        drawCircle(colorAccepted.copy(alpha = 0.22f), 7.dp.toPx(), Offset(x, midY))
                        drawCircle(colorAccepted, 3.5.dp.toPx(), Offset(x, midY))
                    }
                }
                is MicDiagnosticsEvent.OnsetSuppressed -> {
                    val x = timeToX(event.timestampMs)
                    if (x in 0f..w) drawCircle(colorSuppressed, 3.dp.toPx(), Offset(x, upperY))
                }
                is MicDiagnosticsEvent.OnsetRejected -> {
                    val x = timeToX(event.timestampMs)
                    if (x in 0f..w) drawCircle(colorRejected, 3.dp.toPx(), Offset(x, lowerY))
                }
                is MicDiagnosticsEvent.CalibrationSample -> {
                    val x = timeToX(event.timestampMs)
                    if (x in 0f..w) drawCircle(colorCalSample, 3.dp.toPx(), Offset(x, midY))
                }
                else -> {}
            }
        }

        // ── Divider between event zone and amplitude zone ─────────────────────
        drawLine(
            color = AppColors.surfaceVariant.copy(alpha = 0.6f),
            start = Offset(0f, eventH), end = Offset(w, eventH), strokeWidth = 0.5f,
        )

        // ── Amplitude waveform (bottom strip) ─────────────────────────────────
        val barW = (w / (TIMELINE_WINDOW_MS / 23f)).coerceAtLeast(1.5f)
        ampHistory.filter { it.first >= windowStart }.forEach { (t, amp) ->
            val x = timeToX(t)
            if (x in 0f..w) {
                val barH = (amp * ampH).coerceAtLeast(0.5f)
                drawRect(
                    color = colorAccepted.copy(alpha = 0.60f),
                    topLeft = Offset(x, h - barH),
                    size = Size(barW, barH),
                )
            }
        }

        // ── "NOW" edge ────────────────────────────────────────────────────────
        drawLine(Color.White.copy(alpha = 0.30f), Offset(w - 1f, 0f), Offset(w - 1f, h), 1f)
    }
}

// ── Event log row ─────────────────────────────────────────────────────────────

@Composable
private fun DiagEventRow(event: MicDiagnosticsEvent, sessionStartMs: Long) {
    val relMs = event.timestampMs - sessionStartMs
    val timeStr = "%+7d".format(relMs)

    val (icon, iconColor, desc) = when (event) {
        is MicDiagnosticsEvent.SessionStarted ->
            Triple("▶", AppColors.primaryPurple,
                "SESSION START  ${event.source}  AEC=${event.aecActive}")
        is MicDiagnosticsEvent.SessionEnded ->
            Triple("■", AppColors.textDim, "SESSION END")
        is MicDiagnosticsEvent.BeatFired ->
            Triple("│", AppColors.gold,
                "BEAT ${event.beat}  supp→+${event.suppressUntilMs - event.timestampMs}ms" +
                "  play≈+${event.estimatedPlayMs - event.timestampMs}ms")
        is MicDiagnosticsEvent.OnsetAccepted ->
            Triple("●", colorAccepted,
                "ACCEPTED  raw ${fmtDev(event.rawDeviationMs)}  cal ${fmtDev(event.calibratedDeviationMs)}")
        is MicDiagnosticsEvent.OnsetSuppressed ->
            Triple("○", colorSuppressed,
                "SUPPRESSED  window ends in +${event.suppressUntilMs - event.timestampMs}ms")
        is MicDiagnosticsEvent.OnsetRejected ->
            Triple("✗", colorRejected,
                "REJECTED  raw ${fmtDev(event.rawDeviationMs)}")
        is MicDiagnosticsEvent.CalibrationSample ->
            Triple("·", colorCalSample,
                "CAL #${event.sampleIndex + 1}  ${fmtDev(event.rawDeviationMs)}")
        is MicDiagnosticsEvent.CalibrationFinalized ->
            Triple("✓", AppColors.gold,
                "CALIBRATED  bias=${event.biasMs.toInt()}ms  from ${event.sampleCount} samples")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = iconColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(11.dp))
        Spacer(Modifier.width(4.dp))
        Text(timeStr, color = AppColors.textDim, fontSize = 9.sp, fontFamily = FontFamily.Monospace,
            modifier = Modifier.width(54.dp))
        Spacer(Modifier.width(4.dp))
        Text(desc, color = AppColors.textSecondary, fontSize = 9.sp,
            fontFamily = FontFamily.Monospace, lineHeight = 11.sp)
    }
}

// ── Stat chip + legend ────────────────────────────────────────────────────────

@Composable
private fun DiagStatChip(label: String, value: String, valueColor: Color) {
    Surface(color = AppColors.surfaceDim, shape = RoundedCornerShape(5.dp)) {
        Column(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(label, color = AppColors.textDim, fontSize = 7.sp, letterSpacing = 0.5.sp,
                fontFamily = FontFamily.Monospace)
            Text(value, color = valueColor, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace)
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(3.dp))
        Text(label, color = AppColors.textDim, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
    }
}

// ── Floating trigger button ───────────────────────────────────────────────────

/**
 * Small ⚙ chip shown in debug builds while a mic session is active.
 * Tap to open [MicDiagnosticsOverlay].
 */
@Composable
fun MicDiagnosticsTrigger(
    visible: Boolean,
    overlayOpen: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (overlayOpen) AppColors.gold else AppColors.surfaceDim)
            .border(1.dp, AppColors.gold.copy(alpha = 0.65f), CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onToggle,
            ),
    ) {
        Text(
            "⚙",
            fontSize = 14.sp,
            color = if (overlayOpen) AppColors.background else AppColors.gold,
        )
    }
}

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun fmtDev(ms: Float) = "${if (ms >= 0f) "+" else ""}${ms.toInt()}ms"
