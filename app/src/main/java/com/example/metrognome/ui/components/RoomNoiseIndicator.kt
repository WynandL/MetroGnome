package com.example.metrognome.ui.components

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/**
 * A small, quiet room-noise indicator for the mic surfaces. While a mic-scored session is running
 * and the room has become *sustainedly* loud (see
 * [com.example.metrognome.audio.rhythm.RoomNoiseMonitor]), a single amber [Icons.Filled.GraphicEq]
 * glyph fades in - the same glyph the Tuner already uses for room noise. Tapping it explains why it
 * is there; that is the indicator's only behaviour.
 *
 * It is deliberately a glyph, not a banner or a text strip: nothing is laid over the gnome, nothing
 * shouts. It never touches detection or scoring - it cannot reject a real clap, it only sets
 * expectations if the player wonders why their timing reads oddly mid-session.
 */
@Composable
fun RoomNoiseIndicator(visible: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(AppColors.warning.copy(alpha = 0.12f))
                .clickable {
                    Toast.makeText(
                        context,
                        "Room sounds loud - your timing may read less accurately",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.GraphicEq,
                contentDescription = "Room is noisy - tap for details",
                tint = AppColors.warning,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}
