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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
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
import com.example.metrognome.groove.SessionAnalyzer
import com.example.metrognome.ui.theme.AppColors
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

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
                    // Absolute elapsedRealtime of the audio playhead while a recorded clip plays;
                    // null when stopped. Shared so the amplitude trace can draw a synced playhead.
                    var playheadMs by remember { mutableStateOf<Long?>(null) }
                    val clip by SessionAudioRecorder.clip.collectAsStateWithLifecycle()
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        SummaryCard(events, source, aecActive)
                        Spacer(Modifier.height(10.dp))
                        SessionAudioCard(onPlayheadMs = { playheadMs = it })
                        if (clip?.envelope?.isNotEmpty() == true || ampHistory.isNotEmpty()) {
                            AmplitudeTrace(clip, ampHistory, playheadMs)
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

    // The ACTUAL grade the player saw: the ViewModel stashes its exact analysis at session end.
    // Fall back to a live re-derivation from the (capped) event buffer only when none was stashed
    // (e.g. mid-session, or a Rhythm Game session which the analyzer does not grade).
    val stashed by MicDiagnosticsBuffer.lastAnalysis.collectAsStateWithLifecycle()
    val beatTimes = events.filterIsInstance<MicDiagnosticsEvent.BeatFired>().map { it.timestampMs }
    val analysis = stashed ?: SessionAnalyzer.analyze(accepted.map { it.timestampMs }, beatTimes)
    val authoritative = stashed != null

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

            Spacer(Modifier.height(12.dp))

            // ── THE LIVE GRADE (what the player saw) ──────────────────────────────
            Text(
                if (authoritative) "★ GRADE SHOWN TO PLAYER" else "SESSION ANALYZER (recomputed live)",
                color = AppColors.gold, fontFamily = FontFamily.Monospace,
                fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            KeyVal("groove score", "${if (analysis.confident) analysis.grooveScore else 0} / 100",
                if (analysis.confident) acceptColor else rejectColor)
            KeyVal("read", analysis.read, AppColors.textPrimary)
            KeyVal("rhythm strength (R)", "%.2f".format(analysis.rhythmStrength),
                if (analysis.confident) acceptColor else rejectColor)
            KeyVal("valid inputs", "${analysis.validInputs} / ${analysis.totalOnsets}", AppColors.textPrimary)
            KeyVal("your tempo", if (analysis.estimatedBpm > 0f) "~${analysis.estimatedBpm.roundToInt()} BPM" else "-",
                AppColors.textPrimary)
            KeyVal("self-consistency", "${analysis.selfConsistencyMs.roundToInt()} ms",
                if (analysis.selfConsistencyMs <= GrooveScorer.LOOSE_JITTER_MS) acceptColor else AppColors.textPrimary)
            KeyVal("grid bias / jitter", "${signed(analysis.gridBiasMs)} / ${analysis.gridJitterMs.roundToInt()} ms",
                AppColors.textMuted)
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

// ── Session audio playback ────────────────────────────────────────────────────

/**
 * Plays back the raw mic capture of the last session (written by [SessionAudioRecorder]) so the
 * developer can judge by ear what the detector actually heard. While playing, it reports the
 * playhead time (on the elapsedRealtime clock) so [AmplitudeTrace] can draw a synced cursor.
 * Renders nothing until a clip exists.
 */
@Composable
private fun SessionAudioCard(onPlayheadMs: (Long?) -> Unit) {
    val clip by SessionAudioRecorder.clip.collectAsStateWithLifecycle()
    val c = clip ?: return

    var isPlaying by remember(c.path) { mutableStateOf(false) }
    var positionMs by remember(c.path) { mutableLongStateOf(0L) }
    val player = remember(c.path) { MediaPlayer() }

    DisposableEffect(c.path) {
        var ok = false
        try {
            player.setDataSource(c.path)
            player.prepare()
            ok = true
        } catch (_: Exception) { /* corrupt/missing clip — card stays idle */ }
        onDispose {
            try { if (ok) player.stop() } catch (_: Exception) {}
            player.release()
            onPlayheadMs(null)
        }
    }

    LaunchedEffect(isPlaying, c.path) {
        while (isPlaying && player.isPlaying) {
            positionMs = player.currentPosition.toLong()
            onPlayheadMs(c.startMs + positionMs)
            delay(33.milliseconds)
        }
        if (isPlaying) {                 // loop fell through == playback finished
            isPlaying = false
            positionMs = 0L
            onPlayheadMs(null)
        }
    }

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(acceptColor.copy(alpha = 0.16f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (isPlaying) {
                            try { player.pause(); player.seekTo(0) } catch (_: Exception) {}
                            isPlaying = false; positionMs = 0L; onPlayheadMs(null)
                        } else {
                            try { player.seekTo(0); player.start(); isPlaying = true } catch (_: Exception) {}
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(if (isPlaying) "■" else "▶", color = acceptColor, fontSize = 16.sp)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "SESSION AUDIO",
                    color = AppColors.textMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "%.1f / %.1f s   %d Hz".format(positionMs / 1000f, c.durationMs / 1000f, c.sampleRate),
                    color = AppColors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                )
                Text(
                    "what the mic heard: listen and judge the clap/click calls yourself",
                    color = AppColors.textDim,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                )
            }
        }
    }
    Spacer(Modifier.height(10.dp))
}

// ── Amplitude trace ───────────────────────────────────────────────────────────

/** Width of one waveform section, in ms. The view pages through the recording one section at a time. */
private const val WAVEFORM_SECTION_MS = 8_000L

@Composable
private fun AmplitudeTrace(
    clip: SessionAudioRecorder.Clip?,
    amp: List<Pair<Long, Float>>,
    playheadMs: Long?,
) {
    val envelope = clip?.envelope
    if (!envelope.isNullOrEmpty()) {
        SectionedWaveform(clip, envelope, playheadMs)
    } else if (amp.size >= 2) {
        LiveAmplitudeTrace(amp)
    }
}

/**
 * The recorded session's full envelope, shown one [WAVEFORM_SECTION_MS] section at a time. The
 * green playhead scans left-to-right within a section; when playback crosses into the next section
 * the view jumps to it - so every part of the recording can be eyeballed against the audio, not
 * just the last few seconds (the old live trace only retained a ~7s ring buffer).
 */
@Composable
private fun SectionedWaveform(
    clip: SessionAudioRecorder.Clip,
    envelope: List<Float>,
    playheadMs: Long?,
) {
    val totalMs = clip.durationMs.coerceAtLeast(1L)
    val sectionCount = ((totalMs + WAVEFORM_SECTION_MS - 1) / WAVEFORM_SECTION_MS).toInt().coerceAtLeast(1)
    // Position within the recording (ms). Anchored to the clip start; 0 when idle.
    val posMs = (playheadMs?.let { it - clip.startMs } ?: 0L).coerceIn(0L, totalMs)
    val section = (posMs / WAVEFORM_SECTION_MS).toInt().coerceIn(0, sectionCount - 1)
    val sectionStartMs = section * WAVEFORM_SECTION_MS
    val sectionEndMs = minOf(sectionStartMs + WAVEFORM_SECTION_MS, totalMs)
    val hop = clip.envelopeHopMs.coerceAtLeast(1)
    val startIdx = (sectionStartMs / hop).toInt().coerceIn(0, envelope.size)
    val endIdx = (sectionEndMs / hop).toInt().coerceIn(startIdx, envelope.size)
    val peak = envelope.maxOrNull() ?: 1f

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("SESSION WAVEFORM", color = AppColors.textMuted, fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("section ${section + 1}/$sectionCount   ${"%.1f".format(sectionStartMs / 1000f)}-${"%.1f".format(sectionEndMs / 1000f)}s",
                    color = AppColors.gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
            ) {
                val w = size.width
                val h = size.height
                val norm = peak.coerceAtLeast(0.02f)
                // Map x across the full section width (so a short final section sits to the left and
                // the playhead's ms->x is identical to the playback mapping below).
                val count = (endIdx - startIdx).coerceAtLeast(1)
                val path = Path()
                for (k in 0 until count) {
                    val a = envelope[startIdx + k]
                    // Time offset of this sample within the section (startIdx aligns to sectionStart).
                    val x = w * (k * hop).toFloat() / WAVEFORM_SECTION_MS
                    val y = h - (a / norm).coerceIn(0f, 1f) * h
                    if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = AppColors.gold, style = Stroke(width = 2f, cap = StrokeCap.Round))
                drawLine(AppColors.textDim.copy(alpha = 0.4f), Offset(0f, h), Offset(w, h), strokeWidth = 1f)
                // Playhead within this section.
                if (playheadMs != null) {
                    val x = (w * (posMs - sectionStartMs).toFloat() / WAVEFORM_SECTION_MS).coerceIn(0f, w)
                    drawLine(acceptColor, Offset(x, 0f), Offset(x, h), strokeWidth = 2f)
                }
            }
            Text(
                "peak ${"%.2f".format(peak)}  ·  press play; the view jumps section by section as it scans",
                color = AppColors.textDim, fontSize = 9.sp, modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/** Fallback live trace (mid-session, before a clip is written): the ~7s amplitude ring buffer. */
@Composable
private fun LiveAmplitudeTrace(amp: List<Pair<Long, Float>>) {
    val peak = amp.maxOf { it.second }
    val tMin = amp.first().first
    val tMax = amp.last().first
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("MIC AMPLITUDE (live)", color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("peak ${"%.2f".format(peak)}", color = AppColors.gold, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Canvas(modifier = Modifier.fillMaxWidth().height(60.dp)) {
                val w = size.width
                val h = size.height
                val span = (tMax - tMin).coerceAtLeast(1L)
                val norm = peak.coerceAtLeast(0.05f)
                val path = Path()
                amp.forEachIndexed { i, (t, a) ->
                    val x = w * (t - tMin).toFloat() / span
                    val y = h - (a / norm).coerceIn(0f, 1f) * h
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, color = AppColors.gold, style = Stroke(width = 2f, cap = StrokeCap.Round))
                drawLine(AppColors.textDim.copy(alpha = 0.4f), Offset(0f, h), Offset(w, h), strokeWidth = 1f)
            }
        }
    }
}

// ── Event list ────────────────────────────────────────────────────────────────

@Composable
private fun EventList(events: List<MicDiagnosticsEvent>, sessionStart: Long) {
    // Anchor relative times to the session start when we have one; otherwise fall back to the first
    // event's timestamp. Without the fallback, a run where startSession() never fired (so sessionStart
    // stays 0 - e.g. a dev simulate run with no real mic) printed every row as +0.00s.
    val anchor = if (sessionStart > 0) sessionStart else events.firstOrNull()?.timestampMs ?: 0L
    Text(
        "EVENTS (newest last, ${events.size})",
        color = AppColors.textMuted,
        fontFamily = FontFamily.Monospace,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
    )
    events.forEach { ev ->
        val rel = if (anchor > 0) (ev.timestampMs - anchor) / 1000f else 0f
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
            "ratio=${"%.2f".format(ev.highRms / (ev.lowRms + 1e-9))} flat=${"%.2f".format(ev.flatness)}" to suppressColor
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
