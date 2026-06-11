package com.example.metrognome.ui.components

import android.content.Context
import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.example.metrognome.audio.selftest.SelfTestThresholds
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

/**
 * Shared media-volume gate for any feature that plays a sound through the speaker and listens
 * back through the mic (the Groove Check self-test, the tuner's Microphone/loopback calibration).
 * Such a loopback only works with the media volume up, so callers gate their Start button on it
 * and nudge the user instead of letting the run fail after the fact.
 *
 * Pure system reads - no microphone, no permission needed.
 */

/** Current media-stream volume as a 0..1 fraction. */
fun musicVolumeFraction(am: AudioManager): Float {
    val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    return am.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
}

/**
 * Live media-volume fraction (0..1), re-read every 300ms while [active] (the user can change
 * the volume on the same screen, so we poll rather than snapshot once).
 */
@Composable
fun rememberMediaVolumeFraction(active: Boolean = true): Float {
    val context = LocalContext.current
    val am = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var fraction by remember { mutableFloatStateOf(musicVolumeFraction(am)) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        while (true) {
            fraction = musicVolumeFraction(am)
            delay(300.milliseconds)
        }
    }
    return fraction
}

/** True when media volume is high enough for a mic loopback to work. */
@Composable
fun rememberMediaVolumeOk(active: Boolean = true): Boolean =
    rememberMediaVolumeFraction(active) >= SelfTestThresholds.MIN_VOLUME_FRACTION

/** Short gold prompt shown when media volume is too low for a mic-loopback action. */
@Composable
fun VolumeNudge(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        color = AppColors.gold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        textAlign = TextAlign.Center,
        modifier = modifier,
    )
}
