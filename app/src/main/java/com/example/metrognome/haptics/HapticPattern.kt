package com.example.metrognome.haptics

/**
 * Named haptic patterns. Timings and amplitudes live here — call sites only
 * declare *what* happened, never *how* it should feel.
 *
 * Timing arrays follow the Android waveform convention: alternating off/on
 * durations starting with an off period. Amplitude arrays mirror each segment
 * (0 = silent segment, 1–255 = vibration strength). Lengths must match.
 *
 * To add a new pattern: append an entry below, tune the values, ship.
 */
enum class HapticPattern(
    val timings: LongArray,
    val amplitudes: IntArray,
) {
    /** Very short tap — micro UI feedback (scroll ticks, BPM nudges). */
    TICK(
        timings    = longArrayOf(0, 20),
        amplitudes = intArrayOf(0, 80),
    ),

    /** Standard button or chip tap. */
    CLICK(
        timings    = longArrayOf(0, 35),
        amplitudes = intArrayOf(0, 120),
    ),

    /** Double thud — confirms a long-press was recognized (preset delete, etc.). */
    LONG_PRESS(
        timings    = longArrayOf(0, 40, 50, 65),
        amplitudes = intArrayOf(0, 140, 0, 200),
    ),

    /** Ascending triple pulse — achievement, positive reward, unlock. */
    SUCCESS(
        timings    = longArrayOf(0, 40, 25, 40, 25, 70),
        amplitudes = intArrayOf(0, 100, 0, 160, 0, 220),
    ),

    /** Two heavy pulses — error, rejection, failed action. */
    ERROR(
        timings    = longArrayOf(0, 80, 40, 80),
        amplitudes = intArrayOf(0, 220, 0, 220),
    ),

    /** Strong single thud — destructive confirm, important action. */
    HEAVY_CLICK(
        timings    = longArrayOf(0, 80),
        amplitudes = intArrayOf(0, 255),
    ),
}
