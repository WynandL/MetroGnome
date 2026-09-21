package com.example.metrognome.debug.chords

import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.example.metrognome.audio.chords.ChordEngine
import com.example.metrognome.ui.components.AppFilterChip
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.ChordFinderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val passColor = Color(0xFF4CAF50)
private val failColor = Color(0xFFE53935)
private val runColor  = Color(0xFF64B5F6)

/**
 * DEV ONLY: the engineering view of [ChordLoopDiagnostic]. Pick a mode, run it, walk to
 * the Chords tab (the loop starts when that tab's mic opens), and come back to read the
 * result. Spaced: the rounds, each chord's expected and captured notes, each note's fault
 * signature and the knob the loop turned; the timings that pass are stored on the device.
 * Legato: one card per engine with a PASS/FAIL, notes and chords caught, the median time
 * from a note starting to its capture, and the same per-chord detail; the status card
 * carries the verdict in a sentence.
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
    val currentEngine by vm.engine.collectAsStateWithLifecycle()
    var mode by remember { mutableStateOf(state.mode) }
    val scope = rememberCoroutineScope()

    // The system picker: the dev chooses where the file goes (Drive is one of the choices),
    // which needs no FileProvider and no storage permission.
    val saveLog = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val snapshot = ChordLoopDiagnostic.state.value
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val json = snapshot.toJsonReport()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                        ?: error("could not open the destination")
                    json.length
                }
            }
            Toast.makeText(
                context,
                result.fold({ "Log saved (${it / 1024} KB)" }, { "Save failed: ${it.message}" }),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

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

                // Mode
                val running = state.status == ChordLoopDiagnostic.Status.RUNNING ||
                    state.status == ChordLoopDiagnostic.Status.WAITING_FOR_MIC
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    ChordLoopDiagnostic.Mode.entries.forEach { m ->
                        AppFilterChip(selected = m == mode, onClick = { if (!running) mode = m }, label = m.label)
                    }
                }
                Mono(mode.blurb, AppColors.textMuted)
                Mono(
                    if (mode != ChordLoopDiagnostic.Mode.LEGATO) "Engine: ${currentEngine.methodName} (chosen on the Chords tab)"
                    else "Engines: ${ChordEngine.entries.joinToString(" then ") { it.methodName }}; the tab's choice is restored after",
                    AppColors.textMuted,
                )
                Spacer(Modifier.height(10.dp))
                // ── Controls ──────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            if (running) {
                                ChordLoopDiagnostic.cancel()
                            } else {
                                val ref = context.getSharedPreferences("tuner_prefs", Context.MODE_PRIVATE).getFloat("reference_hz", 440f)
                                ChordLoopDiagnostic.start(vm, store, ref, mode, context.filesDir)
                                Toast.makeText(context, "Go to the Chords tab: the loop starts when its mic opens", Toast.LENGTH_LONG).show()
                                onDismiss()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (running) failColor else AppColors.gold),
                        border = BorderStroke(1.dp, if (running) failColor else AppColors.gold),
                    ) {
                        Text(if (running) "Cancel" else "Run ${mode.label}", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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

                val hasReport = state.rounds.isNotEmpty() || state.legato.isNotEmpty()
                if (hasReport) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                            saveLog.launch("chord_loop_${state.mode.name.lowercase()}_$stamp.json")
                        },
                        enabled = !running,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = AppColors.textAccent),
                        border = BorderStroke(1.dp, AppColors.textAccent),
                    ) {
                        Text("Save Log (JSON, every frame)", fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
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
                        if (state.mode == ChordLoopDiagnostic.Mode.LEGATO && state.legato.isNotEmpty()) {
                            Spacer(Modifier.height(6.dp))
                            state.legato.forEach { r ->
                                Mono("${r.engine.methodName.uppercase()}  ${if (r.pass) "PASS" else "FAIL"}  ${r.summary}",
                                    if (r.pass) passColor else failColor, bold = true)
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Mono("Sequence: ${ChordArpeggioTestTone.description}", AppColors.textMuted)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // ── Rounds, latest first ──────────────────────────────────────
                Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                    if (state.mode == ChordLoopDiagnostic.Mode.LEGATO) {
                        state.legato.forEach { report -> LegatoCard(report) }
                    } else {
                        state.rounds.asReversed().forEach { round -> RoundCard(round) }
                    }
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
                Mono("ROUND ${round.round} on ${round.engine.methodName}", tint, bold = true)
                Spacer(Modifier.weight(1f))
                Mono(if (round.pass) "PASS" else "FAIL", tint, bold = true)
            }
            Mono(round.timings.toString(), AppColors.textMuted)
            Spacer(Modifier.height(8.dp))
            ChordLines(round.chords)
            Mono("why: ${round.diagnosis}", AppColors.textSecondary)
            Mono("next: ${round.adjustment}", AppColors.gold)
        }
    }
}

/**
 * One engine's legato result: PASS/FAIL, the counts and the median capture delay in the
 * header, then the same per-chord lines as a round. The delay is measured from the note's
 * nominal start and includes the speaker's start-up, which is the same for both engines,
 * so the difference between the two cards is the engines'.
 */
@Composable
private fun LegatoCard(report: ChordLoopDiagnostic.LegatoReport) {
    val tint = if (report.pass) passColor else failColor
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.5f)),
        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Mono(report.engine.methodName.uppercase(), tint, bold = true)
                Spacer(Modifier.weight(1f))
                Mono(if (report.pass) "PASS" else "FAIL", tint, bold = true)
            }
            Mono(report.summary, AppColors.textSecondary)
            Mono("delay: from the note's start to its capture, speaker latency included", AppColors.textDim)
            Spacer(Modifier.height(8.dp))
            ChordLines(report.chords)
        }
    }
}

/** The per-chord lines shared by a round and a legato card. */
@Composable
private fun ChordLines(chords: List<ChordLoopDiagnostic.ChordResult>) {
    chords.forEach { chord ->
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
