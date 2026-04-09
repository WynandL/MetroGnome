package com.example.metrognome.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.components.GnomeCanvas
import com.example.metrognome.ui.theme.GnomeColors
import com.example.metrognome.viewmodel.MetronomeViewModel

@Composable
fun MetronomeScreen(vm: MetronomeViewModel) {
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val flashOnBeat by vm.flashOnBeat.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val currentBeat by vm.currentBeat.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0D0B1E))
    ) {
        // Beat indicator dots (top)
        BeatIndicatorRow(
            timeSig = timeSig,
            currentBeat = currentBeat,
            isPlaying = isPlaying,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 4.dp)
        )

        // Gnome canvas — takes all remaining space
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            GnomeCanvas(
                bpm = bpm,
                isPlaying = isPlaying,
                beatEvents = vm.beatEvents,
                flashOnBeat = flashOnBeat,
                modifier = Modifier.fillMaxSize()
            )

            // BPM display overlay (centered in canvas, upper area)
            BpmDisplay(
                bpm = bpm,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
            )
        }

        // Controls bar
        ControlsBar(
            bpm = bpm,
            isPlaying = isPlaying,
            onBpmChange = { vm.setBpm(it) },
            onTogglePlay = { vm.togglePlay() },
            onTapTempo = { vm.tapTempo() },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0B1E))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        // AdMob banner
        AdBannerView(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun BeatIndicatorRow(
    timeSig: Int,
    currentBeat: Int,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until timeSig) {
            val isActive = isPlaying && i == currentBeat
            val isAccent = i == 0
            val dotColor by animateColorAsState(
                targetValue = when {
                    isActive && isAccent -> Color(0xFFFFD700)
                    isActive -> Color(0xFFAB7DE0)
                    isAccent -> Color(0x66FFD700)
                    else -> Color(0x33FFFFFF)
                },
                animationSpec = tween(80),
                label = "dotColor"
            )
            Box(
                modifier = Modifier
                    .size(if (isActive) 16.dp else 12.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            if (i < timeSig - 1) Spacer(modifier = Modifier.width(10.dp))
        }
    }
}

@Composable
private fun BpmDisplay(bpm: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0x99000000),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = bpm.toString(),
            style = MaterialTheme.typography.displayLarge.copy(
                fontSize = 72.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-2).sp
            ),
            color = Color.White,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ControlsBar(
    bpm: Int,
    isPlaying: Boolean,
    onBpmChange: (Int) -> Unit,
    onTogglePlay: () -> Unit,
    onTapTempo: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // BPM stepper row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            // Fast decrease (−10)
            BpmButton("-10") { onBpmChange(bpm - 10) }
            Spacer(Modifier.width(6.dp))
            // Fine decrease (−1)
            BpmButton("−") { onBpmChange(bpm - 1) }
            Spacer(Modifier.width(12.dp))
            // Play / pause
            PlayPauseButton(isPlaying = isPlaying, onClick = onTogglePlay)
            Spacer(Modifier.width(12.dp))
            // Fine increase (+1)
            BpmButton("+") { onBpmChange(bpm + 1) }
            Spacer(Modifier.width(6.dp))
            // Fast increase (+10)
            BpmButton("+10") { onBpmChange(bpm + 10) }
        }

        Spacer(Modifier.height(10.dp))

        // Tap tempo button
        FilledTonalButton(
            onClick = onTapTempo,
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("TAP HERE TO SET SPEED", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

@Composable
private fun BpmButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.size(width = 52.dp, height = 44.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}

@Composable
private fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (isPlaying) Color(0xFFCC2233) else Color(0xFF5B2D8A),
        label = "playButtonColor"
    )
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bgColor,
        modifier = Modifier.size(64.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
    }
}
