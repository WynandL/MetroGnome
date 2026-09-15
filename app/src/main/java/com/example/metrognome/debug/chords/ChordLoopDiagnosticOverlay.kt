package com.example.metrognome.debug.chords

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.ChordFinderViewModel

private val passColor = Color(0xFF4CAF50)
private val failColor = Color(0xFFE53935)
private val runColor  = Color(0xFF64B5F6)

/**
 * DEV ONLY: the engineering view of [ChordLoopDiagnostic]. Run it, walk to the Chords tab
 * within three seconds, and come back to read the rounds: for each chord what was expected,
 * what the finder captured and named, each note's fault signature, and the knob the loop
 * turned before the next round. The timings that pass are stored on the device.
 *
 * Same idiom as the mic self-test and profile round-trip overlays: full-screen dialog,
 * monospace, pass/fail tinted cards.
 */
@Composable
fun ChordLoopDiagnosticOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val vm: ChordFinderViewModel = viewModel()
    val store = remember { ChordTestTimingsStore(context) }
    val state by ChordLoopDiagnostic.state.collectAsStateWithLifecycle()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.97f))
                .statusBarsPadding(),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                // ── Header ────────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⟳", fontSize = 16.sp, color = AppColors.gold)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "CHORD LOOP",
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

                Spacer(Modifier.height(10.dp))

                // ── Controls ──────────────────────────────────────────────────
                val running = state.status == ChordLoopDiagnostic.Status.RUNNING ||
                    state.status == ChordLoopDiagnostic.Status.WAITING_FOR_MIC
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (running) {
                                ChordLoopDiagnostic.cancel()
                            } else {
                                val ref = context.getSharedPreferences("tuner_prefs", Context.MODE_PRIVATE).getFloat("reference_hz", 440f)
                                ChordLoopDiagnostic.start(vm, store, ref)
                                Toast.makeText(context, "Go to the Chords tab now: the loop starts in 3 s", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (running) failColor else AppColors.gold),
                        border = BorderStroke(1.dp, if (running) failColor else AppColors.gold),
                    ) {
                        Text(if (running) "Cancel" else "Run (3 s delay)", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            store.clear()
                            Toast.makeText(context, "Timings reset to defaults", Toast.LENGTH_SHORT).show()
                        },
                        enabled = !running,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.devRed),
                        border = BorderStroke(1.dp, AppColors.devRedBorder),
                    ) {
                        Text("Reset timings", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Status ────────────────────────────────────────────────────
                val tint = when (state.status) {
                    ChordLoopDiagnostic.Status.PASSED -> passColor
                    ChordLoopDiagnostic.Status.FAILED -> failColor
                    ChordLoopDiagnostic.Status.IDLE -> AppColors.textMuted
                    else -> runColor
                }
                Surface(color = tint.copy(alpha = 0.14f), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Mono(state.status.name.replace('_', ' '), tint, bold = true)
                        Spacer(Modifier.height(4.dp))
                        Mono(state.message.ifEmpty {
                            if (store.isTuned) "Stored timings: ${store.load()}" else "Not tuned yet. Defaults: ${ChordTestTimings.DEFAULT}"
                        }, AppColors.textSecondary)
                        Spacer(Modifier.height(4.dp))
                        Mono("Sequence: ${ChordArpeggioTestTone.description}", AppColors.textMuted)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Rounds, latest first ──────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    state.rounds.asReversed().forEach { round -> RoundCard(round) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundCard(round: ChordLoopDiagnostic.RoundReport) {
    val tint = if (round.pass) passColor else failColor
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono("ROUND ${round.round}", tint, bold = true)
                Spacer(Modifier.weight(1f))
                Mono(if (round.pass) "PASS" else "FAIL", tint, bold = true)
            }
            Mono(round.timings.toString(), AppColors.textMuted)
            Spacer(Modifier.height(8.dp))
            round.chords.forEach { chord ->
                val ok = chord.pass
                Mono(
                    "${chord.expectedSymbol}  ${chord.expectedNotes.joinToString(" ") { NoteNames.labelOf(it) }}",
                    if (ok) passColor else AppColors.textPrimary, bold = true,
                )
                Mono(
                    "got ${chord.gotSymbol ?: "no name"}  ${chord.gotNotes.joinToString(" ") { NoteNames.labelOf(it) }.ifEmpty { "nothing" }}",
                    if (ok) passColor else failColor,
                )
                chord.notes.filter { it.fault != ChordLoopDiagnostic.Fault.OK }.forEach { n ->
                    val heard = n.heardMidis.joinToString(" ") { NoteNames.labelOf(it) }.ifEmpty { "nothing" }
                    Mono("  ${NoteNames.labelOf(n.expected)}: ${n.fault.name.lowercase().replace('_', ' ')}, heard $heard" +
                        (n.captured?.let { ", captured ${NoteNames.labelOf(it)}" } ?: ""), AppColors.textMuted)
                }
                chord.notes.filter { it.fault == ChordLoopDiagnostic.Fault.OK }.takeIf { it.isNotEmpty() }?.let { oks ->
                    Mono("  captured after " + oks.joinToString(" ") { "${it.captureDelayMs ?: 0}ms" }, AppColors.textDim)
                }
                if (chord.extras.isNotEmpty()) {
                    Mono("  extra: ${chord.extras.joinToString(" ") { NoteNames.labelOf(it) }}", failColor)
                }
                Spacer(Modifier.height(6.dp))
            }
            Mono("why: ${round.diagnosis}", AppColors.textSecondary)
            Mono("next: ${round.adjustment}", AppColors.gold)
        }
    }
}

@Composable
private fun Mono(text: String, color: Color, bold: Boolean = false) {
    Text(
        text,
        color = color,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp, lineHeight = 15.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
    )
}
