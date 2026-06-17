package com.example.metrognome.debug.tuner

import androidx.compose.foundation.Canvas
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
import com.example.metrognome.audio.tuner.AmbientTuning
import com.example.metrognome.ui.theme.AppColors

// Local accent colours for this debug overlay.
private val saveColor   = Color(0xFF4CAF50)
private val hijackColor  = Color(0xFFFFB300)
private val dropColor    = Color(0xFFE53935)

/**
 * Read-only viewer for [TunerLockLog] — shows how well the tuner held lock during the most
 * recent listening session: every completed lock, how long it held, how low clarity/presence
 * dipped while it survived, and the two headline proofs that the noise-robustness layer is
 * working: PRESENCE SAVES (frames kept alive by the targeted probe when the global pitch was
 * gone) and HIJACK SURVIVALS (a louder wrong pitch arrived but the lock held).
 *
 * Run the tuner against a real instrument (ideally with some background noise / talking),
 * stop it, then open this from Settings to review. Debug-only; delete this file and its dev
 * call site to remove with zero impact.
 */
@Composable
fun TunerLockLogOverlay(onDismiss: () -> Unit) {
    val sessions by TunerLockLog.sessions.collectAsStateWithLifecycle()

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
                // ── Header ────────────────────────────────────────────────────
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("🔒", fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "TUNER LOCK LOG",
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

                if (sessions.isEmpty()) {
                    EmptyState()
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        SummaryCard(sessions)
                        Spacer(Modifier.height(10.dp))
                        sessions.asReversed().forEach { LockRow(it) }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(sessions: List<TunerLockLog.LockSession>) {
    val totalHoldMs = sessions.sumOf { it.holdMs }
    val avgHoldMs = totalHoldMs / sessions.size
    val presenceSaves = sessions.sumOf { it.presenceSaves }
    val hijacks = sessions.sumOf { it.hijackSurvivals }
    val drops = sessions.groupingBy { it.dropReason }.eachCount()
    val level = AmbientTuning.level

    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                "noise mode: ${level.label}",
                color = if (level == AmbientTuning.Level.STANDARD) AppColors.textMuted else saveColor,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(10.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatChip("LOCKS", "${sessions.size}", AppColors.gold, Modifier.weight(1f))
                StatChip("SAVES", "$presenceSaves", saveColor, Modifier.weight(1f))
                StatChip("HIJACKS", "$hijacks", hijackColor, Modifier.weight(1f))
            }

            Spacer(Modifier.height(10.dp))
            KeyVal("avg hold", "$avgHoldMs ms", AppColors.textPrimary)
            KeyVal("total locked", "${(totalHoldMs / 1000f).let { "%.1f".format(it) }} s", AppColors.textPrimary)
            KeyVal("drop reasons", drops.entries.joinToString("  ") { "${it.key}:${it.value}" }, AppColors.textPrimary)

            Spacer(Modifier.height(10.dp))
            Text(
                "SAVES = the presence probe kept a lock alive when the global pitch was gone. " +
                    "HIJACKS = a louder wrong pitch arrived but the lock survived. Higher = the " +
                    "noise-robustness layer earning its keep. Cycle \"Noise Mode\" (Off/Medium/" +
                    "Aggressive) in dev tools and compare runs in the same room.",
                color = AppColors.textMuted,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 15.sp,
            )
        }
    }
}

@Composable
private fun LockRow(s: TunerLockLog.LockSession) {
    val dropTint = if (s.dropReason == "STOP") AppColors.textMuted else dropColor
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    s.noteLabel,
                    color = AppColors.gold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "${s.holdMs} ms",
                    color = AppColors.textPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "→ ${s.dropReason}",
                    color = dropTint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(6.dp))
            HoldBar(s.holdMs)
            Spacer(Modifier.height(6.dp))
            Text(
                "saves ${s.presenceSaves}   hijacks ${s.hijackSurvivals}   " +
                    "min clarity ${"%.2f".format(s.minClarity)}   min presence ${"%.2f".format(s.minPresence)}   " +
                    s.ambientLevel.lowercase(),
                color = AppColors.textMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun HoldBar(holdMs: Long) {
    // Cap the bar at 10 s so a very long hold does not flatten the rest.
    val frac = (holdMs / 10_000f).coerceIn(0.02f, 1f)
    Canvas(modifier = Modifier.fillMaxWidth().height(6.dp)) {
        drawRoundRect(
            color = AppColors.surfaceVariant,
            size = size,
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
        )
        drawRoundRect(
            color = saveColor,
            size = size.copy(width = size.width * frac),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
        )
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(key, color = AppColors.textMuted, fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(value, color = valueColor, fontFamily = FontFamily.Monospace, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EmptyState() {
    Surface(
        color = AppColors.surfaceDim,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            "No tuner locks captured yet.\n\n" +
                "1. Open the Tuner and play a sustained note (with some background noise for a real test).\n" +
                "2. Let it lock, hold, and eventually drop a few times.\n" +
                "3. Leave the Tuner, come back here — each completed lock appears above.\n\n" +
                "Cycle \"Noise Mode\" (Off/Medium/Aggressive) in dev tools between runs to compare.",
            color = AppColors.textMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 17.sp,
            modifier = Modifier.padding(16.dp),
        )
    }
}
