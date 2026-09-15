package com.example.metrognome.debug.chords

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.theme.AppColors

/**
 * DEV ONLY: a small pill that sits over the Chords tab while a [ChordLoopDiagnostic] run
 * is in progress, so the developer can tell it is running (and which chord it is on)
 * without waiting to hear the next note, and can stop it from where they are standing.
 * Tapping it cancels the run. Composes to nothing when no run is in progress, which is
 * always the case in a release build, since only the dev tools can start one; it needs no
 * debug gate of its own.
 */
@Composable
fun ChordLoopRunningPill(modifier: Modifier = Modifier) {
    val state by ChordLoopDiagnostic.state.collectAsStateWithLifecycle()
    val waiting = state.status == ChordLoopDiagnostic.Status.WAITING_FOR_MIC
    val running = state.status == ChordLoopDiagnostic.Status.RUNNING
    if (!waiting && !running) return

    val pulse by rememberInfiniteTransition(label = "chordLoopPulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "chordLoopDot",
    )
    val tint = if (waiting) AppColors.devBlue else AppColors.gold
    val text = if (waiting) {
        "Chord Loop waiting: turn the mic on to start"
    } else {
        val chord = ChordArpeggioTestTone.EXPECTED_SYMBOLS.getOrNull(state.chordIndex)
        val where = if (chord != null) "$chord, chord ${state.chordIndex + 1} of ${ChordArpeggioTestTone.CHORDS.size}" else "between chords"
        "Chord Loop round ${state.round}: $where. Tap to cancel"
    }

    Surface(
        modifier = modifier.clickable { ChordLoopDiagnostic.cancel() },
        shape = CircleShape,
        color = AppColors.surfaceDeep,
        border = BorderStroke(1.dp, tint),
        tonalElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(
                Modifier
                    .size(8.dp)
                    .alpha(pulse)
                    .background(tint, CircleShape),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = tint,
                maxLines = 1,
            )
        }
    }
}
