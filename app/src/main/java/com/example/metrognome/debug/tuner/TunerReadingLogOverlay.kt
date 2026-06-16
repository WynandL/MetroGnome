package com.example.metrognome.debug.tuner

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.theme.AppColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln

private val goodCents = Color(0xFF4CAF50)
private val warnCents = Color(0xFFFFB300)
private val badCents  = Color(0xFFE53935)

/**
 * Read-only viewer for [TunerReadingLog] — the known-truth tuner accuracy test.
 *
 * Per detected note it shows count, MEAN cents (≈ the detector+calibration bias for that
 * note), min/max cents (≈ +/- the played offset), mean Hz, and mean clarity. The header
 * shows the calibration factor in force and its equivalent cents, so a run made
 * uncalibrated and a run made calibrated can be compared directly — the mean-cents column
 * should pull toward zero once a good calibration is applied.
 *
 * Recording is toggled from the dev tools; this is just the read-out. Debug-only.
 */
@Composable
fun TunerReadingLogOverlay(onDismiss: () -> Unit) {
    val samples by TunerReadingLog.samples.collectAsStateWithLifecycle()
    val stats = remember(samples) { TunerReadingLog.statsByNote(samples) }

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
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("🎯", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "TUNER READING LOG",
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

                if (samples.isEmpty()) {
                    EmptyState()
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Header(samples)
                        Spacer(Modifier.height(10.dp))
                        StatsTable(stats)
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(samples: List<TunerReadingLog.Sample>) {
    val factor = samples.lastOrNull()?.calFactor ?: 1f
    val biasCents = (1200.0 * ln(factor.toDouble()) / ln(2.0)).toFloat()
    val recording = TunerReadingLog.recording

    Surface(color = AppColors.surfaceDim, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                if (recording) "● RECORDING" else "■ stopped",
                color = if (recording) badCents else AppColors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            KeyVal("samples", "${samples.size}", AppColors.textPrimary)
            KeyVal(
                "calibration factor",
                String.format(Locale.US, "%.5f  (%+.1f¢)", factor, biasCents),
                if (abs(biasCents) < 0.05f) AppColors.textMuted else AppColors.gold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "mean¢ per note ≈ detector + calibration bias. min/max ≈ the ±offset that was " +
                    "played. Run once uncalibrated and once calibrated; mean¢ should move toward 0.",
                color = AppColors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun StatsTable(stats: List<TunerReadingLog.NoteStats>) {
    Surface(color = AppColors.surfaceDim, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Column headers
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
                HeaderCell("NOTE", 1.1f)
                HeaderCell("n", 0.7f)
                HeaderCell("mean¢", 1.1f)
                HeaderCell("min¢", 1.0f)
                HeaderCell("max¢", 1.0f)
                HeaderCell("Hz", 1.4f)
                HeaderCell("clar", 1.0f)
            }
            stats.forEach { s ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Cell(s.note, 1.1f, AppColors.gold, bold = true)
                    Cell("${s.count}", 0.7f, if (s.reliable) AppColors.textMuted else AppColors.textDim)
                    // Unreliable mean (thin / one-sided sweep): show it dimmed with a "*"
                    // marker instead of the bias colours, so it can't read as a real error.
                    Cell(
                        signed(s.meanCents) + if (s.reliable) "" else "*",
                        1.1f,
                        if (s.reliable) centsColor(s.meanCents) else AppColors.textDim,
                        bold = true,
                    )
                    Cell(signed(s.minCents), 1.0f, AppColors.textSecondary)
                    Cell(signed(s.maxCents), 1.0f, AppColors.textSecondary)
                    Cell(String.format(Locale.US, "%.1f", s.meanHz), 1.4f, AppColors.textPrimary)
                    Cell(String.format(Locale.US, "%.2f", s.meanClarity), 1.0f, AppColors.textMuted)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "* mean unreliable — thin sample or one-sided sweep (min/max didn't straddle 0¢)",
                color = AppColors.textDim,
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                lineHeight = 13.sp,
            )
        }
    }
}

private fun centsColor(c: Float): Color = when {
    abs(c) <= 3f -> goodCents
    abs(c) <= 10f -> warnCents
    else -> badCents
}

private fun signed(c: Float): String = String.format(Locale.US, "%+.1f", c)

@Composable
private fun RowScope.HeaderCell(text: String, weight: Float) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        color = AppColors.textMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun RowScope.Cell(
    text: String,
    weight: Float,
    color: Color,
    bold: Boolean = false,
) {
    Text(
        text,
        modifier = Modifier.weight(weight),
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}

@Composable
private fun KeyVal(key: String, value: String, valueColor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(value, color = valueColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState() {
    Surface(color = AppColors.surfaceDim, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
        Text(
            "No tuner readings captured yet.\n\n" +
                "1. In dev tools, turn \"Record Readings\" ON (clears any previous run).\n" +
                "2. Go to the Tuner and play the test-tone file (tools/tuner_test_tones) into the mic.\n" +
                "3. Come back here — each detected note's stats appear above.\n\n" +
                "Tip: run once uncalibrated, screenshot, then calibrate and run again to compare the " +
                "mean¢ column.",
            color = AppColors.textMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(16.dp),
        )
    }
}
