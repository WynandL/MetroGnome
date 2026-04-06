package com.example.metrognome.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.viewmodel.GamePhase
import com.example.metrognome.viewmodel.HitQuality
import com.example.metrognome.viewmodel.RhythmGameViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

// ── Difficulty definitions ─────────────────────────────────────────────────────

private data class Difficulty(val name: String, val bpm: Int, val beats: Int, val desc: String)

private val difficulties = listOf(
    Difficulty("Beginner",  60, 16, "60 BPM · 16 beats  —  slow & steady"),
    Difficulty("Easy",      80, 24, "80 BPM · 24 beats  —  getting into the groove"),
    Difficulty("Medium",   100, 32, "100 BPM · 32 beats  —  the classic challenge"),
    Difficulty("Hard",     130, 32, "130 BPM · 32 beats  —  quick reflexes needed"),
    Difficulty("Expert",   160, 48, "160 BPM · 48 beats  —  for seasoned rhythmists"),
)

// ── Root screen ────────────────────────────────────────────────────────────────

@Composable
fun RhythmGameScreen(
    vm: RhythmGameViewModel,
    isMetronomePlaying: Boolean = false,
    onStopMetronome: () -> Unit = {}
) {
    val phase          by vm.phase.collectAsStateWithLifecycle()
    val score          by vm.score.collectAsStateWithLifecycle()
    val combo          by vm.combo.collectAsStateWithLifecycle()
    val countDown      by vm.countDown.collectAsStateWithLifecycle()
    val currentBeat    by vm.currentBeat.collectAsStateWithLifecycle()
    val timeSig        by vm.timeSig.collectAsStateWithLifecycle()
    val lastQuality    by vm.lastQuality.collectAsStateWithLifecycle()
    val result         by vm.result.collectAsStateWithLifecycle()
    val useMic         by vm.useMic.collectAsStateWithLifecycle()
    val beatIntervalMs by vm.beatIntervalMs.collectAsStateWithLifecycle()
    val lastHitOffset  by vm.lastHitOffset.collectAsStateWithLifecycle()
    val beatsRemaining by vm.beatsRemaining.collectAsStateWithLifecycle()
    val tolerance      by vm.tolerance.collectAsStateWithLifecycle()

    var micGranted by remember { mutableStateOf(false) }
    val micLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micGranted = granted
        if (granted) vm.toggleMic(true)
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0B1E))) {
        when (phase) {
            GamePhase.IDLE -> IdlePanel(
                vm = vm, useMic = useMic, micGranted = micGranted,
                onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                tolerance = tolerance,
                onToleranceChange = { vm.setTolerance(it) },
                isMetronomePlaying = isMetronomePlaying,
                onStopMetronome = onStopMetronome
            )
            GamePhase.COUNTDOWN -> CountdownPanel(countDown)
            GamePhase.PLAYING   -> PlayingPanel(
                vm = vm, score = score, combo = combo,
                timeSig = timeSig, lastQuality = lastQuality,
                beatIntervalMs = beatIntervalMs, lastHitOffset = lastHitOffset,
                beatsRemaining = beatsRemaining
            )
            GamePhase.RESULT -> ResultPanel(result = result, onDismiss = { vm.dismissResult() })
        }
        Spacer(Modifier.weight(1f))
        AdBannerView(modifier = Modifier.fillMaxWidth())
    }
}

// ── Idle — difficulty picker + tolerance settings ─────────────────────────────

@Composable
private fun IdlePanel(
    vm: RhythmGameViewModel,
    useMic: Boolean,
    micGranted: Boolean,
    onRequestMic: () -> Unit,
    tolerance: Float,
    onToleranceChange: (Float) -> Unit,
    isMetronomePlaying: Boolean,
    onStopMetronome: () -> Unit
) {
    var showStopDialog   by remember { mutableStateOf(false) }
    var pendingStart     by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showTolerance    by remember { mutableStateOf(false) }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false; pendingStart = null },
            title = { Text("Metronome is running") },
            text  = { Text("Stop it before starting the game, or let it keep playing in the background?") },
            confirmButton = {
                TextButton(onClick = { onStopMetronome(); pendingStart?.invoke(); showStopDialog = false; pendingStart = null }) {
                    Text("Stop & Play")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStart?.invoke(); showStopDialog = false; pendingStart = null }) {
                    Text("Keep Playing")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(28.dp))

        Text("RHYTHM GAME", color = Color(0xFFFFD700), fontSize = 22.sp,
            fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(Modifier.height(4.dp))
        Text(
            "Watch the ring fill up — tap when it's FULL!",
            color = Color(0xFF7070AA), fontSize = 13.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))

        difficulties.forEach { d ->
            DifficultyCard(difficulty = d, onClick = {
                val start = { vm.setDifficulty(d.bpm, d.beats); vm.startGame() }
                if (isMetronomePlaying) { pendingStart = start; showStopDialog = true } else start()
            })
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(14.dp))

        // ── Timing tolerance (developer/accessibility option) ─────────────────
        Surface(
            onClick = { showTolerance = !showTolerance },
            color = Color(0x221A1A3A),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Timing Tolerance", color = Color(0xFFCCCCEE),
                        fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text(toleranceLabel(tolerance), color = toleranceLabelColor(tolerance), fontSize = 12.sp)
                }
                Text(if (showTolerance) "▲" else "▼", color = Color(0xFF8080AA), fontSize = 12.sp)
            }
        }

        if (showTolerance) {
            Spacer(Modifier.height(6.dp))
            Surface(color = Color(0x221A1A3A), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val perfMs  = (150 * tolerance).toInt()
                    val greatMs = (280 * tolerance).toInt()
                    val goodMs  = (500 * tolerance).toInt()

                    Text(
                        "How forgiving the game is when judging your taps.",
                        color = Color(0xFF7070AA), fontSize = 12.sp
                    )
                    Spacer(Modifier.height(10.dp))
                    Slider(
                        value = tolerance,
                        onValueChange = onToleranceChange,
                        valueRange = 0.5f..2.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFFFFD700),
                            activeTrackColor = Color(0xFF5B2D8A),
                            inactiveTrackColor = Color(0x44FFFFFF)
                        )
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Strict", color = Color(0xFF5566AA), fontSize = 11.sp)
                        Text("Very Easy", color = Color(0xFF5566AA), fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    // Window breakdown
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        WindowBadge("PERFECT", "±${perfMs}ms",  Color(0xFFFFD700))
                        WindowBadge("GREAT",   "±${greatMs}ms", Color(0xFF7BE87B))
                        WindowBadge("GOOD",    "±${goodMs}ms",  Color(0xFF7BB8FF))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // Mic mode
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("Microphone Mode", color = Color(0xFFEEEEFF), fontWeight = FontWeight.Medium)
                Text("Detect claps/hits via mic", color = Color(0xFF8080AA), fontSize = 12.sp)
            }
            Switch(
                checked = useMic && micGranted,
                onCheckedChange = { on -> if (on && !micGranted) onRequestMic() else vm.toggleMic(on) },
                colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFFFFD700), checkedTrackColor = Color(0xFF5B2D8A))
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DifficultyCard(difficulty: Difficulty, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1838), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(difficulty.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(difficulty.desc, color = Color(0xFF7070AA), fontSize = 12.sp)
            }
            Text("▶", color = Color(0xFFFFD700), fontSize = 18.sp)
        }
    }
}

@Composable
private fun WindowBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
        Text(value, color = Color(0xFFCCCCEE), fontSize = 12.sp)
    }
}

private fun toleranceLabel(t: Float) = when {
    t < 0.8f -> "Strict (Pro)"
    t < 1.2f -> "Normal"
    t < 1.8f -> "Easy (default)"
    else     -> "Very Easy"
}

private fun toleranceLabelColor(t: Float): Color = when {
    t < 0.8f -> Color(0xFFCC4444)
    t < 1.2f -> Color(0xFF7BB8FF)
    t < 1.8f -> Color(0xFF7BE87B)
    else     -> Color(0xFFFFD700)
}

// ── Countdown ─────────────────────────────────────────────────────────────────

@Composable
private fun CountdownPanel(countDown: Int) {
    Box(modifier = Modifier.fillMaxWidth().height(400.dp), contentAlignment = Alignment.Center) {
        AnimatedContent(
            targetState = countDown,
            transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
            label = "countdown"
        ) { count ->
            Text(count.toString(), fontSize = 140.sp, fontWeight = FontWeight.Black,
                color = Color(0xFFFFD700), textAlign = TextAlign.Center)
        }
    }
}

// ── Playing ───────────────────────────────────────────────────────────────────

@Composable
private fun PlayingPanel(
    vm: RhythmGameViewModel,
    score: Int,
    combo: Int,
    timeSig: Int,
    lastQuality: HitQuality,
    beatIntervalMs: Long,
    lastHitOffset: Long,
    beatsRemaining: Int
) {
    val scope    = rememberCoroutineScope()
    val tapScale = remember { Animatable(1f) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(20.dp))

        // Score bar
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically) {
            ScoreBadge("SCORE",     score.toString(),        Color(0xFFFFD700))
            ScoreBadge("BEATS LEFT", beatsRemaining.toString(), Color(0xFF8080AA))
            ScoreBadge("COMBO",     "×$combo",               Color(0xFFAB7DE0))
        }

        Spacer(Modifier.height(18.dp))

        // ── Beat ring — the core visual cue ───────────────────────────────────
        // The ring fills from 0% → 100% over each beat interval.
        // Tap when the ring is full (gold) for the best score.
        BeatRing(
            beatPulse      = vm.beatPulse,
            beatIntervalMs = beatIntervalMs,
            lastQuality    = lastQuality,
            modifier       = Modifier.size(210.dp)
        )

        Spacer(Modifier.height(10.dp))

        // Quality feedback + early/late hint
        QualityFeedback(lastQuality = lastQuality, lastHitOffset = lastHitOffset)

        Spacer(Modifier.height(18.dp))

        // TAP button
        Button(
            onClick = {
                vm.onScreenTap()
                scope.launch {
                    tapScale.animateTo(0.86f, tween(55))
                    tapScale.animateTo(1f,    tween(95))
                }
            },
            modifier = Modifier.size(150.dp).scale(tapScale.value),
            shape  = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B2D8A))
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("TAP",       fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White)
                Text("the beat",  fontSize = 11.sp, color = Color(0xFFCCAAFF))
            }
        }

        Spacer(Modifier.height(10.dp))
        Text("Tap when the ring is FULL", color = Color(0xFF444466), fontSize = 12.sp)
    }
}

// ── Beat ring ─────────────────────────────────────────────────────────────────
//
// Animates a progress arc that fills over each beat interval.
// Color transitions  purple → amber → gold  as the beat approaches.
// A glow flashes when the beat fires.

@Composable
private fun BeatRing(
    beatPulse:      SharedFlow<Int>,
    beatIntervalMs: Long,
    lastQuality:    HitQuality,
    modifier:       Modifier = Modifier
) {
    val progress  = remember { Animatable(0f) }
    val glowAlpha = remember { Animatable(0f) }

    // Keep a stable reference to the latest interval so the LaunchedEffect
    // (keyed on beatPulse identity, not interval) always uses the current value.
    val currentInterval by rememberUpdatedState(beatIntervalMs)

    LaunchedEffect(beatPulse) {
        var fillJob: kotlinx.coroutines.Job? = null
        beatPulse.collect { _ ->
            // Flash glow
            launch { glowAlpha.snapTo(0.45f); glowAlpha.animateTo(0f, tween(350)) }
            // Restart ring fill
            fillJob?.cancel()
            progress.snapTo(0f)
            if (currentInterval > 0L) {
                fillJob = launch {
                    progress.animateTo(1f, tween(currentInterval.toInt(), easing = LinearEasing))
                }
            }
        }
    }

    val p = progress.value
    val ringColor = when {
        p >= 0.82f -> Color(0xFFFFD700)   // gold  — tap NOW
        p >= 0.58f -> Color(0xFFCC8800)   // amber — nearly there
        else       -> Color(0xFF5B2D8A)   // purple — wait
    }
    val qualityGlow = when (lastQuality) {
        HitQuality.PERFECT -> Color(0xFFFFD700)
        HitQuality.GREAT   -> Color(0xFF7BE87B)
        HitQuality.GOOD    -> Color(0xFF7BB8FF)
        HitQuality.MISS    -> Color(0xFFCC4444)
        HitQuality.NONE    -> Color.Transparent
    }
    val centerSymbol = when (lastQuality) {
        HitQuality.PERFECT -> "✓" to Color(0xFFFFD700)
        HitQuality.GREAT   -> "✓" to Color(0xFF7BE87B)
        HitQuality.GOOD    -> "✓" to Color(0xFF7BB8FF)
        HitQuality.MISS    -> "✗" to Color(0xFFCC4444)
        HitQuality.NONE    -> ""  to Color.Transparent
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val sw = 15.dp.toPx()
            val r  = (size.minDimension - sw) / 2f
            val cx = size.width  / 2f
            val cy = size.height / 2f

            // Beat-fire glow
            if (glowAlpha.value > 0f) {
                drawCircle(Color(0xFFFFD700).copy(alpha = glowAlpha.value * 0.5f),
                    radius = r + sw, center = Offset(cx, cy))
            }
            // Quality result glow inside ring
            if (lastQuality != HitQuality.NONE && qualityGlow != Color.Transparent) {
                drawCircle(qualityGlow.copy(alpha = 0.12f), radius = r - sw / 2, center = Offset(cx, cy))
            }
            // Track (background ring)
            drawCircle(Color(0x22FFFFFF), radius = r, center = Offset(cx, cy), style = Stroke(sw))
            // Progress arc
            if (p > 0.01f) {
                drawArc(
                    color      = ringColor,
                    startAngle = -90f,
                    sweepAngle = p * 360f,
                    useCenter  = false,
                    topLeft    = Offset(cx - r, cy - r),
                    size       = Size(r * 2, r * 2),
                    style      = Stroke(sw, cap = StrokeCap.Round)
                )
            }
        }
        // Centre symbol (result flash)
        if (centerSymbol.first.isNotEmpty()) {
            Text(centerSymbol.first, color = centerSymbol.second, fontSize = 52.sp, fontWeight = FontWeight.Black)
        }
    }
}

// ── Quality feedback row ──────────────────────────────────────────────────────

@Composable
private fun QualityFeedback(lastQuality: HitQuality, lastHitOffset: Long) {
    val (mainText, mainColor) = when (lastQuality) {
        HitQuality.PERFECT -> "PERFECT!" to Color(0xFFFFD700)
        HitQuality.GREAT   -> "GREAT!"   to Color(0xFF7BE87B)
        HitQuality.GOOD    -> "GOOD"     to Color(0xFF7BB8FF)
        HitQuality.MISS    -> "MISS"     to Color(0xFFCC4444)
        HitQuality.NONE    -> ""         to Color.Transparent
    }
    val hint = when {
        lastQuality == HitQuality.NONE || lastQuality == HitQuality.PERFECT -> ""
        lastHitOffset >  80L  -> "a bit late"
        lastHitOffset < -80L  -> "a bit early"
        else                  -> ""
    }

    Column(
        modifier = Modifier.height(52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(visible = lastQuality != HitQuality.NONE,
            enter = fadeIn(tween(80)), exit = fadeOut(tween(300))) {
            Text(mainText, color = mainColor, fontSize = 26.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
        }
        if (hint.isNotEmpty()) {
            Text(hint, color = Color(0xFF6666AA), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ScoreBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF7070AA), fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 26.sp, fontWeight = FontWeight.Black)
    }
}

// ── Result ────────────────────────────────────────────────────────────────────

@Composable
private fun ResultPanel(
    result: com.example.metrognome.viewmodel.GameResult?,
    onDismiss: () -> Unit
) {
    if (result == null) return
    val stars = when {
        result.perfects > result.greats + result.goods + result.misses -> 3
        result.misses   < result.perfects + result.greats              -> 2
        result.score    > 0                                            -> 1
        else                                                           -> 0
    }

    Column(modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(24.dp))
        Text("RESULT", color = Color(0xFFFFD700), fontSize = 20.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
        Spacer(Modifier.height(10.dp))
        Row {
            repeat(3) { i ->
                Text(if (i < stars) "★" else "☆", fontSize = 44.sp,
                    color = if (i < stars) Color(0xFFFFD700) else Color(0x33FFFFFF))
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text("${result.score}", color = Color.White, fontSize = 60.sp, fontWeight = FontWeight.Black)
        Text("points", color = Color(0xFF8080AA), fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Surface(color = Color(0xFF1A1838), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                ResultRow("Perfect",   "${result.perfects}", Color(0xFFFFD700))
                ResultRow("Great",     "${result.greats}",   Color(0xFF7BE87B))
                ResultRow("Good",      "${result.goods}",    Color(0xFF7BB8FF))
                ResultRow("Miss",      "${result.misses}",   Color(0xFFCC4444))
                ResultRow("Max Combo", "×${result.maxCombo}", Color(0xFFAB7DE0))
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B2D8A)),
            shape  = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text("PLAY AGAIN", fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFFCCCCEE))
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}
