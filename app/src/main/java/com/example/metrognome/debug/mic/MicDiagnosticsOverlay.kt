package com.example.metrognome.debug.mic

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.audio.selftest.CheckStatus
import com.example.metrognome.audio.dsp.ClapDetector
import com.example.metrognome.audio.selftest.GradeLevel
import com.example.metrognome.audio.selftest.MicSelfTest
import com.example.metrognome.audio.selftest.ScoringPoint
import com.example.metrognome.audio.selftest.SelfTestPhase
import com.example.metrognome.audio.selftest.SelfTestReport
import com.example.metrognome.audio.selftest.SelfTestThresholds
import com.example.metrognome.ui.theme.AppColors

private val passColor = Color(0xFF4CAF50)
private val failColor = Color(0xFFE53935)
private val abortColor = Color(0xFFFF9800)

private fun statusColor(s: CheckStatus) = when (s) {
    CheckStatus.PASS -> passColor
    CheckStatus.FAIL -> failColor
    CheckStatus.ABORT -> abortColor
    CheckStatus.PENDING -> AppColors.textDim
}

/**
 * Full-screen engineering overlay: the mic acoustic-loopback self-test.
 *
 * Runs [MicSelfTest] - the device emits its own click/clap stimuli, hears them
 * back, and reports how accurately it can score a player. Shows live phase
 * progress and a final report card with the device latency constant.
 *
 * Debug-only. Public composable name kept stable so existing dev-gated call sites
 * compile unchanged.
 */
@SuppressLint("MissingPermission")
@Composable
fun MicDiagnosticsOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val selfTest = remember { MicSelfTest(context) }
    val ui by selfTest.state.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose { selfTest.cancel() }
    }

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
                    Text("⚙", fontSize = 16.sp, color = AppColors.gold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "MIC SELF-TEST",
                        color = AppColors.gold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        letterSpacing = 1.5.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.weight(1f))
                    CloseButton(onDismiss)
                }

                Spacer(Modifier.height(14.dp))

                val report = ui.report
                when {
                    report != null -> ReportCard(report, onRunAgain = { runTest(selfTest) })
                    ui.running -> RunningView(ui.phase, ui.statusLine, ui.liveLatencyMs)
                    else -> IntroView(onRun = { runTest(selfTest) })
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
private fun runTest(selfTest: MicSelfTest) = selfTest.run()

// ── Intro ───────────────────────────────────────────────────────────────────

@Composable
private fun IntroView(onRun: () -> Unit) {
    Column {
        MonoBody(
            "This plays a short series of clicks and claps through the speaker and " +
                "listens back through the mic - measuring how accurately the app can " +
                "score timing on this device.\n\n" +
                "Find a quiet spot, turn the volume up, and keep the phone still.",
        )
        Spacer(Modifier.height(20.dp))
        RunButton("RUN SELF-TEST", onRun)
    }
}

// ── Running ─────────────────────────────────────────────────────────────────

@Composable
private fun RunningView(phase: SelfTestPhase, status: String, liveLatency: Float?) {
    Column {
        val phases = listOf(
            SelfTestPhase.ENVIRONMENT to "Room",
            SelfTestPhase.SPEAKER_PATH to "Speaker",
            SelfTestPhase.DISCRIMINATION to "Discrimination",
            SelfTestPhase.SCORING to "Scoring",
        )
        phases.forEach { (p, label) ->
            val done = p.ordinal < phase.ordinal
            val active = p == phase
            val color = when {
                done -> passColor
                active -> AppColors.gold
                else -> AppColors.textDim
            }
            Row(modifier = Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (done) "✓" else if (active) "▶" else "·", color = color,
                    fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.width(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(label, color = color, fontFamily = FontFamily.Monospace, fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
            }
        }
        Spacer(Modifier.height(14.dp))
        MonoBody(status)
        if (liveLatency != null) {
            Spacer(Modifier.height(6.dp))
            Text("latency ≈ ${liveLatency.toInt()} ms", color = AppColors.gold,
                fontFamily = FontFamily.Monospace, fontSize = 12.sp)
        }
    }
}

// ── Report card ─────────────────────────────────────────────────────────────

@Composable
private fun ReportCard(report: SelfTestReport, onRunAgain: () -> Unit) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {

        // Verdict banner — states the graded outcome and the rule behind it, not just a word.
        val (verdictText, verdictColor) = when {
            report.verdict == CheckStatus.ABORT -> "RETRY · fixable condition (see notes)" to abortColor
            report.verdict == CheckStatus.PENDING -> "INCOMPLETE" to AppColors.textDim
            report.grade == GradeLevel.GOOD_FIT ->
                "GOOD FIT · timing lands in the perfect window · constant saved" to passColor
            report.grade == GradeLevel.USABLE ->
                "USABLE · scoring is fair, with caveats (see notes) · constant saved" to abortColor
            else -> "NOT A FIT · this device can't score reliably (see below)" to failColor
        }
        Surface(color = verdictColor.copy(alpha = 0.16f), shape = RoundedCornerShape(8.dp)) {
            Text(
                verdictText, color = verdictColor, fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.ExtraBold, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(report.deviceModel + "  ·  " + report.route.label, color = AppColors.textMuted,
            fontFamily = FontFamily.Monospace, fontSize = 9.sp)

        Spacer(Modifier.height(14.dp))

        // Each block: what it measured · the limit · how it was judged.
        CheckBlock("ENVIRONMENT", report.environment, listOf(
            "noise floor ${"%.3f".format(report.ambientFloor)}   (limit <= ${"%.3f".format(SelfTestThresholds.MAX_AMBIENT_FLOOR)})",
            "media volume ${(report.systemVolumeFraction * 100).toInt()}%   (min ${(SelfTestThresholds.MIN_VOLUME_FRACTION * 100).toInt()}%)",
        ))

        CheckBlock("SPEAKER PATH", report.speakerPath, buildList {
            val d = report.latencyDetail
            if (d != null && report.latencyMs != null) {
                add("latency ${report.latencyMs.toInt()} ms   (median of ${d.used}/${d.emitted} claps; ${d.warmupClaps} warm-up discarded)")
                add("jitter +-${report.latencyJitterMs?.toInt() ?: 0} ms   (limit <= ${SelfTestThresholds.MAX_LATENCY_JITTER_MS.toInt()})")
                add("split-half ${d.firstHalfMs.toInt()} vs ${d.secondHalfMs.toInt()} ms · drift ${d.splitDeltaMs.toInt()} ms   (limit <= ${SelfTestThresholds.MAX_LATENCY_SPLIT_DRIFT_MS.toInt()})")
                add("plausible range ${SelfTestThresholds.MIN_LATENCY_MS.toInt()} to ${SelfTestThresholds.MAX_LATENCY_MS.toInt()} ms")
            } else if (report.speakerPath == CheckStatus.FAIL) {
                add("no output timestamp from this device · timing not measurable")
            } else {
                add("not run")
            }
        })

        CheckBlock("DISCRIMINATION", report.discrimination, buildList {
            if (report.clickRejectRate != null) {
                add("from ${report.discPairs} click + clap pairs")
                add("click rejected ${pct(report.clickRejectRate)}   (min ${(SelfTestThresholds.MIN_CLICK_REJECT_RATE * 100).toInt()}%)")
                add("clap detected ${pct(report.clapDetectRate)}   (min ${(SelfTestThresholds.MIN_CLAP_DETECT_RATE * 100).toInt()}%)")
                val m = report.spectralMargins
                if (m != null) {
                    add("integrated ratio: click<=${ratioStr(m.clickIntegratedMax)} · clap>=${ratioStr(m.clapIntegratedMin)}   (split at ${"%.1f".format(ClapDetector.CLAP_BAND_RATIO)}, used)")
                    add("burst ratio: click<=${ratioStr(m.clickPeakMax)} · clap>=${ratioStr(m.clapPeakMin)}   (diagnostic only, not used)")
                }
            } else add("not run")
        })

        CheckBlock("SCORING", report.scoring, buildList {
            if (report.meanAbsResidualMs != null || report.detectionRecall != null) {
                val offsets = report.scoringPoints.size.coerceAtLeast(1)
                add("${report.scoringTrials} trials (${report.scoringTrials / offsets} x ${report.scoringPoints.size} offsets)")
                add("recall ${pct(report.detectionRecall)} overall · ${pct(report.outOfBandRecall)} clear of beat   (min ${(SelfTestThresholds.OUT_OF_BAND_MIN_RECALL * 100).toInt()}%)")
                add("on-beat dead zone ${report.maskingHalfWidthMs?.toInt() ?: 0} ms   (good <= ${SelfTestThresholds.GRADE_GOOD_MASK_MS.toInt()})")
                add("false positives ${report.falsePositives}")
                add("mean |error| ${report.meanAbsResidualMs?.toInt() ?: 0} ms   (good <= ${SelfTestThresholds.GRADE_GOOD_MEAN_MS.toInt()}, max ${SelfTestThresholds.GRADE_USABLE_MEAN_MS.toInt()})")
                add("p95 |error| ${report.p95AbsResidualMs?.toInt() ?: 0} ms   (good <= ${SelfTestThresholds.GRADE_GOOD_P95_MS.toInt()}, max ${SelfTestThresholds.GRADE_USABLE_P95_MS.toInt()})")
            } else add("not run")
        })

        // Per-offset residual table — the raw scoring evidence, one row per offset.
        if (report.scoringPoints.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text("PER-OFFSET RESIDUAL  (reported - injected, ms)", color = AppColors.textDim,
                fontFamily = FontFamily.Monospace, fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            ResidualHeader()
            report.scoringPoints.forEach { ResidualRow(it) }
        }

        // Latency constant
        if (report.latencyMs != null && report.verdict == CheckStatus.PASS) {
            Spacer(Modifier.height(14.dp))
            Surface(color = AppColors.surfaceDim, shape = RoundedCornerShape(6.dp)) {
                Text("SAVED LATENCY CONSTANT: ${report.latencyMs.toInt()} ms · applied to every mic feature",
                    color = AppColors.gold, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).fillMaxWidth())
            }
        }

        // Notes
        if (report.notes.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("NOTES", color = AppColors.textDim, fontFamily = FontFamily.Monospace,
                fontSize = 9.sp, letterSpacing = 1.sp)
            Spacer(Modifier.height(4.dp))
            report.notes.forEach { note ->
                Row(modifier = Modifier.padding(vertical = 2.dp)) {
                    Text("•", color = abortColor, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
                        modifier = Modifier.width(12.dp))
                    Text(note, color = AppColors.textSecondary, fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp, lineHeight = 13.sp)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        RunButton("RUN AGAIN", onRunAgain)
        Spacer(Modifier.height(20.dp))
    }
}

/** One check's heading (label + status) followed by its measured-vs-limit detail lines. */
@Composable
private fun CheckBlock(label: String, status: CheckStatus, lines: List<String>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AppColors.textSecondary, fontFamily = FontFamily.Monospace,
            fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(status.name, color = statusColor(status), fontFamily = FontFamily.Monospace,
            fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
    lines.forEach { line ->
        Text(line, color = AppColors.textMuted, fontFamily = FontFamily.Monospace,
            fontSize = 10.sp, lineHeight = 14.sp,
            modifier = Modifier.padding(start = 12.dp, top = 1.dp))
    }
    Spacer(Modifier.height(10.dp))
}

@Composable
private fun ResidualHeader() {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Cell("inject", AppColors.textDim); Cell("report", AppColors.textDim); Cell("residual", AppColors.textDim)
    }
}

@Composable
private fun ResidualRow(p: ScoringPoint) {
    val residual = p.residualMs
    // Game-anchored tolerances; a miss inside the masking band is the expected on-beat
    // dead zone (amber), a miss clear of the beat is a real failure (red).
    val color = when {
        residual == null ->
            if (kotlin.math.abs(p.injectedMs) <= SelfTestThresholds.MASKING_BAND_MS) abortColor else failColor
        kotlin.math.abs(residual) <= 15f -> passColor
        kotlin.math.abs(residual) <= 35f -> abortColor
        else -> failColor
    }
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Cell(fmt(p.injectedMs), AppColors.textSecondary)
        Cell(p.reportedMs?.let { fmt(it) } ?: "miss", AppColors.textSecondary)
        Cell(residual?.let { fmt(it) } ?: "-", color)
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Cell(text: String, color: Color) {
    Text(text, color = color, fontFamily = FontFamily.Monospace, fontSize = 10.sp,
        modifier = Modifier.weight(1f))
}

// ── Small shared pieces ───────────────────────────────────────────────────────

@Composable
private fun MonoBody(text: String) {
    Text(text, color = AppColors.textSecondary, fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, lineHeight = 16.sp)
}

@Composable
private fun RunButton(label: String, onClick: () -> Unit) {
    Surface(
        color = AppColors.gold,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(label, color = AppColors.background, fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp))
    }
}

@Composable
private fun CloseButton(onDismiss: () -> Unit) {
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

// ── Helpers ───────────────────────────────────────────────────────────────────

private fun fmt(ms: Float) = "${if (ms >= 0f) "+" else ""}${ms.toInt()}"
private fun pct(f: Float?) = f?.let { "${(it * 100).toInt()}%" } ?: "-"
private fun ratioStr(r: Float?) = r?.let { "%.1f".format(it) } ?: "-"
