package com.example.metrognome.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/**
 * The app's round play/stop key: a [RaisedControl] disc that is purple with a play arrow
 * when idle and red with a stop square while something sounds, crossfading between the
 * two. First built inline for the drone on the Tuner tab; extracted so the Chord Finder's
 * "hear it" is the same control, and any future "start a sound" key is too.
 *
 * [enabled] false dims the key and ignores taps, for a row that has nothing to play yet.
 * The caller decides what a tap means in each state (the drone toggles; "hear it" plays
 * or stops), so there is one [onClick].
 */
@Composable
fun PlayStopKey(
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: Dp = 52.dp,
    playDescription: String = "Play",
    stopDescription: String = "Stop",
) {
    val tint by animateColorAsState(
        targetValue = if (playing) AppColors.danger else AppColors.primaryPurple,
        animationSpec = tween(260),
        label = "playStopTint",
    )
    RaisedControl(
        onClick = { if (enabled) onClick() },
        shape = CircleShape,
        tint = tint,
        modifier = modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.35f),
    ) {
        Icon(
            imageVector = if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) stopDescription else playDescription,
            tint = Color.White,
            modifier = Modifier.size(size / 2),
        )
    }
}
