package com.example.metrognome.audio.selftest

/**
 * Result model for the mic self-test.
 *
 * The self-test is a fully device-dependent acoustic loopback: the engine emits
 * its own stimuli through the speaker, hears them back through the mic, and
 * measures how faithfully it can recover a known timing. No human input is
 * involved at any point - every number here is the device proving (or failing
 * to prove) that it can score a player honestly.
 *
 * See [MicSelfTest] for the engine that produces this, and [SelfTestThresholds]
 * for the pass/fail bars.
 */

/** Outcome of a single check. */
enum class CheckStatus { PASS, FAIL, ABORT, PENDING }

/**
 * Graded overall outcome, replacing a binary pass/fail for the *believability* call.
 *
 * The latency constant the test ships is driven by the speaker-path phase and is
 * trustworthy whenever the clock is stable; the scoring sweep only decides whether
 * a player can be *scored* honestly here. That is a confidence judgement, not a
 * threshold, so it is graded:
 *
 *  - [GOOD_FIT]  clock stable and timing lands inside the game's PERFECT window.
 *  - [USABLE]    scoring is fair within the GOOD window, with caveats (e.g. a small
 *                on-beat masking dead zone). Constant is still saved.
 *  - [NOT_FIT]   the device genuinely can't score: unstable clock, no output
 *                timestamp, click/clap inseparable, or detection broken away from
 *                the beat. This is the only outcome that disables the feature.
 *
 * Masking of a clap that lands on top of the click is a physical constant of every
 * speaker/mic, not a device defect, so it is *characterized* (see
 * [SelfTestReport.maskingHalfWidthMs]) and at most demotes GOOD_FIT to USABLE - it
 * never produces NOT_FIT on its own.
 */
enum class GradeLevel { GOOD_FIT, USABLE, NOT_FIT }

/** Which stage of the self-test is running (or last ran). */
enum class SelfTestPhase { IDLE, ENVIRONMENT, SPEAKER_PATH, DISCRIMINATION, SCORING, DONE }

/**
 * One point in the scoring-accuracy sweep: a clap was emitted [injectedMs] from
 * the beat, and the full scoring path reported [reportedMs] (null = the clap was
 * never detected). [residualMs] is the scoring error - the headline believability
 * number.
 */
data class ScoringPoint(
    val injectedMs: Float,
    val reportedMs: Float?,
) {
    val residualMs: Float? get() = reportedMs?.let { it - injectedMs }
}

/**
 * How the speaker-path latency constant was arrived at, for the engineering report.
 *
 * The constant is the median of [used] measured claps (after [warmupClaps] claps
 * were emitted and discarded so the audio path was at steady state, not cold).
 * [firstHalfMs] / [secondHalfMs] are the medians of the first and second half of
 * the measured claps and [splitDeltaMs] is the gap between them - a small gap means
 * the measurement reproduced within the run; a large one means it drifted.
 */
data class LatencyDetail(
    val warmupClaps: Int,
    val emitted: Int,
    val used: Int,
    val firstHalfMs: Float,
    val secondHalfMs: Float,
    val splitDeltaMs: Float,
)

/**
 * Click-vs-clap separation actually measured on this device, for tuning the detector's
 * thresholds from one run. The classifier accepts a clap on EITHER of two axes (spectral
 * flatness OR integrated band ratio), so a click leaks if it beats *either* one - honest
 * margins therefore cover both: the ratio threshold should sit between
 * [clickIntegratedMax] and [clapIntegratedMin], and the flatness threshold between
 * [clickFlatnessMax] and [clapFlatnessMin]. Null click values mean the clicks never even
 * registered as onset candidates (the best possible case, trivially separable).
 * [clickPeakMax]/[clapPeakMin] (the single-hop burst ratio) are diagnostic only - real
 * devices proved they overlap, which is why the burst is not part of the decision.
 */
data class SpectralMargins(
    val clickIntegratedMax: Float? = null,
    val clapIntegratedMin: Float? = null,
    val clickFlatnessMax: Float? = null,
    val clapFlatnessMin: Float? = null,
    val clickPeakMax: Float? = null,
    val clapPeakMin: Float? = null,
)

/**
 * Complete self-test outcome. [verdict] rolls the individual checks into a single
 * PASS / FAIL / ABORT used by the production gate.
 *
 * ABORT (not FAIL) is reserved for conditions the *user* can fix - a noisy room
 * or a muted speaker - so the production flow knows to coach-and-retry rather
 * than disable the feature.
 */
data class SelfTestReport(
    val deviceModel: String,
    val timestampMs: Long,
    val route: AudioRoute,

    // ── Environment ──
    val environment: CheckStatus,
    val ambientFloor: Float,
    val systemVolumeFraction: Float,

    // ── Speaker path (the device latency constant lives here) ──
    val speakerPath: CheckStatus,
    val latencyMs: Float?,
    val latencyJitterMs: Float?,

    // ── Click-vs-clap discrimination ──
    val discrimination: CheckStatus,
    /** Classified-as-clap rate at the *default* thresholds. Advisory only (see [SelfTestThresholds.MIN_CLICK_REJECT_RATE]) - does not gate [discrimination]. */
    val clickRejectRate: Float?,
    /** Classified-as-clap rate at the *default* thresholds. Advisory only (see [SelfTestThresholds.MIN_CLAP_DETECT_RATE]) - does not gate [discrimination]. */
    val clapDetectRate: Float?,
    val spectralMargins: SpectralMargins? = null,
    /**
     * Fraction of claps that registered as ANY onset at all, before classification. The actual
     * hard recall floor behind [discrimination] - see [SelfTestThresholds.MIN_CLAP_ONSET_RECALL].
     */
    val discOnsetRecallRate: Float? = null,
    /**
     * Fraction of registered claps that individually separated from the clicks. The other hard
     * gate behind [discrimination] - see [SelfTestThresholds.SEPARABILITY_MIN_FRACTION]. Null
     * when no clap registered as an onset at all (already covered by [discOnsetRecallRate]).
     */
    val discSeparableFraction: Float? = null,
    /**
     * This run's own per-device integrated-ratio threshold, derived from [spectralMargins] when
     * discrimination was separable - null otherwise (falls back to the shipped default). This is
     * what the scoring sweep below was actually classified with, and what gets persisted on PASS.
     */
    val tunedClapBandRatio: Float? = null,
    /** This run's own per-device flatness threshold - see [tunedClapBandRatio]. */
    val tunedClapFlatnessMin: Float? = null,

    // ── Detection reliability across the scoring sweep ──
    val detectionRecall: Float?,
    val falsePositives: Int,
    /**
     * Recall on claps landing clear of the beat (|offset| beyond the masking band).
     * These have no acoustic excuse to be missed, so this - not overall recall - is
     * the honest detection gate.
     */
    val outOfBandRecall: Float? = null,
    /**
     * Half-width (ms) of the on-beat dead zone: the largest |offset| within the
     * masking band where a clap was missed. 0 means no on-beat masking was seen.
     * A characterization, not a failure (see [GradeLevel]).
     */
    val maskingHalfWidthMs: Float? = null,

    // ── Scoring accuracy ──
    val scoring: CheckStatus,
    val scoringPoints: List<ScoringPoint>,
    val meanAbsResidualMs: Float?,
    val p95AbsResidualMs: Float?,

    /** Diagnostic codes observed this run - see [NoteCode] for what each one means. */
    val notes: List<NoteCode>,

    // ── Engineering detail (defaults keep the early-exit report paths simple) ──
    val latencyDetail: LatencyDetail? = null,
    val discPairs: Int = 0,
    val scoringTrials: Int = 0,
) {
    /**
     * The graded believability outcome. ABORT is reported through [verdict], not here;
     * for an aborted/incomplete run this falls through to [GradeLevel.NOT_FIT] and
     * callers gate on [verdict] == ABORT first.
     */
    val grade: GradeLevel
        get() {
            // The "completely wrong" cases - genuine reasons a device cannot score.
            if (speakerPath != CheckStatus.PASS) return GradeLevel.NOT_FIT      // clock / no timestamp
            if (discrimination != CheckStatus.PASS) return GradeLevel.NOT_FIT   // click/clap inseparable
            val mean = meanAbsResidualMs ?: return GradeLevel.NOT_FIT
            val p95 = p95AbsResidualMs ?: return GradeLevel.NOT_FIT
            val outRecall = outOfBandRecall ?: detectionRecall ?: return GradeLevel.NOT_FIT
            if (outRecall < SelfTestThresholds.OUT_OF_BAND_MIN_RECALL) return GradeLevel.NOT_FIT
            // Too inaccurate to score fairly even against the lenient GOOD window.
            if (p95 > SelfTestThresholds.GRADE_USABLE_P95_MS ||
                mean > SelfTestThresholds.GRADE_USABLE_MEAN_MS
            ) return GradeLevel.NOT_FIT

            // Passing region: a tight, accurate device with a negligible dead zone is a
            // GOOD_FIT; anything else that still clears the bars is USABLE-with-caveats.
            val mask = maskingHalfWidthMs ?: 0f
            val tightEnough = p95 <= SelfTestThresholds.GRADE_GOOD_P95_MS &&
                mean <= SelfTestThresholds.GRADE_GOOD_MEAN_MS &&
                mask <= SelfTestThresholds.GRADE_GOOD_MASK_MS
            return if (tightEnough) GradeLevel.GOOD_FIT else GradeLevel.USABLE
        }

    /**
     * Coarse verdict kept for the calibration store and the abort path. GOOD_FIT and
     * USABLE both save the constant (PASS); NOT_FIT records a failure; an environment
     * abort stays a transient ABORT.
     */
    val verdict: CheckStatus
        get() = when {
            environment == CheckStatus.ABORT -> CheckStatus.ABORT
            environment == CheckStatus.PENDING && speakerPath == CheckStatus.PENDING -> CheckStatus.PENDING
            grade == GradeLevel.NOT_FIT -> CheckStatus.FAIL
            else -> CheckStatus.PASS
        }
}

/**
 * Pass/fail bars for each check. Deliberately collected in one place and named:
 * the first real-device runs will move these, and they should move *here*, not
 * scattered through the engine.
 *
 * Starting values are intentionally honest rather than generous - a feature that
 * scores a musician should not ship on a device that can only *just* clear the
 * bar.
 */
object SelfTestThresholds {

    /**
     * Version of the pass/fail rubric (these thresholds plus the gate logic in [MicSelfTest]
     * and [SelfTestReport.grade]). Bump it whenever a change could flip a device's outcome.
     *
     * A stored FAIL is only trusted if it was produced by the *current* rubric: a device that
     * failed an older, stricter version is reported as never tested, so it is offered the check
     * again instead of being remembered as unfit forever. A PASS is never invalidated this way -
     * its latency constant demonstrably worked, and tightening the bars retroactively would
     * punish users whose scoring is fine.
     *
     *  1 - v5.11 original gates (hard jitter FAIL, reject-rate discrimination FAIL).
     *  2 - jitter and split-drift demoted to notes; discrimination fails only on genuine
     *      inseparability across BOTH spectral axes (ratio and flatness); tuned clap
     *      ratio + flatness thresholds persisted (2026-07-02).
     *  3 - discrimination's clap-side gate now mirrors the click-side one: a low
     *      *classified* clap-detect rate is a calibration offset (noted, not failed) as
     *      long as claps are separable AND actually registering as onsets at all; only a
     *      genuine acoustic miss (below [MIN_CLAP_ONSET_RECALL] before classification even
     *      applies) still fails discrimination (2026-07-27).
     *  4 - the scoring phase is graded against onsets reclassified with THIS run's own
     *      just-derived per-device thresholds (not the shipped defaults it ran capture
     *      with), so a device is judged on what it will actually do once calibrated, not
     *      on its out-of-the-box fit; [SEPARABILITY_MIN_FRACTION] tolerates ~10% outlier
     *      claps instead of one stray sample branding a device inseparable forever;
     *      [MIN_CLAP_ONSET_RECALL] and [OUT_OF_BAND_MIN_RECALL] loosened from a
     *      zero-tolerance 0.95 (effectively demanding a perfect 12/12) to 0.85 (one miss
     *      tolerated), since a hard sample size of 12 makes "perfect" a fragile bar
     *      (2026-07-27).
     */
    const val GATE_LOGIC_VERSION = 4


    // ── Environment (ABORT, not FAIL - user-fixable) ──
    /**
     * Above this learned ambient floor (normalised RMS) the room is too loud to test.
     * Loosened from 0.060, then from 0.10: a moderately noisy room is fine because loud
     * playback + the spectral clap/click discrimination tolerate broadband background
     * noise; demanding near-silence just frustrates the user.
     */
    const val MAX_AMBIENT_FLOOR = 0.13f
    /**
     * At or above this fraction of [MAX_AMBIENT_FLOOR] is *surfaced as a note*, not failed:
     * the room passed, but is close enough to the ceiling that a slightly noisier session
     * next time could tip it into an ABORT. A clean PASS with nothing near a limit stays
     * silent; this is what lets a good run still flag "passed, but by a hair."
     */
    const val AMBIENT_CEILING_WARN_FRACTION = 0.9f
    /**
     * Below this fraction of max system volume the speaker is too quiet to hear back.
     * Raised from 0.30: a loud, clean playback matters far more to a reliable loopback
     * than a perfectly quiet room, so we ask for a genuinely high volume.
     */
    const val MIN_VOLUME_FRACTION = 0.60f
    /**
     * At or below this fraction ABOVE [MIN_VOLUME_FRACTION] is *surfaced as a note*: the
     * volume cleared the floor, but with little headroom. Mirrors [AMBIENT_CEILING_WARN_FRACTION]
     * for the opposite kind of limit (a floor instead of a ceiling).
     */
    const val VOLUME_FLOOR_WARN_FRACTION = 1.1f

    // ── Speaker path ──
    /** Plausible acoustic round-trip (emission→capture). Outside → unreliable clock. */
    const val MIN_LATENCY_MS = 2f
    const val MAX_LATENCY_MS = 400f
    /**
     * Per-click latency spread above this is *surfaced as a note*, not failed. The scoring
     * sweep runs on the derived latency constant and is the real proof it works; and as a
     * stddev over <=8 claps this number is outlier-dominated anyway (a device measuring
     * "42 ms jitter" here produced 5 ms scoring residuals).
     */
    const val MAX_LATENCY_JITTER_MS = 15f
    /**
     * First-half vs second-half latency medians disagreeing by more than this is *surfaced
     * as a note*, not failed. A constant that actually drifted shows up directly in the
     * scoring residuals (the residual includes the constant), which the grade bars already
     * bound - and medians of 4-vs-4 claps are too fragile to be a hard gate. The only hard
     * speaker-path failures are: no output timestamp, implausible latency, too few claps.
     */
    const val MAX_LATENCY_SPLIT_DRIFT_MS = 8f
    /**
     * Latency at or above this fraction of [MAX_LATENCY_MS] is *surfaced as a note*: the
     * device still technically clears the hard ceiling, but is close enough to it that the
     * fit is marginal (a device sitting at 395/400 ms is not the same confidence level as
     * one at 60/400 ms).
     */
    const val LATENCY_CEILING_WARN_FRACTION = 0.9f

    // ── Discrimination ──
    /**
     * Reject/detect rate below this on either side is *advisory*, not a failure: it only
     * means the shipped detector thresholds sit wrong for this hardware on that axis.
     * Discrimination fails only when either (a) some clap is genuinely inseparable from the
     * clicks on EITHER axis (its flatness and its band ratio both sit at or below the clicks'
     * maxima in [SpectralMargins]), so no threshold pair can accept it without also leaking
     * clicks, or (b) claps aren't registering as onsets at all - see [MIN_CLAP_ONSET_RECALL].
     * A low [MIN_CLICK_REJECT_RATE] (click leaks through) or low [MIN_CLAP_DETECT_RATE] (clap
     * gets classified as non-clap) alone, on an otherwise-separable device, is a calibration
     * target - fixed by the per-device thresholds saved on PASS - not an unfit device.
     */
    const val MIN_CLICK_REJECT_RATE = 0.95f
    /** Claps classified as "clap" by the *default* detector below this: informational only (see above). */
    const val MIN_CLAP_DETECT_RATE = 0.95f
    /**
     * Claps registering as ANY onset at all (before the clap/click classifier even runs)
     * below this means the mic genuinely isn't hearing them - no threshold tuning fixes a
     * clap that was never captured as an onset in the first place. This is the one hard
     * recall floor for discrimination.
     *
     * Set to tolerate exactly one miss out of [MicSelfTest]'s 12 discrimination pairs
     * (11/12 = 0.917 passes, 10/12 = 0.833 fails) rather than demanding a literally
     * perfect run: requiring zero misses across only 12 samples is fragile to one stray
     * noise burst or vibration, and would fail a device for hardware noise rather than
     * genuine insensitivity.
     */
    const val MIN_CLAP_ONSET_RECALL = 0.85f
    /**
     * Fraction of registered claps that must individually separate from the clicks (beat
     * the clicks' maximum on at least one axis) for the device to count as separable.
     * Below 1.0 by design: a single outlier clap - one noisy sample out of a small run -
     * should not alone brand an otherwise-workable device "inseparable" forever. The
     * per-device threshold placement ([MicSelfTest.deviceClapBandRatio] /
     * [MicSelfTest.deviceClapFlatnessMin]) already degrades gracefully when the "gap" this
     * implies is thin or absent, so tolerating a minority of outliers here doesn't produce
     * a bad threshold - it just stops one fluke from being treated as proof of a hardware
     * limit.
     */
    const val SEPARABILITY_MIN_FRACTION = 0.9f
    /**
     * Ratio axis: the clap minimum must clear the click maximum by at least this multiple
     * (ratios are multiplicative, per [SpectralMargins]) to count as a *comfortable* margin
     * rather than a thin one - see [MicSelfTest.hasComfortableMargin].
     */
    const val RATIO_MARGIN_COMFORT_FACTOR = 2.0f
    /**
     * Flatness axis: the clap minimum must clear the click maximum by at least this much
     * (flatness is additively bounded 0..1) to count as comfortable rather than thin.
     */
    const val FLATNESS_MARGIN_COMFORT = 0.10f

    // ── Detection ──
    /**
     * Recall required on claps clear of the beat (|offset| beyond [MASKING_BAND_MS]).
     * These cannot be excused by masking, so falling short here is a real detection
     * failure and the one recall bar that produces NOT_FIT. Measured against onsets
     * reclassified with THIS run's own derived per-device thresholds where discrimination
     * was separable ([MicSelfTest] passes them into the scoring phase), so a device is
     * graded on what it will actually do once calibrated - not on the shipped defaults it
     * is about to stop using. Set to tolerate one miss out of 12 out-of-band trials
     * (11/12 = 0.917 passes), matching [MIN_CLAP_ONSET_RECALL]'s reasoning: demanding a
     * perfect run from only 12 samples fails a device for noise, not incapability.
     */
    const val OUT_OF_BAND_MIN_RECALL = 0.85f
    /** |offset| within this is masking-susceptible; misses here characterize the dead zone rather than fail. */
    const val MASKING_BAND_MS = 40f
    /** |offset| at or beyond this is "clear of the beat" and feeds [OUT_OF_BAND_MIN_RECALL]. */
    const val OUT_OF_BAND_MS = 60f

    // ── Scoring grade bars, anchored to the game's hit windows (PERFECT ±50, GOOD ±100) ──
    // GOOD_FIT: errors land comfortably inside the PERFECT window, so a player's score
    // reflects their timing. USABLE: still inside the GOOD window; beyond it the score
    // stops being a fair reflection of timing, which is NOT_FIT.
    const val GRADE_GOOD_P95_MS = 35f
    const val GRADE_GOOD_MEAN_MS = 15f
    const val GRADE_USABLE_P95_MS = 80f
    const val GRADE_USABLE_MEAN_MS = 35f
    /** On-beat dead zone this narrow (half-width, ms) is imperceptible; wider only demotes GOOD_FIT to USABLE. */
    const val GRADE_GOOD_MASK_MS = 25f
}
