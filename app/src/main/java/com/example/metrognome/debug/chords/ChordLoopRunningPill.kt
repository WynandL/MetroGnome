package com.example.metrognome.debug.chords

import android.os.SystemClock
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * DEV ONLY: a small pill that sits over the Chords tab while a [ChordLoopDiagnostic] run
 * is in progress, so the developer can tell it is running (and which chord it is on)
 * without waiting to hear the next note, and can stop it from where they are standing.
 * Tapping it cancels the run. Composes to nothing when no run is in progress, which is
 * always the case in a release build, since only the dev tools can start one; it needs no
 * debug gate of its own.
 *
 * The bar under the text is progress through the current pass, one step per note: a
 * spaced round (ten notes; the round counter says how many rounds could follow) or the
 * whole legato comparison (both engines). Added because a run whose only sign of life
 * was the chord name gave no sense of how long it would go on, and the dev cancelled one
 * mid-way for that reason alone.
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
    // Progress is a function of the clock while a chord sounds; tick it so the bar moves per note.
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(running) {
        while (running) { now = SystemClock.elapsedRealtime(); delay(100) }
    }
    val rawProgress = if (running) state.progressAt(now) else 0f
    val progress by animateFloatAsState(rawProgress, tween(150), label = "chordLoopProgress")
    val notesSoFar = (rawProgress * state.notesTotal).roundToInt()

    val tint = if (waiting) AppColors.devBlue else AppColors.gold
    val text = if (waiting) {
        "Chord Loop waiting: turn the mic on to start"
    } else {
        val chord = ChordArpeggioTestTone.EXPECTED_SYMBOLS.getOrNull(state.chordIndex)
        val where = if (chord != null) "$chord, chord ${state.chordIndex + 1} of ${ChordArpeggioTestTone.CHORDS.size}" else "between chords"
        val engine = state.engine?.let { " · ${it.methodName}" } ?: ""
        when (state.mode) {
            ChordLoopDiagnostic.Mode.LEGATO -> "Legato$engine · $where"
            ChordLoopDiagnostic.Mode.LISTEN -> "Listening$engine · make some noise"
            ChordLoopDiagnostic.Mode.SPACED -> "Round ${state.round} of ${ChordLoopDiagnostic.MAX_ROUNDS}$engine · $where"
        }
    }

    Surface(
        modifier = modifier.clickable { ChordLoopDiagnostic.cancel() },
        shape = RoundedCornerShape(16.dp),
        color = AppColors.surfaceDeep,
        border = BorderStroke(1.dp, tint),
        tonalElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
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
            if (running) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(tint.copy(alpha = 0.2f)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(progress)
                                .height(4.dp)
                                .background(tint, CircleShape),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "$notesSoFar/${state.notesTotal} notes · tap to cancel",
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = tint.copy(alpha = 0.8f),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
