package com.example.metrognome.ui.screens

import android.view.WindowManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.ModeNight
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import com.example.metrognome.ui.components.metro_items.MetroItem
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.metrognome.ui.components.AdBannerView
import com.example.metrognome.ui.components.GnomeCanvas
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.viewmodel.MetronomeViewModel

@Composable
fun MetronomeScreen(vm: MetronomeViewModel) {
    val bpm by vm.bpm.collectAsStateWithLifecycle()
    val isPlaying by vm.isPlaying.collectAsStateWithLifecycle()
    val flashOnBeat by vm.flashOnBeat.collectAsStateWithLifecycle()
    val timeSig by vm.timeSig.collectAsStateWithLifecycle()
    val currentBeat by vm.currentBeat.collectAsStateWithLifecycle()
    val accentBeat by vm.accentBeat.collectAsStateWithLifecycle()
    val activeItemIds by vm.activeItemIds.collectAsStateWithLifecycle()
    val activeItems = androidx.compose.runtime.remember(activeItemIds) {
        METRO_ITEM_REGISTRY.filter { it.item.id in activeItemIds }.map { it.item }
    }
    val isMuted by vm.isMuted.collectAsStateWithLifecycle()
    val keepScreenOn by vm.keepScreenOn.collectAsStateWithLifecycle()
    var tappedItem by remember { mutableStateOf<MetroItem?>(null) }

    val activity = LocalActivity.current
    DisposableEffect(keepScreenOn) {
        if (keepScreenOn) {
            activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

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
            accentBeat = accentBeat,
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
                accentBeat = accentBeat,
                activeItems = activeItems,
                onItemTapped = { tappedItem = it },
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

        // Utility toggles
        UtilityTogglesRow(
            isMuted = isMuted,
            keepScreenOn = keepScreenOn,
            onToggleMute = { vm.toggleMute() },
            onToggleKeepScreenOn = { vm.setKeepScreenOn(!keepScreenOn) },
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0D0B1E))
                .padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // AdMob banner
        AdBannerView(modifier = Modifier.fillMaxWidth())
    }

    tappedItem?.let { item ->
        AlertDialog(
            onDismissRequest = { tappedItem = null },
            title = {
                Text(
                    item.displayName,
                    color = Color(0xFFFFD700),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(item.earnedMessage, color = Color(0xFFEEEEFF))
            },
            confirmButton = {
                TextButton(onClick = { tappedItem = null }) {
                    Text("Nice!", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                }
            },
            containerColor = Color(0xFF1E1B3A),
            tonalElevation = 0.dp
        )
    }
}

@Composable
private fun BeatIndicatorRow(
    timeSig: Int,
    currentBeat: Int,
    isPlaying: Boolean,
    accentBeat: Int,   // 1-based; 0 = no accent
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        for (i in 0 until timeSig) {
            val isActive = isPlaying && i == currentBeat
            val isAccent = accentBeat > 0 && i == accentBeat - 1
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

internal fun tempoLabel(bpm: Int): String = when {
    bpm < 40  -> "Grave"
    bpm < 60  -> "Largo"
    bpm < 66  -> "Larghetto"
    bpm < 76  -> "Adagio"
    bpm < 108 -> "Andante"
    bpm < 120 -> "Moderato"
    bpm < 156 -> "Allegretto"
    bpm < 176 -> "Allegro"
    bpm < 200 -> "Vivace"
    bpm < 240 -> "Presto"
    else      -> "Prestissimo"
}

@Composable
private fun BpmDisplay(bpm: Int, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0x99000000),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp)
        ) {
            Text(
                text = bpm.toString(),
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 52.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-1).sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                text = tempoLabel(bpm),
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFFFD700),
                letterSpacing = 1.5.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.offset(y = (-6).dp).padding(bottom = 2.dp)
            )
        }
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
            // Fast decrease (−5)
            BpmButton("-5") { onBpmChange(bpm - 5) }
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
            // Fast increase (+5)
            BpmButton("+5") { onBpmChange(bpm + 5) }
        }

        Spacer(Modifier.height(10.dp))

        // Tap tempo button
        FilledTonalButton(
            onClick = onTapTempo,
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = RoundedCornerShape(22.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text("TAP TO SET", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
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

@Composable
private fun UtilityTogglesRow(
    isMuted: Boolean,
    keepScreenOn: Boolean,
    onToggleMute: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UtilityToggle(
            icon = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
            label = if (isMuted) "Unmute" else "Mute",
            active = isMuted,
            onClick = onToggleMute
        )
        UtilityToggle(
            icon = if (keepScreenOn) Icons.Filled.LightMode else Icons.Filled.ModeNight,
            label = "Screen On",
            active = keepScreenOn,
            onClick = onToggleKeepScreenOn
        )
    }
}

@Composable
private fun UtilityToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    val borderColor = if (active) Color(0xFFFFD700) else Color(0x33FFFFFF)
    val bgColor = if (active) Color(0xFF2A1F55) else Color(0xFF1E1B3A)
    val contentColor = if (active) Color(0xFFFFD700) else Color(0x80FFFFFF)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .height(34.dp)
            .clip(shape)
            .background(bgColor)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = contentColor,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = label,
                color = contentColor,
                fontSize = 12.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                letterSpacing = 0.3.sp
            )
        }
    }
}
