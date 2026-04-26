package com.example.metrognome.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.components.UnlockCelebrationOverlay
import com.example.metrognome.viewmodel.GamePhase
import com.example.metrognome.viewmodel.HitQuality
import com.example.metrognome.viewmodel.NoteState
import com.example.metrognome.viewmodel.RenderNote
import com.example.metrognome.viewmodel.RhythmGameViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch

// ── Difficulty definitions ─────────────────────────────────────────────────────

private data class Difficulty(val name: String, val bpm: Int, val beats: Int, val desc: String)

private val difficulties = listOf(
    Difficulty("Beginner", 60, 16, "60 BPM · 16 beats · slow & steady"),
    Difficulty("Easy", 80, 24, "80 BPM · 24 beats · getting into the groove"),
    Difficulty("Medium", 100, 32, "100 BPM · 32 beats · the classic challenge"),
    Difficulty("Hard", 130, 32, "130 BPM · 32 beats · quick reflexes needed"),
    Difficulty("Expert", 160, 48, "160 BPM · 48 beats · for seasoned rhythmists"),
)

// ── Root screen ────────────────────────────────────────────────────────────────

@Composable
fun RhythmGameScreen(
    vm: RhythmGameViewModel,
    isMetronomePlaying: Boolean = false,
    onStopMetronome: () -> Unit = {}
) {
    val phase by vm.phase.collectAsStateWithLifecycle()
    val score by vm.score.collectAsStateWithLifecycle()
    val combo by vm.combo.collectAsStateWithLifecycle()
    val countDown by vm.countDown.collectAsStateWithLifecycle()
    val currentBeat by vm.currentBeat.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val lastQuality by vm.lastQuality.collectAsStateWithLifecycle()
    val result by vm.result.collectAsStateWithLifecycle()
    val useMic by vm.useMic.collectAsStateWithLifecycle()
    val lastHitOffset by vm.lastHitOffset.collectAsStateWithLifecycle()
    val beatsRemaining by vm.beatsRemaining.collectAsStateWithLifecycle()
    val tolerance by vm.tolerance.collectAsStateWithLifecycle()
    val highScores by vm.highScores.collectAsStateWithLifecycle()
    val visibleNotes by vm.visibleNotes.collectAsStateWithLifecycle()

    val unlockQueue = remember { mutableStateListOf<com.example.metrognome.ui.components.metro_items.MetroItemEntry>() }
    LaunchedEffect(vm) {
        vm.newlyUnlocked.collect { entry -> unlockQueue.add(entry) }
    }

    val context = LocalContext.current
    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED
        )
    }
    val micLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            micGranted = granted
            if (granted) vm.toggleMic(true)
        }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0D0B1E))
        .statusBarsPadding()) {
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {
            when (phase) {
                GamePhase.IDLE -> IdlePanel(
                    vm = vm, useMic = useMic, micGranted = micGranted,
                    onRequestMic = { micLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    tolerance = tolerance,
                    onToleranceChange = { vm.setTolerance(it) },
                    isMetronomePlaying = isMetronomePlaying,
                    onStopMetronome = onStopMetronome,
                    highScores = highScores
                )

                GamePhase.COUNTDOWN -> CountdownPanel(countDown)
                GamePhase.PLAYING -> PlayingPanel(
                    vm = vm,
                    score = score,
                    combo = combo,
                    currentBeat = currentBeat,
                    timeSig = timeSig,
                    lastQuality = lastQuality,
                    lastHitOffset = lastHitOffset,
                    beatsRemaining = beatsRemaining,
                    visibleNotes = visibleNotes
                )

                GamePhase.RESULT -> ResultPanel(result = result, onDismiss = { vm.dismissResult() })
            }
        }
        AdBannerView(modifier = Modifier.fillMaxWidth())
    }

    unlockQueue.firstOrNull()?.let { entry ->
        UnlockCelebrationOverlay(
            entry = entry,
            onDismiss = { vm.markCelebrated(entry.item.id); unlockQueue.removeAt(0) },
        )
    }
    } // close outer Box
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
    onStopMetronome: () -> Unit,
    highScores: Map<String, Int> = emptyMap()
) {
    @Suppress("UNUSED_VALUE")
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showTolerance by remember { mutableStateOf(false) }

    if (pendingStart != null) {
        AlertDialog(
            onDismissRequest = { pendingStart = null },
            title = { Text("Metronome is running") },
            text = { Text("Stop it before starting the game, or let it keep playing in the background?") },
            confirmButton = {
                TextButton(onClick = {
                    onStopMetronome()
                    pendingStart?.invoke()
                    pendingStart = null
                }) {
                    Text("Stop & Play")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingStart?.invoke()
                    pendingStart = null
                }) {
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

        Text(
            "RHYTHM GAME", color = Color(0xFFFFD700), fontSize = 22.sp,
            fontWeight = FontWeight.Black, letterSpacing = 3.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Notes fall from the top · tap when they hit the line!",
            color = Color(0xFF7070AA), fontSize = 13.sp, textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(22.dp))

        difficulties.forEach { d ->
            DifficultyCard(
                difficulty = d,
                bestScore = highScores[d.name] ?: 0,
                onClick = {
                    val start = { vm.setDifficulty(d.bpm, d.beats, d.name); vm.startGame() }
                    if (isMetronomePlaying) {
                        pendingStart = start
                    } else start()
                }
            )
            Spacer(Modifier.height(10.dp))
        }

        Spacer(Modifier.height(14.dp))

        // ── Timing tolerance ─────────────────────────────────────────────────
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
                    Text(
                        "Difficulty", color = Color(0xFFCCCCEE),
                        fontWeight = FontWeight.Medium, fontSize = 14.sp
                    )
                    Text(
                        toleranceLabel(tolerance),
                        color = toleranceLabelColor(tolerance),
                        fontSize = 12.sp
                    )
                }
                Text(if (showTolerance) "▲" else "▼", color = Color(0xFF8080AA), fontSize = 12.sp)
            }
        }

        if (showTolerance) {
            Spacer(Modifier.height(6.dp))
            Surface(
                color = Color(0x221A1A3A),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val perfMs = (50 * tolerance).toInt()
                    val goodMs = (100 * tolerance).toInt()
                    val almostMs = (150 * tolerance).toInt()
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
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        WindowBadge("PERFECT", "±${perfMs}ms", Color(0xFFFFD700))
                        WindowBadge("GOOD", "±${goodMs}ms", Color(0xFF7BE87B))
                        WindowBadge("ALMOST", "±${almostMs}ms", Color(0xFF7BB8FF))
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))

        // ── Mic mode ─────────────────────────────────────────────────────────
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = if (useMic) Color(0xFF1A1F3A) else Color(0xFF1A1838),
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.5.dp,
                    color = if (useMic) Color(0xFFFFD700) else Color(0xFF2A2860),
                    shape = RoundedCornerShape(16.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (useMic) Color(0xFFFFD700) else Color(0xFF6060AA),
                        modifier = Modifier
                            .size(36.dp)
                            .padding(end = 14.dp)
                    )
                    Column {
                        Text(
                            "Play With Sound",
                            color = if (useMic) Color(0xFFFFD700) else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "Clap, tap a surface, or play your instrument. The mic detects the beat so you don't need to touch the screen.",
                            color = Color(0xFF8080AA),
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = useMic,
                    onCheckedChange = { on ->
                        vm.toggleMic(on)
                        if (on && !micGranted) onRequestMic()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFFFFD700),
                        checkedTrackColor = Color(0xFF5B2D8A)
                    )
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DifficultyCard(difficulty: Difficulty, bestScore: Int, onClick: () -> Unit) {
    Surface(
        onClick = onClick, shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A1838), modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    difficulty.name,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Text(difficulty.desc, color = Color(0xFF7070AA), fontSize = 12.sp)
            }
            if (bestScore > 0) {
                Column(
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier.padding(end = 12.dp)
                ) {
                    Text(
                        "BEST", color = Color(0xFF6060AA), fontSize = 10.sp,
                        fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                    Text(
                        "$bestScore", color = Color(0xFFFFD700), fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            Text("▶", color = Color(0xFFFFD700), fontSize = 18.sp)
        }
    }
}

@Composable
private fun WindowBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(value, color = Color(0xFFCCCCEE), fontSize = 12.sp)
    }
}

private fun toleranceLabel(t: Float) = when {
    t < 0.8f -> "Strict (Pro)"
    t < 1.2f -> "Normal"
    t < 1.8f -> "Easy (default)"
    else -> "Very Easy"
}

private fun toleranceLabelColor(t: Float): Color = when {
    t < 0.8f -> Color(0xFFCC4444)
    t < 1.2f -> Color(0xFF7BB8FF)
    t < 1.8f -> Color(0xFF7BE87B)
    else -> Color(0xFFFFD700)
}

// ── Countdown ─────────────────────────────────────────────────────────────────

@Composable
private fun CountdownPanel(countDown: Int) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Get ready!", color = Color(0xFF8080AA), fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))
            AnimatedContent(
                targetState = countDown,
                transitionSpec = { scaleIn() + fadeIn() togetherWith scaleOut() + fadeOut() },
                label = "countdown"
            ) { count ->
                Text(
                    count.toString(), fontSize = 140.sp, fontWeight = FontWeight.Black,
                    color = Color(0xFFFFD700), textAlign = TextAlign.Center
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("Tap when the note hits the line", color = Color(0xFF5B2D8A), fontSize = 14.sp)
        }
    }
}

// ── Playing ───────────────────────────────────────────────────────────────────

@Composable
private fun PlayingPanel(
    vm: RhythmGameViewModel,
    score: Int,
    combo: Int,
    currentBeat: Int,
    timeSig: Int,
    lastQuality: HitQuality,
    lastHitOffset: Long,
    beatsRemaining: Int,
    visibleNotes: List<RenderNote>
) {
    val scope = rememberCoroutineScope()
    val tapScale = remember { Animatable(1f) }
    val useMic by vm.useMic.collectAsStateWithLifecycle()
    val micAmplitude by vm.micAmplitude.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(14.dp))

            // ── Score bar ────────────────────────────────────────────────────
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ScoreBadge("SCORE", score.toString(), Color(0xFFFFD700))
                ScoreBadge("BEATS LEFT", beatsRemaining.toString(), Color(0xFF8080AA))
                ScoreBadge("COMBO", "×$combo", Color(0xFFAB7DE0))
            }

            Spacer(Modifier.height(10.dp))

            // Beat position dots
            BeatDotsRow(currentBeat = currentBeat, timeSig = timeSig)

            // Mic equaliser — only visible when microphone mode is active
            if (useMic) {
                Spacer(Modifier.height(8.dp))
                MicEqualizer(
                    amplitude = micAmplitude,
                    lastQuality = lastQuality,
                    micDetected = vm.micDetected,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(36.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            // Note highway — driven by ViewModel's pre-computed render list
            NoteHighway(
                visibleNotes = visibleNotes,
                lastQuality = lastQuality,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            // Quality feedback
            QualityFeedback(lastQuality = lastQuality, lastHitOffset = lastHitOffset)

            Spacer(Modifier.height(4.dp))

            // TAP button
            Button(
                onClick = {
                    vm.onScreenTap()
                    scope.launch {
                        tapScale.animateTo(0.84f, tween(50))
                        tapScale.animateTo(1f, tween(90))
                    }
                },
                modifier = Modifier
                    .size(110.dp)
                    .scale(tapScale.value),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B2D8A))
            ) {
                Text(
                    "TAP",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = { vm.stopGame() },
                modifier = Modifier
                    .fillMaxWidth(0.68f)
                    .height(46.dp),
                shape = RoundedCornerShape(23.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFFF6666)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF882222))
            ) {
                Icon(
                    imageVector = Icons.Filled.StopCircle,
                    contentDescription = "Stop game",
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "STOP GAME",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 1.5.sp
                )
            }

            Spacer(Modifier.height(10.dp))
        }
    }
}

// ── Beat position dots ────────────────────────────────────────────────────────

@Composable
private fun BeatDotsRow(currentBeat: Int, timeSig: Int) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until timeSig) {
            val isActive = i == currentBeat
            val isAccent = i == 0
            val dotSize = if (isAccent) 14.dp else 10.dp
            val dotColor = when {
                isActive && isAccent -> Color(0xFFFFD700)
                isActive -> Color(0xFF9B5DE5)
                isAccent -> Color(0xFF5B3D00)
                else -> Color(0xFF2A2845)
            }
            Box(
                modifier = Modifier
                    .size(dotSize)
                    .clip(CircleShape)
                    .background(dotColor)
                    .then(
                        if (isActive) Modifier.border(
                            1.5.dp,
                            dotColor.copy(alpha = 0.5f),
                            CircleShape
                        ) else Modifier
                    )
            )
        }
    }
}

// ── Mic Equaliser ─────────────────────────────────────────────────────────────
//
// Scrolling bar graph driven by the live microphone RMS amplitude.
// Acts like a mini audio spectrum analyser — bars shift left each frame.
// When a hit is detected (lastQuality changes to non-NONE), the bars flash
// briefly in the quality colour so the developer/player can see the trigger.

private const val EQ_BARS = 30

@Composable
private fun MicEqualizer(
    amplitude: Float,
    lastQuality: HitQuality,
    micDetected: SharedFlow<Unit>,  // fires on every mic trigger, scored or not
    modifier: Modifier = Modifier
) {
    // Ring buffer of recent amplitude readings
    val history = remember {
        mutableStateListOf<Float>().also { list -> repeat(EQ_BARS) { list.add(0f) } }
    }

    // Push the latest amplitude sample into the scrolling buffer
    LaunchedEffect(amplitude) {
        history.removeAt(0)
        history.add(amplitude)
    }

    // Quality flash — fires when a detection was SCORED (gold/green/blue/red)
    val qualityFlashColor = when (lastQuality) {
        HitQuality.PERFECT -> Color(0xFFFFD700)
        HitQuality.GOOD -> Color(0xFF7BE87B)
        HitQuality.ALMOST -> Color(0xFF7BB8FF)
        HitQuality.MISS -> Color(0xFFCC4444)
        HitQuality.NONE -> Color.Transparent
    }
    val qualityAlpha = remember { Animatable(0f) }
    LaunchedEffect(lastQuality) {
        if (lastQuality != HitQuality.NONE) {
            qualityAlpha.snapTo(0.95f)
            qualityAlpha.animateTo(0f, tween(500))
        }
    }

    // Raw-detection flash — fires on every mic trigger (white), including out-of-time ones.
    // Shows the user that the mic DID hear something even if timing was wrong.
    val rawAlpha = remember { Animatable(0f) }
    LaunchedEffect(micDetected) {
        micDetected.collect {
            rawAlpha.snapTo(0.6f)
            rawAlpha.animateTo(0f, tween(250))
        }
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Canvas(modifier = Modifier
            .weight(1f)
            .fillMaxHeight()) {
            val totalGap = (EQ_BARS - 1) * 2.dp.toPx()
            val barW = (size.width - totalGap) / EQ_BARS
            val maxH = size.height

            // Determine the active overlay colour:
            // Quality flash wins over raw flash when both are active.
            val qA = qualityAlpha.value
            val rA = rawAlpha.value
            val activeFlashColor = when {
                qA > 0.01f -> qualityFlashColor
                rA > 0.01f -> Color.White
                else -> Color.Transparent
            }
            val activeFlashAlpha = if (qA > 0.01f) qA else rA

            history.forEachIndexed { i, amp ->
                // Amplify so quiet claps still show; clamp at max
                val barH = (amp * maxH * 8f).coerceAtMost(maxH).coerceAtLeast(2.dp.toPx())
                val x = i * (barW + 2.dp.toPx())

                // Base colour: dark purple (silence) → purple → gold (loud)
                val baseColor = when {
                    amp > 0.20f -> Color(0xFFFFD700)
                    amp > 0.06f -> Color(0xFFAB7DE0)
                    else -> Color(0xFF2D1F50)
                }

                val barColor =
                    if (activeFlashAlpha > 0.01f && activeFlashColor != Color.Transparent)
                        Color(
                            red = baseColor.red * (1f - activeFlashAlpha) + activeFlashColor.red * activeFlashAlpha,
                            green = baseColor.green * (1f - activeFlashAlpha) + activeFlashColor.green * activeFlashAlpha,
                            blue = baseColor.blue * (1f - activeFlashAlpha) + activeFlashColor.blue * activeFlashAlpha,
                            alpha = 1f
                        )
                    else baseColor

                drawRect(
                    color = barColor,
                    topLeft = Offset(x, maxH - barH),
                    size = Size(barW, barH)
                )
            }
        }

        // Label
        Text(
            "MIC",
            color = Color(0xFF3A2A60),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
        )
    }
}

// ── Note Highway ──────────────────────────────────────────────────────────────
//
// Guitar Hero-style falling note lane.
//
// Notes are driven entirely by the ViewModel's [visibleNotes] list, which
// contains pre-computed progress values derived from the global clock:
//
//   progress = (songTimeMs - spawnTimeMs) / NOTE_TRAVEL_MS
//   0.0 → just spawned (top of lane)
//   1.0 → at hit line — tap for PERFECT
//   >1.0 → past hit line (missed, shown red briefly)
//
// Position is render-only; hit detection is time-based in the ViewModel.

@Composable
private fun NoteHighway(
    visibleNotes: List<RenderNote>,
    lastQuality: HitQuality,
    modifier: Modifier = Modifier
) {
    // Quality glow at the hit line
    val hitLineColor = when (lastQuality) {
        HitQuality.PERFECT -> Color(0xFFFFD700)
        HitQuality.GOOD -> Color(0xFF7BE87B)
        HitQuality.ALMOST -> Color(0xFF7BB8FF)
        HitQuality.MISS -> Color(0xFFCC4444)
        HitQuality.NONE -> Color(0xFF3A2A60)
    }

    Box(modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val laneW = size.width
            val laneH = size.height
            val cx = laneW / 2f
            val hitY = laneH * 0.88f
            val noteR = 26.dp.toPx()
            val lineW = 3.dp.toPx()

            val railX1 = cx - noteR * 1.6f
            val railX2 = cx + noteR * 1.6f

            // Lane guide lines
            drawLine(Color(0xFF1A1838), Offset(railX1, 0f), Offset(railX1, laneH), lineW * 0.5f)
            drawLine(Color(0xFF1A1838), Offset(railX2, 0f), Offset(railX2, laneH), lineW * 0.5f)

            // Hit zone glow
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, hitLineColor.copy(alpha = 0.08f)),
                    startY = hitY - noteR * 2,
                    endY = hitY + noteR
                ),
                topLeft = Offset(railX1 - noteR, hitY - noteR * 2),
                size = Size(railX2 - railX1 + noteR * 2, noteR * 3)
            )

            // Hit line
            drawLine(
                color = hitLineColor,
                start = Offset(railX1 - noteR * 0.5f, hitY),
                end = Offset(railX2 + noteR * 0.5f, hitY),
                strokeWidth = lineW,
                cap = StrokeCap.Round
            )
            drawCircle(
                hitLineColor,
                radius = lineW * 1.2f,
                center = Offset(railX1 - noteR * 0.5f, hitY)
            )
            drawCircle(
                hitLineColor,
                radius = lineW * 1.2f,
                center = Offset(railX2 + noteR * 0.5f, hitY)
            )

            // Draw notes — position derived from pre-computed progress
            for (note in visibleNotes) {
                val y = note.progress * hitY
                if (y > laneH + noteR) continue   // fully exited lane

                val inHitWindow = note.progress in 0.87f..1.13f
                val isPast = note.state == NoteState.MISSED || note.progress > 1.13f

                val noteColor = when {
                    isPast -> Color(0xFFCC4444)   // missed — red
                    inHitWindow -> Color(0xFFFFD700)   // in window — gold: TAP NOW
                    note.progress > 0.65f -> Color(0xFFCC8800)  // approaching — amber
                    else -> Color(0xFF7B4DB8)   // far — purple
                }
                val glowAlpha = if (inHitWindow) 0.35f else 0.15f

                drawCircle(
                    noteColor.copy(alpha = glowAlpha),
                    radius = noteR * 1.8f,
                    center = Offset(cx, y)
                )
                drawCircle(noteColor, radius = noteR, center = Offset(cx, y))
                drawCircle(
                    Color.White.copy(alpha = 0.18f), radius = noteR * 0.45f,
                    center = Offset(cx - noteR * 0.2f, y - noteR * 0.25f)
                )
            }
        }
    }
}

// ── Quality feedback row ──────────────────────────────────────────────────────

@Composable
private fun QualityFeedback(lastQuality: HitQuality, lastHitOffset: Long) {
    val (mainText, mainColor) = when (lastQuality) {
        HitQuality.PERFECT -> "PERFECT!" to Color(0xFFFFD700)
        HitQuality.GOOD -> "GOOD" to Color(0xFF7BE87B)
        HitQuality.ALMOST -> "ALMOST" to Color(0xFF7BB8FF)
        HitQuality.MISS -> "MISS" to Color(0xFFCC4444)
        HitQuality.NONE -> "" to Color.Transparent
    }
    val hint = when {
        lastQuality == HitQuality.NONE || lastQuality == HitQuality.PERFECT -> ""
        lastQuality == HitQuality.MISS -> "didn't tap in time"
        lastHitOffset > 80L -> "a bit late"
        lastHitOffset < -80L -> "a bit early"
        else -> ""
    }

    Column(
        modifier = Modifier.height(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedVisibility(
            visible = lastQuality != HitQuality.NONE,
            enter = fadeIn(tween(60)), exit = fadeOut(tween(300))
        ) {
            Text(
                mainText,
                color = mainColor,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
        if (hint.isNotEmpty()) {
            Text(hint, color = Color(0xFF5566AA), fontSize = 11.sp)
        }
    }
}

@Composable
private fun ScoreBadge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = Color(0xFF7070AA), fontSize = 10.sp, letterSpacing = 1.sp)
        Text(value, color = color, fontSize = 24.sp, fontWeight = FontWeight.Black)
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
        result.misses == 0 && result.perfects > 0 -> 3
        result.perfects > result.goods + result.almosts + result.misses -> 3
        result.misses < result.perfects + result.goods -> 2
        result.score > 0 -> 1
        else -> 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            "RESULT",
            color = Color(0xFFFFD700),
            fontSize = 20.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 3.sp
        )
        if (result.isNewHighScore) {
            Spacer(Modifier.height(6.dp))
            Surface(color = Color(0xFFFFD700), shape = RoundedCornerShape(8.dp)) {
                Text(
                    "★  NEW BEST  ★", color = Color(0xFF0D0B1E), fontSize = 13.sp,
                    fontWeight = FontWeight.Black, letterSpacing = 2.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row {
            repeat(3) { i ->
                Text(
                    if (i < stars) "★" else "☆", fontSize = 44.sp,
                    color = if (i < stars) Color(0xFFFFD700) else Color(0x33FFFFFF)
                )
                Spacer(Modifier.width(6.dp))
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            "${result.score}",
            color = Color.White,
            fontSize = 60.sp,
            fontWeight = FontWeight.Black
        )
        Text("points", color = Color(0xFF8080AA), fontSize = 14.sp)
        Spacer(Modifier.height(18.dp))
        Surface(
            color = Color(0xFF1A1838),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                ResultRow("Perfect", "${result.perfects}", Color(0xFFFFD700))
                ResultRow("Good", "${result.goods}", Color(0xFF7BE87B))
                ResultRow("Almost", "${result.almosts}", Color(0xFF7BB8FF))
                ResultRow("Miss", "${result.misses}", Color(0xFFCC4444))
                ResultRow("Max Combo", "×${result.maxCombo}", Color(0xFFAB7DE0))
            }
        }
        Spacer(Modifier.height(22.dp))
        Button(
            onClick = onDismiss,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5B2D8A)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) { Text("PLAY AGAIN", fontWeight = FontWeight.Bold, letterSpacing = 2.sp) }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun ResultRow(label: String, value: String, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color(0xFFCCCCEE))
        Text(value, color = color, fontWeight = FontWeight.Bold)
    }
}
