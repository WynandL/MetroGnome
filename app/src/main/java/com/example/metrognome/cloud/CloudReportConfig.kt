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
}
