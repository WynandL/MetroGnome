package com.example.metrognome.debug.mic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.groove.GrooveScorer
import com.example.metrognome.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.roundToInt

// Event-type colours, kept local to this debug overlay.
private val acceptColor   = Color(0xFF4CAF50)
private val suppressColor = Color(0xFF7E8AA0)
private val rejectColor   = Color(0xFFE53935)
private val beatColor     = Color(0xFF9575CD)

/**
 * Read-only viewer for the [MicDiagnosticsBuffer] - shows what the mic heard during
 * the most recent Speed Trainer (or Rhythm Game) session: every beat, every onset
 * (accepted / suppressed / rejected with its deviation), the amplitude trace, and a
 * summary that maps the result onto the PerformanceBonus gates so it is obvious why
 * a Timing Bonus did or did not pay out.
 *
 * Not real-time: it reflects whatever the singleton last captured. Run a session with
 * dev mode on and the mic active, then open this from Settings to review.
 *
 * Debug-only. Delete this file and its dev-gated call site to remove with zero impact.
 */
@Composable
fun MicTimingLogOverlay(onDismiss: () -> Unit) {
    val events       by MicDiagnosticsBuffer.events.collectAsStateWithLifecycle()
    val ampHistory   by MicDiagnosticsBuffer.ampHistory.collectAsStateWithLifecycle()
    val source       by MicDiagnosticsBuffer.source.collectAsStateWithLifecycle()
    val aecActive    by MicDiagnosticsBuffer.aecActive.collectAsStateWithLifecycle()
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
                    .padding(14.dp),
            ) {
                // ── Header ────────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚡", fontSize = 16.sp, color = AppColors.gold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "MIC TIMING LOG",
                        color = AppColors.gold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.weight(1f))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = onDismiss,
                            )
                            .padding(6.dp),
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = AppColors.textMuted)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Only SessionStarted is ever present with no real input; treat that as empty.
                val hasData = events.any {
                    it is MicDiagnosticsEvent.OnsetAccepted ||
                        it is MicDiagnosticsEvent.OnsetSuppressed ||
                        it is MicDiagnosticsEvent.ClickRejected ||
                        it is MicDiagnosticsEvent.OnsetRejected ||
                        it is MicDiagnosticsEvent.BeatFired
                }

                if (!hasData) {
                    EmptyState()
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        SummaryCard(events, source, aecActive)
                        Spacer(Modifier.height(10.dp))
                        if (ampHistory.isNotEmpty()) {
                            AmplitudeTrace(ampHistory)
                            Spacer(Modifier.height(10.dp))
                        }
                        EventList(events, sessionStart)
                    }
                }
            }
        }
    }
}

// ── Summary ─────────────────────────────────────────────────────────────────

@Composable
private fun SummaryCard(
    events: List<MicDiagnosticsEvent>,
    source: String,
    aecActive: Boolean,
) {
    val accepted  = events.filterIsInstance<MicDiagnosticsEvent.OnsetAccepted>()
    // Spectral mode replaced the old time-suppression, so this slot now counts the clicks the
    // ClapDetector caught and dropped — the positive proof the filter is doing its job.
    val clicksRejected = events.count { it is MicDiagnosticsEvent.ClickRejected }
    val rejected   = events.count { it is MicDiagnosticsEvent.OnsetRejected }
    val beats      = events.count { it is MicDiagnosticsEvent.BeatFired }

    val calDevs = accepted.map { it.calibratedDeviationMs }
    val avgAbs  = calDevs.takeIf { it.isNotEmpty() }?.map { abs(it) }?.average()?.toFloat()
    val tightest = calDevs.minByOrNull { abs(it) }
    val loosest  = calDevs.maxByOrNull { abs(it) }

    val groove = GrooveScorer.score(calDevs)
    val meetsMinHits = accepted.size >= GrooveScorer.MIN_HITS

    // Plain-language verdict that mirrors GrooveScorer exactly (consistency-first grading).
    val (verdict, verdictColor) = when {
        accepted.size < GrooveScorer.MIN_HITS ->
            "Only ${accepted.size} onset(s) accepted - need >= ${GrooveScorer.MIN_HITS}. " +
                "Grade is 0 regardless of timing. The mic is not hearing enough distinct hits " +
                "(finger taps are weak; a clap registers far better)." to rejectColor
        !groove.qualified ->
            "Hits were too scattered to grade - groove score 0." to rejectColor
        else ->
            "Groove score ${groove.grooveScore}/100 (${groove.read}). Driven by consistency " +
                "(jitter ${groove.jitterMs.roundToInt()}ms) with a ${signed(groove.biasMs)}ms offset. " +
                "Gnotes = round(sessionMinutes x ${"%.2f".format(groove.fraction)}), floored to >= 1." to acceptColor
    }

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "${source.ifBlank { "?" }}   AEC ${if (aecActive) "on" else "off"}",
                color = AppColors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("ACCEPTED", "${accepted.size}", acceptColor, Modifier.weight(1f))
                StatChip("CLICKS", "$clicksRejected", suppressColor, Modifier.weight(1f))
                StatChip("REJECTED", "$rejected", rejectColor, Modifier.weight(1f))
                StatChip("BEATS", "$beats", beatColor, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))

            KeyVal("MIN_HITS gate", if (meetsMinHits) "PASS (>= ${GrooveScorer.MIN_HITS})" else "FAIL (< ${GrooveScorer.MIN_HITS})",
                if (meetsMinHits) acceptColor else rejectColor)
            KeyVal("groove score", "${groove.grooveScore} / 100", if (groove.qualified) acceptColor else rejectColor)
            KeyVal("read", groove.read, AppColors.textPrimary)
            KeyVal("consistency (jitter)", "${groove.jitterMs.roundToInt()} ms", AppColors.textPrimary)
            KeyVal("offset (bias)", "${signed(groove.biasMs)} ms", AppColors.textPrimary)
            KeyVal("avg |deviation|", avgAbs?.let { "${it.roundToInt()} ms" } ?: "-", AppColors.textPrimary)
            KeyVal("tightest hit", tightest?.let { "${signed(it)} ms" } ?: "-", AppColors.textPrimary)
            KeyVal("loosest hit", loosest?.let { "${signed(it)} ms" } ?: "-", AppColors.textPrimary)
            KeyVal("jitter curve", "${GrooveScorer.TIGHT_JITTER_MS.roundToInt()} / ${GrooveScorer.LOOSE_JITTER_MS.roundToInt()} ms",
                AppColors.textMuted)

            Spacer(Modifier.height(12.dp))

            Text(
                verdict,
                color = verdictColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

@Composable
private fun StatChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.height(52.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 18.sp, fontFamily = FontFamily.Monospace)
            Text(label, color = color.copy(alpha = 0.8f), fontSize = 7.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
    }
}

@Composable
private fun KeyVal(key: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(value, color = valueColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Amplitude trace ───────────────────────────────────────────────────────────

@Composable
private fun AmplitudeTrace(amp: List<Pair<Long, Float>>) {
    val peak = amp.maxOf { it.second }
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MIC AMPLITUDE", color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("peak ${"%.2f".format(peak)}", color = AppColors.gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp),
            ) {
                if (amp.size < 2) return@Canvas
                val w = size.width
                val h = size.height
                // Normalise against peak so a quiet trace is still visible (with a floor).
                val norm = peak.coerceAtLeast(0.05f)
                val path = Path()
                amp.forEachIndexed { i, (_, a) ->
                    val x = w * i / (amp.size - 1)
                    val y = h - (a / norm).coerceIn(0f, 1f) * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = AppColors.gold, style = Stroke(width = 2f, cap = StrokeCap.Round))
                drawLine(
                    color = AppColors.textDim.copy(alpha = 0.4f),
                    start = Offset(0f, h),
                    end = Offset(w, h),
                    strokeWidth = 1f,
                )
            }
        }
    }
}

// ── Event list ────────────────────────────────────────────────────────────────

@Composable
private fun EventList(events: List<MicDiagnosticsEvent>, sessionStart: Long) {
    Text(
        "EVENTS (newest last, ${events.size})",
        color = AppColors.textMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
    events.forEach { ev ->
        val rel = if (sessionStart > 0) (ev.timestampMs - sessionStart) / 1000f else 0f
        val (text, color) = describe(ev)
        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Text(
                "%+6.2fs".format(rel),
                color = AppColors.textDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
            )
            Spacer(Modifier.width(8.dp))
            Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
        }
    }
    Spacer(Modifier.height(20.dp))
}

private fun describe(ev: MicDiagnosticsEvent): Pair<String, Color> = when (ev) {
    is MicDiagnosticsEvent.SessionStarted ->
        "SESSION START  ${ev.source}  aec=${ev.aecActive}" to AppColors.textMuted
    is MicDiagnosticsEvent.SessionEnded ->
        "SESSION END" to AppColors.textMuted
    is MicDiagnosticsEvent.BeatFired ->
        "BEAT ${ev.beat}" to beatColor
    is MicDiagnosticsEvent.OnsetAccepted ->
        "ACCEPT raw=${signed(ev.rawDeviationMs)} cal=${signed(ev.calibratedDeviationMs)}" to acceptColor
    is MicDiagnosticsEvent.OnsetSuppressed ->
        "SUPPRESS (inside click window)" to suppressColor
    is MicDiagnosticsEvent.ClickRejected ->
        "CLICK rejected  lo=${"%.0f".format(ev.lowRms)} hi=${"%.0f".format(ev.highRms)} " +
            "ratio=${"%.2f".format(ev.highRms / (ev.lowRms + 1e-9))} peak=${"%.2f".format(ev.peakRatio)}" to suppressColor
    is MicDiagnosticsEvent.OnsetRejected ->
        "REJECT raw=${signed(ev.rawDeviationMs)} (> 500ms)" to rejectColor
    is MicDiagnosticsEvent.CalibrationSample ->
        "CAL SAMPLE #${ev.sampleIndex} raw=${signed(ev.rawDeviationMs)}" to AppColors.textMuted
    is MicDiagnosticsEvent.CalibrationFinalized ->
        "CAL DONE bias=${signed(ev.biasMs)} n=${ev.sampleCount}" to AppColors.gold
}

private fun signed(ms: Float): String = "%+d".format(ms.roundToInt())

// ── Empty state ───────────────────────────────────────────────────────────────

@Composable
private fun EmptyState() {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "No mic session captured yet.\n\n" +
                "1. Make sure mic mode is calibrated and ON.\n" +
                "2. Make sure the dev Easter egg is enabled (it is, if you got here).\n" +
                "3. Run a Speed Trainer session and tap/clap along.\n" +
                "4. Come back here, the last session's onsets appear above.\n\n" +
                "Only real-mic sessions log here; \"Simulate\" does not run the mic.",
            color = AppColors.textMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(16.dp),
        )
    }
}
