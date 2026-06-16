package com.example.metrognome.cloud

/**
 * Master switches for the online (Firestore) reporting layer.
 *
 * One flag per reporter so any single stream can be silenced in any build without
 * touching call sites or engine code. Flip to false to fully disable a stream.
 */
object CloudReportConfig {

    /** Anonymous mic self-test outcome reporting (see [MicCheckReporter]). */
    const val MIC_SELF_TEST_ENABLED = true

    /**
     * Anonymous tuner lock-quality session summaries (see [TunerLockReporter]). One small
     * aggregate document per tuner session — never per lock. Default OFF: this is a
     * diagnostic stream the developer turns on intentionally once the feature ships and
     * real-world lock behaviour is worth gathering.
     */
    const val TUNER_LOCK_ENABLED = false
}
