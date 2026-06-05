package com.example.metrognome.haptics

import android.content.ContentResolver
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings

/**
 * Central haptic feedback engine. Obtain via [LocalHaptics] in Compose, or
 * construct directly where Compose is unavailable.
 *
 * Passing null for [context] produces a no-op instance safe for use in
 * Compose Previews and the [LocalHaptics] default.
 *
 * Gates (all must pass before vibration fires):
 *   1. Device has a vibrator ([Vibrator.hasVibrator])
 *   2. System haptic-feedback setting is enabled
 *
 * Amplitude control is detected at runtime; devices without it fall back
 * to default-amplitude waveforms automatically.
 */
class HapticEngine(context: Context?) {

    private val vibrator: Vibrator? = context?.getSystemService(Vibrator::class.java)
    private val contentResolver: ContentResolver? = context?.contentResolver

    @Suppress("DEPRECATION") // no cross-version replacement for checking this via Vibrator directly
    private val systemHapticsEnabled: Boolean
        get() = contentResolver?.let {
            Settings.System.getInt(it, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
        } ?: false

    /** Fire a named pattern. Timings and amplitudes are defined in [HapticPattern]. */
    fun fire(pattern: HapticPattern) =
        fireWaveform(pattern.timings, pattern.amplitudes)

    /**
     * Fire a fully custom waveform.
     *
     * @param timings    Alternating off/on durations in ms, starting with off.
     * @param amplitudes Per-segment strength 0–255. Pass null to use device default.
     * @param repeat     Index into [timings] at which to loop, or -1 for no repeat.
     */
    @Suppress("unused")
    fun fireCustom(
        timings: LongArray,
        amplitudes: IntArray? = null,
        repeat: Int = -1,
    ) {
        if (amplitudes != null) fireWaveform(timings, amplitudes, repeat)
        else fireTimings(timings, repeat)
    }

    /** Cancel any ongoing vibration immediately. */
    fun cancel() { vibrator?.cancel() }

    // ── Private ───────────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private fun fireWaveform(timings: LongArray, amplitudes: IntArray, repeat: Int = -1) {
        val v = readyVibrator() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = if (v.hasAmplitudeControl()) {
                VibrationEffect.createWaveform(timings, amplitudes, repeat)
            } else {
                VibrationEffect.createWaveform(timings, repeat)
            }
            v.vibrate(effect)
        } else {
            v.vibrate(timings, repeat)
        }
    }

    @Suppress("DEPRECATION")
    private fun fireTimings(timings: LongArray, repeat: Int = -1) {
        val v = readyVibrator() ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createWaveform(timings, repeat))
        } else {
            v.vibrate(timings, repeat)
        }
    }

    private fun readyVibrator(): Vibrator? {
        if (!systemHapticsEnabled) return null
        return vibrator?.takeIf { it.hasVibrator() }
    }
}
