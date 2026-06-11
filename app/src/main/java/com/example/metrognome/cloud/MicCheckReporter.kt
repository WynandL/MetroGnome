package com.example.metrognome.cloud

import android.os.Build
import com.example.metrognome.BuildConfig
import com.example.metrognome.audio.selftest.CheckStatus
import com.example.metrognome.audio.selftest.SelfTestReport

/**
 * Reports the outcome of one user-facing microphone check to Firestore, anonymously.
 *
 * Purpose: give the developer a real-world picture of whether the mic-accuracy
 * feature passes or fails across devices, and whether it is quietly frustrating
 * people (lots of FAIL/ABORT, retries, noisy rooms). With enough anonymous
 * readings, a simple console query summarises pass/fail rates per device/route.
 *
 * Every field is data the [SelfTestReport] engine *already* produces - nothing new
 * is measured here; the report is just passed through to the backend. The mapping,
 * collection name, and auth all live in this `cloud`-package file, completely apart
 * from the mic-accuracy engine, which has no knowledge of reporting.
 *
 * Trigger it once from the user-facing flow when a run resolves (see MicCheckOverlay).
 * Dev-only runs (the engineering diagnostics overlay) deliberately do not call this.
 *
 * Firestore rule (Firebase console):
 *
 *   match /mic_self_test/{doc} {
 *     allow create: if request.auth != null;
 *     allow read, update, delete: if false;
 *   }
 */
object MicCheckReporter {

    private const val COLLECTION = "mic_self_test"

    /** Submit a completed self-test outcome. No-ops on a PENDING (incomplete) report. */
    fun submit(report: SelfTestReport) {
        if (!CloudReportConfig.MIC_SELF_TEST_ENABLED) return
        if (report.verdict == CheckStatus.PENDING) return
        CloudReporter.submit(COLLECTION, buildDocument(report))
    }

    private fun buildDocument(r: SelfTestReport): Map<String, Any?> = mapOf(
        // ── Headline outcome ──
        "verdict"      to r.verdict.name,   // PASS / FAIL / ABORT
        "grade"        to r.grade.name,     // GOOD_FIT / USABLE / NOT_FIT
        "device_model" to r.deviceModel,
        "route"        to r.route.name,

        // ── Build / platform (lets the dev filter their own test device out) ──
        "android_api"      to Build.VERSION.SDK_INT,
        "app_version_code" to BuildConfig.VERSION_CODE,
        "app_version_name" to BuildConfig.VERSION_NAME,
        "debug_build"      to BuildConfig.DEBUG,

        // ── Environment ──
        "environment"            to r.environment.name,
        "ambient_floor"          to r.ambientFloor.cloud(),
        "system_volume_fraction" to r.systemVolumeFraction.cloud(),

        // ── Speaker path (latency constant) ──
        "speaker_path"      to r.speakerPath.name,
        "latency_ms"        to r.latencyMs.cloud(),
        "latency_jitter_ms" to r.latencyJitterMs.cloud(),

        // ── Click vs clap discrimination ──
        "discrimination"    to r.discrimination.name,
        "click_reject_rate" to r.clickRejectRate.cloud(),
        "clap_detect_rate"  to r.clapDetectRate.cloud(),

        // ── Detection reliability ──
        "detection_recall"      to r.detectionRecall.cloud(),
        "out_of_band_recall"    to r.outOfBandRecall.cloud(),
        "masking_half_width_ms" to r.maskingHalfWidthMs.cloud(),
        "false_positives"       to r.falsePositives,

        // ── Scoring accuracy ──
        "scoring"              to r.scoring.name,
        "mean_abs_residual_ms" to r.meanAbsResidualMs.cloud(),
        "p95_abs_residual_ms"  to r.p95AbsResidualMs.cloud(),

        // ── Context ──
        "notes"               to r.notes,
        "report_timestamp_ms" to r.timestampMs,
        "submitted_ms"        to System.currentTimeMillis(),
    )

    /** NaN/Infinity-safe Float -> Double? so Firestore never stores a non-finite value. */
    private fun Float?.cloud(): Double? =
        this?.takeIf { !it.isNaN() && !it.isInfinite() }?.toDouble()
}
