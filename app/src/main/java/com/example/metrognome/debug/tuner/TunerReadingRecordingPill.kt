package com.example.metrognome.debug.tuner

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
 * DEV ONLY: a small pill that sits over the Tuner tab while [TunerReadingLog] is
 * recording, counting the samples as they land so the developer can see the capture is
 * live from the page it happens on, and stop it there (a tap stops the recording; the
 * samples stay for the Reading Log viewer). Composes to nothing when not recording, which
 * a release build always is, since only the dev tools can start one; it needs no debug
 * gate of its own. Same shape as the Chords tab's Chord Loop pill, on purpose.
 */
@Composable
fun TunerReadingRecordingPill(modifier: Modifier = Modifier) {
    val recording by TunerReadingLog.isRecording.collectAsStateWithLifecycle()
    if (!recording) return
    val samples by TunerReadingLog.samples.collectAsStateWithLifecycle()

    val pulse by rememberInfiniteTransition(label = "readingRecPulse").animateFloat(
        initialValue = 0.35f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(600, easing = LinearEasing), RepeatMode.Reverse),
        label = "readingRecDot",
    )
    val tint = AppColors.devRed

    Surface(
        modifier = modifier.clickable { TunerReadingLog.stop() },
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
                "Recording readings: ${samples.size} samples. Tap to stop",
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
