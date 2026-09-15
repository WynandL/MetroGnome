package com.example.metrognome.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.metrognome.audio.tuner.ListeningState
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors

/**
 * The mic-listening indicators the Tuner screen introduced, shared so any other screen
 * that opens the microphone through the tuner engine (the Chord Finder) speaks the same
 * visual language rather than drifting its own copy.
 */

/** One icon per [ListeningState], in the order the engine passes through them. */
private val listeningStateIcons: List<Pair<ListeningState, ImageVector>> = listOf(
    ListeningState.PROFILING to Icons.Filled.Search,
    ListeningState.QUIET     to Icons.Filled.Hearing,
    ListeningState.NOISE     to Icons.Filled.GraphicEq,
    ListeningState.UNSTABLE  to Icons.Filled.RecordVoiceOver,
    ListeningState.ACQUIRING to Icons.Filled.MusicNote,
    ListeningState.LOCKED    to Icons.Filled.Lock,
)

/** The colour a listening state lights up in. */
fun listeningStateColor(state: ListeningState): Color = when (state) {
    ListeningState.LOCKED                          -> GameColors.good
    ListeningState.ACQUIRING                       -> AppColors.gold
    ListeningState.UNSTABLE, ListeningState.NOISE  -> AppColors.warning
    ListeningState.QUIET, ListeningState.PROFILING -> GameColors.rangeBlue
}

/**
 * Row of six state indicator icons. The active state lights up in its own
 * [listeningStateColor]; all others are dimmed. Avoids fast-changing text that is hard
 * to read in a noisy rehearsal environment. A null [state] lights nothing, for when the
 * mic is handed to something else (the drone) or is closed.
 */
@Composable
fun ListeningStateIcons(state: ListeningState?) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listeningStateIcons.forEach { (s, icon) ->
            ListeningStateBadge(icon = icon, color = listeningStateColor(s), active = state == s)
        }
    }
}

/**
 * The same indicator folded into one badge: the active state's icon, crossfading as the
 * state changes, the ear when nothing is lit. For a strip too narrow for all six.
 */
@Composable
fun ListeningStateBadge(state: ListeningState?) {
    Crossfade(targetState = state, animationSpec = tween(220), label = "listeningBadge") { s ->
        val entry = listeningStateIcons.firstOrNull { it.first == s }
        ListeningStateBadge(
            icon = entry?.second ?: Icons.Filled.Hearing,
            color = s?.let(::listeningStateColor) ?: AppColors.textDim,
            active = s != null,
        )
    }
}

@Composable
private fun ListeningStateBadge(icon: ImageVector, color: Color, active: Boolean) {
    val tint by animateColorAsState(
        targetValue = if (active) color else AppColors.textDim.copy(alpha = 0.28f),
        animationSpec = tween(220),
        label = "listeningIconTint",
    )
    val bgAlpha by animateFloatAsState(
        targetValue = if (active) 0.15f else 0f,
        animationSpec = tween(220),
        label = "listeningIconBg",
    )
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(26.dp)
            .background(color.copy(alpha = bgAlpha), CircleShape),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(15.dp),
        )
    }
}

/**
 * The slim input-level bar under the tuner's gauge. [amplitude] is the engine's 0..1
 * level; the x6 gain is what makes a normal playing level fill a useful part of the bar.
 */
@Composable
fun InputLevelMeter(amplitude: Float, modifier: Modifier = Modifier.width(120.dp)) {
    val fill = (amplitude * 6f).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(AppColors.surfaceVariant),
    ) {
        Box(
            Modifier
                .fillMaxWidth(fill)
                .fillMaxHeight()
                .background(AppColors.textAccent),
        )
    }
}
