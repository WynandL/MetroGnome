package com.example.metrognome.debug.mic

/**
 * Events logged to [MicDiagnosticsBuffer] during a mic session.
 *
 * Debug-only. Delete this file (and all [MicDiagnosticsBuffer] call sites) to remove
 * the diagnostics feature with zero impact on the app.
 */
sealed class MicDiagnosticsEvent {
    abstract val timestampMs: Long

    data class SessionStarted(
        override val timestampMs: Long,
        val source: String,       // "SpeedTrainer" | "RhythmGame"
        val aecActive: Boolean,
    ) : MicDiagnosticsEvent()

    data class SessionEnded(
        override val timestampMs: Long,
    ) : MicDiagnosticsEvent()

    /** Fires when [lastBeatMs] is stamped — BEFORE the audio write. */
    data class BeatFired(
        override val timestampMs: Long,
        val beat: Int,
        val suppressUntilMs: Long,   // end of the suppression window
        val estimatedPlayMs: Long,   // timestampMs + latencyBias = est. speaker output
    ) : MicDiagnosticsEvent()

    /** Onset passed both the suppression filter and the plausibility check. */
    data class OnsetAccepted(
        override val timestampMs: Long,
        val rawDeviationMs: Float,
        val calibratedDeviationMs: Float,
    ) : MicDiagnosticsEvent()

    /** Onset arrived inside the suppression window — dropped by [RhythmDetector]. */
    data class OnsetSuppressed(
        override val timestampMs: Long,
        val suppressUntilMs: Long,
    ) : MicDiagnosticsEvent()

    /** Onset passed the suppression filter but failed the ±500ms plausibility check. */
    data class OnsetRejected(
        override val timestampMs: Long,
        val rawDeviationMs: Float,
    ) : MicDiagnosticsEvent()

    /** One raw-deviation reading collected during the count-in calibration phase. */
    data class CalibrationSample(
        override val timestampMs: Long,
        val rawDeviationMs: Float,
        val sampleIndex: Int,
    ) : MicDiagnosticsEvent()

    /** Median of calibration samples — this value is subtracted from all future deviations. */
    data class CalibrationFinalized(
        override val timestampMs: Long,
        val biasMs: Float,
        val sampleCount: Int,
    ) : MicDiagnosticsEvent()
}
