package com.example.metrognome.audio.selftest

import android.content.Context

/**
 * The single place every microphone-scoring feature (Speed Trainer, Practice, Rhythm
 * Game) asks the same two questions: "may the user turn mic mode on?" and "what
 * latency should I correct for?". Every consumer reads mic calibration ONLY through
 * here, so the three features can never drift apart in how they gate or correct.
 *
 * There is exactly one user-facing control - the mic toggle in Settings. Every feature
 * reads [isActive] to decide whether to run the mic; nothing has its own toggle.
 *
 * Rules (one place, applied identically everywhere):
 *  - [isCalibrated]  a passing self-test exists, so the toggle MAY be turned on.
 *  - [enabled]       the user's single app-wide opt-in (kept even while disabled).
 *  - [isActive]      the mic should actually run now: [enabled] AND [isCalibrated].
 *  - [isUnsupported] a self-test FAIL is on record (device not a good fit).
 *  - [latencyMs]     the acoustic round-trip to subtract from raw onsets.
 *
 * Calibration is required for real use - there is no dev bypass, so the production flow
 * (turn on -> run check -> use the result) is exercised the same way in a debug build on
 * a real device. The toggle's visibility is gated separately (debug-only for now).
 *
 * This is an immutable snapshot. Read it fresh where you need the current state
 * ([read]); it is intentionally not a flow - state only changes via the Settings toggle
 * / self-test, and consumers re-read when their screen is shown or a session begins.
 */
class MicCalibration private constructor(
    val isCalibrated: Boolean,
    val isUnsupported: Boolean,
    val enabled: Boolean,
    val latencyMs: Float,
) {
    /** The mic should run now: the user turned it on AND a passing calibration exists. */
    val isActive: Boolean get() = enabled && isCalibrated

    companion object {
        fun read(context: Context): MicCalibration {
            val store = SelfTestCalibrationStore(context.applicationContext)
            return MicCalibration(
                isCalibrated = store.isCalibrated,
                isUnsupported = store.lastVerdict == CheckStatus.FAIL,
                enabled = store.micModeEnabled,
                latencyMs = store.latencyMs ?: 0f,
            )
        }
    }
}
