package com.example.metrognome.cloud

import android.os.Build
import com.example.metrognome.BuildConfig
import com.example.metrognome.audio.selftest.CheckStatus
import com.example.metrognome.audio.selftest.SelfTestReport
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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
        "speaker_path"           to r.speakerPath.name,
        "latency_ms"             to r.latencyMs.cloud(),
        "latency_jitter_ms"      to r.latencyJitterMs.cloud(),
        // Reproducibility of the constant across the run - the actual speaker-path trust gate.
        "latency_split_delta_ms" to r.latencyDetail?.splitDeltaMs.cloud(),

        // ── Click vs clap discrimination ──
        "discrimination"    to r.discrimination.name,
        "click_reject_rate" to r.clickRejectRate.cloud(),
        "clap_detect_rate"  to r.clapDetectRate.cloud(),
        // Spectral margins on BOTH acceptance axes (a click can leak via flatness OR ratio):
        // tells "inseparable" (overlap on both) apart from "leaks at default, calibratable"
        // (a gap). A threshold can sit anywhere between click-max and clap-min per axis.
        "click_integrated_max" to r.spectralMargins?.clickIntegratedMax.cloud(),
        "clap_integrated_min"  to r.spectralMargins?.clapIntegratedMin.cloud(),
        "click_flatness_max"   to r.spectralMargins?.clickFlatnessMax.cloud(),
        "clap_flatness_min"    to r.spectralMargins?.clapFlatnessMin.cloud(),
        "click_peak_max"       to r.spectralMargins?.clickPeakMax.cloud(),
        "clap_peak_min"        to r.spectralMargins?.clapPeakMin.cloud(),
        // The two values that actually gate `discrimination` (click_reject_rate /
        // clap_detect_rate above are advisory only, see SelfTestReport.kt).
        "disc_onset_recall_rate"    to r.discOnsetRecallRate.cloud(),
        "disc_separable_fraction"   to r.discSeparableFraction.cloud(),
        // This device's own derived thresholds, applied to the scoring sweep below and
        // persisted on PASS - null means the shipped default was used instead.
        "tuned_clap_band_ratio"     to r.tunedClapBandRatio.cloud(),
        "tuned_clap_flatness_min"   to r.tunedClapFlatnessMin.cloud(),

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
        // Codes only (e.g. "D2") to keep rows short and scannable side by side -
        // see NoteCode for what each one means.
        "notes"               to r.notes.map { it.id },
        // When the test itself finished (MicSelfTest builds the report at this instant).
        // Submission to Firestore follows within milliseconds with no queue or retry in
        // between (see CloudReporter), so a separate "submitted at" would never
        // meaningfully differ - one honest timestamp instead of two identical ones.
        "report_timestamp_ms" to r.timestampMs,
        "report_at"           to r.timestampMs.readableSast(),
    )

    /** NaN/Infinity-safe Float -> Double? so Firestore never stores a non-finite value. */
    private fun Float?.cloud(): Double? =
        this?.takeIf { !it.isNaN() && !it.isInfinite() }?.toDouble()

    /**
     * Human-readable SAST (UTC+2, no DST) timestamp alongside the raw epoch-ms field, for
     * quick scanning - these reports are read from South Africa, not the submitting device's
     * own timezone, so the readable string is fixed to the reader's zone rather than the
     * device's local time.
     */
    private fun Long.readableSast(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("Africa/Johannesburg")
        return fmt.format(Date(this)) + " SAST"
    }
}
