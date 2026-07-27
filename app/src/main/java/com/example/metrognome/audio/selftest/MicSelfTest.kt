package com.example.metrognome.audio.selftest

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTimestamp
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import android.media.AudioManager
import com.example.metrognome.audio.dsp.ClapDetector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Collections
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

/**
 * Fully device-dependent acoustic-loopback self-test for mic scoring.
 *
 * The engine emits its own stimuli through the speaker, hears them back through
 * the mic, and measures how faithfully it recovers a *known* timing. No human
 * input is involved - this exists to prove (or disprove) that a player can be
 * scored honestly on this hardware, and to produce the device latency constant
 * that replaces human calibration entirely.
 *
 * ## Why the result is trustworthy
 * Both ends are anchored to the BOOTTIME clock by hardware timestamps
 * (`AudioTrack.getTimestamp` on output, [AudioRecord.getTimestamp] on input).
 * The scoring residual is
 *
 *     residual = (scoring-phase clap path delay) − (latency-phase clap path delay)
 *
 * so any *constant* bias in the timestamps cancels: scoring accuracy depends only
 * on the path and clock being **stable**, not on the latency being measured to an
 * absolute truth. Stability itself is judged end to end: the scoring sweep runs on
 * the derived constant, and its residuals are the proof it worked. Spread metrics
 * from the latency phase (jitter, split drift) are surfaced as notes, never hard
 * failures - real devices showed them outlier-dominated (42 ms "jitter" alongside
 * 5 ms residuals).
 *
 * ## AEC is off by design (the conservative case, NOT the production config)
 * The capture path runs with no AcousticEchoCanceler; production RhythmDetector
 * *enables* AEC when available. The mismatch is deliberate and conservative: AEC
 * would attenuate our looped-back stimuli, so click rejection measured here is a
 * lower bound on production behaviour, while an external clap is untouched by AEC
 * in both configs. The ratio/flatness features are level-relative, so thresholds
 * calibrated here transfer.
 *
 * Debug-only entry point today; the same engine backs the production calibration
 * flow later. One instance per run.
 */
class MicSelfTest(context: Context) {

    private val appContext = context.applicationContext

    private val _state = MutableStateFlow(SelfTestUiState())
    val state: StateFlow<SelfTestUiState> = _state.asStateFlow()

    private var scope: CoroutineScope? = null
    private var record: AudioRecord? = null
    private var sampleRate = 44_100

    // Captured, classified onsets across the whole run, on the BOOTTIME ms base.
    private val captured = Collections.synchronizedList(ArrayList<CapOnset>())

    @Volatile private var normalizedRms = 0f
    @Volatile private var overrunSeen = false

    private data class CapOnset(
        val bootMs: Double,
        val isClap: Boolean,
        val ratio: Double,      // window-integrated high/low (one acceptance axis)
        val peakRatio: Double,  // largest single-hop high/low (diagnostic only)
        val flatness: Double,   // spectral flatness (the other acceptance axis)
    )

    // ── Public API ──────────────────────────────────────────────────────────────

    /** Run the full test. Safe to call once; collect [state] for progress + report. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun run() {
        if (_state.value.running) return
        scope?.cancel()
        // Reset to a fresh running state so a prior report is cleared immediately;
        // otherwise the UI keeps showing the old report card while the new run is
        // already executing underneath it (the "Run Again does nothing" bug).
        _state.value = SelfTestUiState(running = true, phase = SelfTestPhase.ENVIRONMENT, statusLine = "Starting...")
        val s = CoroutineScope(Dispatchers.Default + SupervisorJob())
        scope = s
        s.launch { execute() }
    }

    fun cancel() {
        scope?.cancel()
        stopCapture()
        scope = null
        _state.value = _state.value.copy(running = false, phase = SelfTestPhase.IDLE)
    }

    // ── Orchestration ─────────────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private suspend fun execute() {
        val route = AudioRouteMonitor(appContext).currentRoute()
        val notes = ArrayList<NoteCode>()
        captured.clear()
        overrunSeen = false

        update(phase = SelfTestPhase.ENVIRONMENT, running = true, status = "Checking the room…")

        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            finishAbort(route, notes + NoteCode.MIC_PERMISSION_DENIED, ambient = 0f, vol = 0f)
            return
        }
        if (!openRecord()) {
            finishAbort(route, notes + NoteCode.MIC_UNAVAILABLE, 0f, 0f)
            return
        }
        startCapture()

        val stim = LoopbackStimulus(sampleRate)

        // ── Phase 1: environment ────────────────────────────────────────────────
        val volFraction = systemVolumeFraction()
        val ambient = profileAmbient()
        if (ambient > SelfTestThresholds.MAX_AMBIENT_FLOOR) {
            stim.release()
            notes += NoteCode.NOISY_ROOM
            if (route == AudioRoute.BLUETOOTH) notes += NoteCode.BLUETOOTH_NOISE_CONFOUND
            finishAbort(route, notes, ambient, volFraction)
            return
        }
        if (volFraction < SelfTestThresholds.MIN_VOLUME_FRACTION) {
            stim.release()
            finishAbort(route, notes + NoteCode.VOLUME_TOO_LOW, ambient, volFraction)
            return
        }
        if (!route.isBuiltInSpeaker) {
            notes += NoteCode.NON_SPEAKER_ROUTE
        }
        // Passed both gates above, but a clean PASS should still say when it passed close to
        // a wall - forward-looking risk a silent notes list would otherwise hide.
        if (volFraction <= SelfTestThresholds.MIN_VOLUME_FRACTION * SelfTestThresholds.VOLUME_FLOOR_WARN_FRACTION) {
            notes += NoteCode.NEAR_VOLUME_FLOOR
        }
        if (ambient >= SelfTestThresholds.MAX_AMBIENT_FLOOR * SelfTestThresholds.AMBIENT_CEILING_WARN_FRACTION) {
            notes += NoteCode.NEAR_NOISE_CEILING
        }

        // ── Phase 2: speaker path (latency constant) ────────────────────────────
        update(phase = SelfTestPhase.SPEAKER_PATH, status = "Measuring speaker timing…")
        val latency = runLatencyPhase(stim) ?: run {
            stim.release()
            // Environment already passed (noise + volume gates cleared above). A missing
            // output timestamp is a hardware limitation the user cannot fix, so this is a
            // FAIL ("not a good fit"), not an ABORT/retry. environment = PASS keeps the
            // verdict from short-circuiting to ABORT; speakerPath = FAIL drives the FAIL.
            finishReport(
                route, CheckStatus.PASS, ambient, volFraction,
                speakerPath = CheckStatus.FAIL,
                notes = notes + NoteCode.NO_OUTPUT_TIMESTAMP,
            )
            return
        }
        val latencyMs = latency.latencyMs
        val jitterMs = latency.jitterMs
        // Neither jitter nor split drift gates the speaker path: the scoring sweep below
        // runs on the derived latency constant and is the real proof it works (a genuinely
        // drifted constant surfaces as residual bias, which the grade bars bound). Both
        // spread metrics are surfaced as notes. The only hard speaker-path failures are no
        // output timestamp (handled above) and an implausible latency.
        val speakerOk = latencyMs in SelfTestThresholds.MIN_LATENCY_MS..SelfTestThresholds.MAX_LATENCY_MS
        if (jitterMs > SelfTestThresholds.MAX_LATENCY_JITTER_MS) {
            notes += NoteCode.LATENCY_JITTERY
        }
        if (latency.splitDeltaMs > SelfTestThresholds.MAX_LATENCY_SPLIT_DRIFT_MS) {
            notes += NoteCode.LATENCY_DRIFTED
        }
        if (speakerOk && latencyMs >= SelfTestThresholds.MAX_LATENCY_MS * SelfTestThresholds.LATENCY_CEILING_WARN_FRACTION) {
            notes += NoteCode.LATENCY_NEAR_CEILING
        }

        // ── Phase 3: discrimination ─────────────────────────────────────────────
        update(phase = SelfTestPhase.DISCRIMINATION, status = "Separating click from clap…")
        val disc = runDiscriminationPhase(stim, latencyMs)

        // ── Phase 4: scoring sweep ──────────────────────────────────────────────
        // Capture ran with the shipped DEFAULT thresholds throughout - discrimination just
        // measured where THIS device's own click/clap margins actually sit. Score it against
        // the thresholds it would actually run with once calibrated, not the defaults it is
        // about to stop using - otherwise a device that only needs recalibrating gets judged
        // (and possibly failed) on a fit it will never ship with.
        val tunedRatio = if (disc.separable) deviceClapBandRatio(disc.margins) else null
        val tunedFlatness = if (disc.separable) deviceClapFlatnessMin(disc.margins) else null
        update(phase = SelfTestPhase.SCORING, status = "Checking scoring accuracy…")
        val scoring = runScoringPhase(stim, latencyMs, tunedRatio, tunedFlatness)

        stim.release()
        stopCapture()

        if (overrunSeen) notes += NoteCode.CAPTURE_OVERRUN

        // ── Verdicts ────────────────────────────────────────────────────────────
        // Discrimination fails only on a genuine, non-calibratable problem: either click and
        // clap are inseparable on both spectral axes, or claps aren't even registering as
        // onsets before the classifier gets a say. A low classified reject/detect rate on an
        // otherwise-separable, well-recalled device is just the shipped thresholds sitting
        // wrong for this hardware - a calibration offset (fixed by the per-device thresholds
        // saved on PASS), not an unfit device - so it is noted on either side, not failed.
        val discStatus = if (disc.separable && disc.onsetRecallRate >= SelfTestThresholds.MIN_CLAP_ONSET_RECALL) {
            CheckStatus.PASS
        } else CheckStatus.FAIL
        when {
            !disc.separable -> notes += NoteCode.INSEPARABLE
            disc.onsetRecallRate < SelfTestThresholds.MIN_CLAP_ONSET_RECALL -> notes += NoteCode.CLAP_UNDETECTED
            else -> {
                if (disc.clickRejectRate < SelfTestThresholds.MIN_CLICK_REJECT_RATE) notes += NoteCode.CLICK_LEAK_CALIBRATED
                if (disc.clapDetectRate < SelfTestThresholds.MIN_CLAP_DETECT_RATE) notes += NoteCode.CLAP_MISS_CALIBRATED
                if (!hasComfortableMargin(disc.margins)) notes += NoteCode.THIN_SEPARATION_MARGIN
            }
        }

        // Scoring is gated on what makes a score honest: claps clear of the beat must be
        // caught, and timing must stay within the GOOD window. On-beat masking is handled
        // by the grade as a caveat, not here.
        val scoringStatus = when {
            scoring.outOfBandRecall < SelfTestThresholds.OUT_OF_BAND_MIN_RECALL -> CheckStatus.FAIL
            (scoring.meanAbsResidual ?: Float.MAX_VALUE) > SelfTestThresholds.GRADE_USABLE_MEAN_MS -> CheckStatus.FAIL
            (scoring.p95AbsResidual ?: Float.MAX_VALUE) > SelfTestThresholds.GRADE_USABLE_P95_MS -> CheckStatus.FAIL
            else -> CheckStatus.PASS
        }
        if (scoring.maskingHalfWidthMs > 0f) {
            notes += NoteCode.ON_BEAT_MASKING
        }
        if (scoring.falsePositives > 0) {
            notes += NoteCode.EXTRA_ONSETS
        }

        val report = SelfTestReport(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            timestampMs = System.currentTimeMillis(),
            route = route,
            environment = CheckStatus.PASS,
            ambientFloor = ambient,
            systemVolumeFraction = volFraction,
            speakerPath = if (speakerOk) CheckStatus.PASS else CheckStatus.FAIL,
            latencyMs = latencyMs,
            latencyJitterMs = jitterMs,
            discrimination = discStatus,
            clickRejectRate = disc.clickRejectRate,
            clapDetectRate = disc.clapDetectRate,
            spectralMargins = disc.margins,
            discOnsetRecallRate = disc.onsetRecallRate,
            discSeparableFraction = disc.separableFraction,
            tunedClapBandRatio = tunedRatio,
            tunedClapFlatnessMin = tunedFlatness,
            detectionRecall = scoring.recall,
            falsePositives = scoring.falsePositives,
            outOfBandRecall = scoring.outOfBandRecall,
            maskingHalfWidthMs = scoring.maskingHalfWidthMs,
            scoring = scoringStatus,
            scoringPoints = scoring.points,
            meanAbsResidualMs = scoring.meanAbsResidual,
            p95AbsResidualMs = scoring.p95AbsResidual,
            notes = notes,
            latencyDetail = LatencyDetail(
                warmupClaps = LATENCY_WARMUP_CLAPS,
                emitted = LATENCY_CLAPS,
                used = latency.used,
                firstHalfMs = latency.firstHalfMedianMs,
                secondHalfMs = latency.secondHalfMedianMs,
                splitDeltaMs = latency.splitDeltaMs,
            ),
            discPairs = DISC_PAIRS,
            scoringTrials = SCORING_REPS * SCORING_OFFSETS_MS.size,
        )

        persistAndPublish(report, latencyMs, route)
    }

    /**
     * Persist the run's outcome and publish the report. PASS saves the constant;
     * FAIL is recorded (and clears any stale constant) so the UI can report a device
     * that is not a good fit; ABORT/PENDING are transient and leave stored state alone.
     */
    private fun persistAndPublish(report: SelfTestReport, latencyMs: Float?, route: AudioRoute) {
        val store = SelfTestCalibrationStore(appContext)
        when (report.verdict) {
            CheckStatus.PASS -> if (latencyMs != null) {
                store.save(
                    latencyMs, route,
                    clapBandRatio = deviceClapBandRatio(report.spectralMargins),
                    clapFlatnessMin = deviceClapFlatnessMin(report.spectralMargins),
                )
            }
            CheckStatus.FAIL -> store.recordFailure()
            else -> { /* transient */ }
        }
        _state.value = SelfTestUiState(phase = SelfTestPhase.DONE, running = false, report = report)
    }

    /**
     * The device-tuned clap/click ratio threshold to persist, or null (no click onsets were
     * even registered) to keep the detector's portable [ClapDetector.CLAP_BAND_RATIO] default.
     *
     * With a clean gap the threshold sits at its geometric midpoint (ratios are multiplicative,
     * so the geometric mean is central). Without a gap it sits just above the clicks' maximum:
     * discrimination only PASSes when every clap beats the clicks on at least one axis, so any
     * clap this cuts off on the ratio axis is guaranteed accepted by the flatness axis - click
     * rejection must not be compromised to chase it. Floor keeps the threshold above 1 (high
     * must exceed the tonal band); deliberately NO ceiling, because capping below the clicks'
     * maximum would re-leak clicks, and a very high threshold merely defers claps to flatness.
     */
    private fun deviceClapBandRatio(margins: SpectralMargins?): Float? {
        val clickMax = margins?.clickIntegratedMax ?: return null
        val clapMin = margins.clapIntegratedMin
        val threshold = if (clapMin != null && clapMin > clickMax) {
            sqrt(clickMax.toDouble().coerceAtLeast(0.0) * clapMin.toDouble()).toFloat()
        } else {
            clickMax * RATIO_NO_GAP_BUMP
        }
        return threshold.coerceAtLeast(CLAP_RATIO_MIN)
    }

    /**
     * The device-tuned clap flatness threshold to persist, or null (no click onsets) to keep
     * the portable [ClapDetector.CLAP_FLATNESS_MIN] default. Same placement policy as the
     * ratio: arithmetic midpoint of a clean gap (flatness is a bounded 0..1 scale), else just
     * above the clicks' maximum, with separability guaranteeing any clap cut off here is
     * accepted on the ratio axis. Clamped to a physical band: below the floor the threshold
     * would accept broadband room noise; the ceiling stays below flatness's hard limit of 1.
     */
    private fun deviceClapFlatnessMin(margins: SpectralMargins?): Float? {
        val clickMax = margins?.clickFlatnessMax ?: return null
        val clapMin = margins.clapFlatnessMin
        val threshold = if (clapMin != null && clapMin > clickMax) {
            (clickMax + clapMin) / 2f
        } else {
            clickMax + FLAT_NO_GAP_BUMP
        }
        return threshold.coerceIn(FLAT_THRESHOLD_MIN, FLAT_THRESHOLD_MAX)
    }

    /**
     * Whether this device's click/clap separation has real headroom on at least one axis,
     * rather than passing discrimination by a hair. The classifier accepts on flatness OR
     * ratio, so one axis with a solid gap is enough to be robust to a noisier session even
     * if the other axis is thin or has no gap at all - only flagged [NoteCode.THIN_SEPARATION_MARGIN]
     * when BOTH axes are thin.
     */
    private fun hasComfortableMargin(m: SpectralMargins): Boolean {
        val ratioOk = run {
            val clickMax = m.clickIntegratedMax ?: return@run true   // clicks never registered - best case
            val clapMin = m.clapIntegratedMin ?: return@run false
            clapMin >= clickMax * SelfTestThresholds.RATIO_MARGIN_COMFORT_FACTOR
        }
        val flatnessOk = run {
            val clickMax = m.clickFlatnessMax ?: return@run true
            val clapMin = m.clapFlatnessMin ?: return@run false
            clapMin - clickMax >= SelfTestThresholds.FLATNESS_MARGIN_COMFORT
        }
        return ratioOk || flatnessOk
    }

    // ── Phases ──────────────────────────────────────────────────────────────────

    private data class LatencyResult(
        val latencyMs: Float,
        val jitterMs: Float,
        val splitDeltaMs: Float,
        val firstHalfMedianMs: Float,
        val secondHalfMedianMs: Float,
        val used: Int,
    )

    /**
     * Measure the acoustic round-trip latency.
     *
     * A handful of [LATENCY_WARMUP_CLAPS] are emitted first and thrown away so the
     * speaker/codec path is at steady state (a cold first burst can read biased even
     * when its samples agree with each other). The remaining [LATENCY_CLAPS] are
     * measured: L = median(capture − emission), jitter = their spread.
     *
     * Reproducibility is checked by splitting the measured claps in half and
     * comparing the two medians - a small gap means the same number came back twice
     * within the run; a large gap means the path drifted and the constant can't be
     * trusted. Returns null only if the run could not anchor or too few claps landed.
     */
    private suspend fun runLatencyPhase(stim: LoopbackStimulus): LatencyResult? {
        val pad = stim.framesForMs(PAD_MS.toFloat())
        val spacing = stim.framesForMs(SPACING_MS.toFloat())
        val totalClaps = LATENCY_WARMUP_CLAPS + LATENCY_CLAPS
        val events = ArrayList<LoopbackStimulus.Event>()
        for (k in 0 until totalClaps) {
            events += LoopbackStimulus.Event(pad + k * spacing, LoopbackStimulus.Kind.CLAP)
        }
        val total = pad + totalClaps * spacing + stim.maxStimulusFrames + pad
        val anchor = playAndAnchor(stim, events, total) ?: return null

        // Discard the warm-up claps; only the steady-state ones feed the constant.
        val measured = events.drop(LATENCY_WARMUP_CLAPS)
            .map { stim.emissionBootMs(it.frame, anchor) }
        val claps = capturedClapsCopy()
        val delays = ArrayList<Float>()
        for (emBootMs in measured) {
            // Permissive low bound: the detector localises an onset to the hop start,
            // up to ~6 ms before the true peak, so a low-latency device can read a
            // delay near (or just below) zero. This localisation bias is identical in
            // the scoring phase, so it cancels in the residual - only its jitter shows.
            val hit = nearestClap(claps, emBootMs, lo = LATENCY_MATCH_LO_MS,
                hi = SelfTestThresholds.MAX_LATENCY_MS.toDouble())
            if (hit != null) {
                delays += (hit.bootMs - emBootMs).toFloat()
                update(liveLatency = delays.last())
            }
        }
        if (delays.size < LATENCY_CLAPS / 2) return null   // too few - clock/detection unusable

        // Split-half reproducibility: delays are in emission order, so the first and
        // last thirds-or-so straddle any drift across the measured window.
        val half = delays.size / 2
        val firstMed = median(delays.subList(0, half))
        val secondMed = median(delays.subList(delays.size - half, delays.size))
        return LatencyResult(
            latencyMs = median(delays),
            jitterMs = stddev(delays),
            splitDeltaMs = abs(firstMed - secondMed),
            firstHalfMedianMs = firstMed,
            secondHalfMedianMs = secondMed,
            used = delays.size,
        )
    }

    private data class DiscResult(
        val clickRejectRate: Float,
        val clapDetectRate: Float,
        val margins: SpectralMargins,
        /**
         * True when a threshold pair exists that rejects every click yet accepts (at least
         * [SelfTestThresholds.SEPARABILITY_MIN_FRACTION] of) the claps. The classifier accepts
         * on flatness OR ratio, so a click is rejected only when BOTH its axes sit below their
         * thresholds - meaning each threshold must clear the clicks' maximum on its axis, and
         * a clap separates by beating the clicks' maximum on at least one axis. Evaluated per
         * clap (not from aggregates), so mixed coverage counts: one clap may separate on
         * flatness while another separates on ratio; a small minority may fail both without
         * failing the device overall.
         */
        val separable: Boolean,
        /**
         * Fraction of registered claps that individually separated (the input to [separable]),
         * or null when no clap registered as an onset at all. Reported alongside [separable] so
         * the report shows the actual number the gate was judged against, not just the verdict.
         */
        val separableFraction: Float?,
        /**
         * Fraction of emitted claps that registered as ANY onset at all, before the clap/click
         * classifier runs. Unlike [clapDetectRate] (which requires the default thresholds to
         * additionally *classify* it as a clap), this only asks whether the mic heard something
         * near that time - a low value means the mic genuinely isn't capturing the clap, which
         * no threshold retuning can fix.
         */
        val onsetRecallRate: Float,
    )

    /** Emit click+clap pairs (clap well after click); measure rejection + detection. */
    private suspend fun runDiscriminationPhase(stim: LoopbackStimulus, latencyMs: Float): DiscResult {
        val pad = stim.framesForMs(PAD_MS.toFloat())
        val spacing = stim.framesForMs(SPACING_MS.toFloat())
        val clapGap = stim.framesForMs(DISC_CLAP_GAP_MS.toFloat())
        val events = ArrayList<LoopbackStimulus.Event>()
        val clickFrames = ArrayList<Int>()
        val clapFrames = ArrayList<Int>()
        for (k in 0 until DISC_PAIRS) {
            val base = pad + k * spacing
            events += LoopbackStimulus.Event(base, LoopbackStimulus.Kind.CLICK)
            events += LoopbackStimulus.Event(base + clapGap, LoopbackStimulus.Kind.CLAP)
            clickFrames += base
            clapFrames += base + clapGap
        }
        val total = pad + DISC_PAIRS * spacing + stim.maxStimulusFrames + pad
        val anchor = playAndAnchor(stim, events, total)
            ?: return DiscResult(0f, 0f, SpectralMargins(), separable = false, separableFraction = null, onsetRecallRate = 0f)

        val claps = capturedClapsCopy()
        val expClaps = clapFrames.map { stim.emissionBootMs(it, anchor) }
        val detected = expClaps.count {
            nearestClap(claps, it + latencyMs, lo = -MATCH_WIN_MS, hi = MATCH_WIN_MS) != null
        }
        // A click is "rejected" unless a clap-onset appears at its emission time.
        val expClicks = clickFrames.map { stim.emissionBootMs(it, anchor) }
        val leaked = expClicks.count {
            nearestClap(claps, it + latencyMs, lo = -MATCH_WIN_MS, hi = MATCH_WIN_MS) != null
        }
        val rejectRate = (DISC_PAIRS - leaked).toFloat() / DISC_PAIRS
        val detectRate = detected.toFloat() / DISC_PAIRS

        // Spectral separation diagnostics: read both acceptance axes (flatness and band
        // ratio) of the onset nearest each emitted stimulus (any class) so the click/clap
        // thresholds can be tuned from one run.
        val all = capturedAllCopy()
        val clickOnsets = expClicks.mapNotNull { nearestAny(all, it + latencyMs) }
        val clapOnsets = expClaps.mapNotNull { nearestAny(all, it + latencyMs) }
        val margins = SpectralMargins(
            clickIntegratedMax = clickOnsets.maxOfOrNull { it.ratio }?.toFloat(),
            clapIntegratedMin = clapOnsets.minOfOrNull { it.ratio }?.toFloat(),
            clickFlatnessMax = clickOnsets.maxOfOrNull { it.flatness }?.toFloat(),
            clapFlatnessMin = clapOnsets.minOfOrNull { it.flatness }?.toFloat(),
            clickPeakMax = clickOnsets.maxOfOrNull { it.peakRatio }?.toFloat(),
            clapPeakMin = clapOnsets.minOfOrNull { it.peakRatio }?.toFloat(),
        )

        // Separability, per clap: a clap is tellable from the clicks if it beats the clicks'
        // maximum on at least one axis (a threshold can then sit between them). Null click
        // maxima mean the clicks never even registered as onset candidates - the best
        // possible case, trivially separable. Tolerates a minority of outlier claps
        // (SEPARABILITY_MIN_FRACTION) rather than requiring every single one to clear the
        // bar: one noisy sample in a 12-clap run shouldn't alone brand a device
        // "inseparable" - see [SelfTestThresholds.SEPARABILITY_MIN_FRACTION].
        val clickRatioMax = clickOnsets.maxOfOrNull { it.ratio }
        val clickFlatMax = clickOnsets.maxOfOrNull { it.flatness }
        val cleanCount = clapOnsets.count { clap ->
            (clickFlatMax == null || clap.flatness > clickFlatMax) ||
                (clickRatioMax == null || clap.ratio > clickRatioMax)
        }
        val separableFraction = if (clapOnsets.isEmpty()) null else cleanCount.toFloat() / clapOnsets.size
        val separable = separableFraction != null && separableFraction >= SelfTestThresholds.SEPARABILITY_MIN_FRACTION
        val onsetRecallRate = clapOnsets.size.toFloat() / DISC_PAIRS
        return DiscResult(
            rejectRate.coerceIn(0f, 1f), detectRate.coerceIn(0f, 1f), margins, separable,
            separableFraction?.coerceIn(0f, 1f), onsetRecallRate.coerceIn(0f, 1f),
        )
    }

    private data class ScoringResult(
        val points: List<ScoringPoint>,
        val recall: Float,
        val outOfBandRecall: Float,
        val maskingHalfWidthMs: Float,
        val falsePositives: Int,
        val meanAbsResidual: Float?,
        val p95AbsResidual: Float?,
    )

    /**
     * Emit beat+clap at known offsets; recover each, residual = reported − injected.
     *
     * [tunedRatio]/[tunedFlatness] are this run's own derived per-device thresholds (null
     * when discrimination wasn't separable, or no click ever registered) - passing them
     * reclassifies captured onsets against what the device will actually run with once
     * calibrated, instead of grading it on the shipped defaults it was captured with.
     */
    private suspend fun runScoringPhase(
        stim: LoopbackStimulus, latencyMs: Float,
        tunedRatio: Float? = null, tunedFlatness: Float? = null,
    ): ScoringResult {
        val pad = stim.framesForMs(PAD_MS.toFloat())
        val spacing = stim.framesForMs(SPACING_MS.toFloat())
        val events = ArrayList<LoopbackStimulus.Event>()
        val trials = ArrayList<Pair<Int, Float>>()   // (beatFrame, injectedMs)
        var slot = 0
        repeat(SCORING_REPS) {
            for (offset in SCORING_OFFSETS_MS) {
                val beat = pad + slot * spacing
                val clap = beat + stim.framesForMs(offset)
                events += LoopbackStimulus.Event(beat, LoopbackStimulus.Kind.CLICK)
                events += LoopbackStimulus.Event(clap, LoopbackStimulus.Kind.CLAP)
                trials += beat to offset
                slot++
            }
        }
        val total = pad + slot * spacing + stim.maxStimulusFrames + pad
        val anchor = playAndAnchor(stim, events, total)
            ?: return ScoringResult(emptyList(), 0f, 0f, 0f, 0, null, null)

        val claps = capturedClapsCopy(tunedRatio, tunedFlatness)
        val used = HashSet<Int>()
        val points = ArrayList<ScoringPoint>()
        var hits = 0
        for ((beat, injected) in trials) {
            val beatEm = stim.emissionBootMs(beat, anchor)
            // Expected captured clap time ≈ beatEm + injected + latency.
            val target = beatEm + injected + latencyMs
            val idx = nearestClapIndex(claps, target, used)
            if (idx >= 0) {
                used += idx
                hits++
                val reported = (claps[idx].bootMs - beatEm - latencyMs).toFloat()
                points += ScoringPoint(injected, reported)
            } else {
                points += ScoringPoint(injected, null)
            }
        }
        val falsePositives = (claps.size - used.size).coerceAtLeast(0)
        val residuals = points.mapNotNull { it.residualMs?.let { r -> abs(r) } }
        val recall = hits.toFloat() / trials.size

        // Coverage split: claps clear of the beat must be caught (the real gate); misses
        // within the masking band characterize the on-beat dead zone instead of failing.
        val outTrials = points.filter { abs(it.injectedMs) >= SelfTestThresholds.OUT_OF_BAND_MS }
        val outOfBandRecall = outTrials.takeIf { it.isNotEmpty() }
            ?.let { t -> t.count { it.reportedMs != null }.toFloat() / t.size } ?: recall
        val maskingHalfWidth = points
            .filter { abs(it.injectedMs) <= SelfTestThresholds.MASKING_BAND_MS && it.reportedMs == null }
            .maxOfOrNull { abs(it.injectedMs) } ?: 0f

        return ScoringResult(
            points = aggregateByOffset(points),
            recall = recall,
            outOfBandRecall = outOfBandRecall,
            maskingHalfWidthMs = maskingHalfWidth,
            falsePositives = falsePositives,
            meanAbsResidual = residuals.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
            p95AbsResidual = residuals.takeIf { it.isNotEmpty() }?.let { p95(it) },
        )
    }

    // ── Capture ───────────────────────────────────────────────────────────────

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openRecord(): Boolean {
        for (rate in intArrayOf(44_100, 48_000, 22_050, 16_000)) {
            val minBuf = AudioRecord.getMinBufferSize(
                rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            if (minBuf <= 0) continue
            val rec = runCatching {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC, rate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    minBuf.coerceAtLeast(READ_CHUNK * 8),
                )
            }.getOrNull()
            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) {
                record = rec
                sampleRate = rate
                return true
            }
            rec?.release()
        }
        return false
    }

    private fun startCapture() {
        val rec = record ?: return
        val det = ClapDetector(sampleRate)
        rec.startRecording()
        val s = scope ?: return
        s.launch(Dispatchers.IO) {
            val buf = ShortArray(READ_CHUNK)
            val ts = AudioTimestamp()
            var appFrames = 0L
            var minBacklog = Long.MAX_VALUE
            var anchorFrame = 0L
            var anchorMs = 0.0
            try {
                while (isActive) {
                    val read = rec.read(buf, 0, READ_CHUNK)
                    if (read <= 0) { if (read < 0) break else continue }
                    appFrames += read

                    var rms = 0.0
                    for (i in 0 until read) rms += buf[i].toDouble() * buf[i]
                    normalizedRms = (sqrt(rms / read).toFloat() * AMP_GAIN).coerceIn(0f, 1f)

                    val have = runCatching {
                        rec.getTimestamp(ts, AudioTimestamp.TIMEBASE_BOOTTIME) == AudioRecord.SUCCESS &&
                            ts.nanoTime > 0L
                    }.getOrDefault(false)
                    if (have) {
                        val backlog = ts.framePosition - appFrames
                        if (backlog in 0 until minBacklog) minBacklog = backlog
                        if (minBacklog != Long.MAX_VALUE && backlog > minBacklog + OVERRUN_MARGIN_FRAMES) {
                            overrunSeen = true
                        }
                        anchorFrame = ts.framePosition
                        anchorMs = ts.nanoTime / 1_000_000.0
                    }

                    for (onset in det.process(buf, read)) {
                        val bootMs = if (anchorMs > 0.0) {
                            anchorMs + (onset.sampleIndex - anchorFrame) * 1000.0 / sampleRate
                        } else {
                            SystemClock.elapsedRealtime().toDouble()
                        }
                        val ratio = if (onset.lowRms > 0.0) onset.highRms / onset.lowRms else 0.0
                        captured.add(CapOnset(bootMs, onset.isClap, ratio, onset.peakRatio, onset.flatness))
                    }
                }
            } catch (_: Exception) {
                // Record released mid-read during stop - expected.
            }
        }
    }

    private fun stopCapture() {
        runCatching { record?.stop() }
        record?.release()
        record = null
    }

    // ── Stimulus playback + anchor acquisition ──────────────────────────────────

    /** Play [events], wait out the buffer, and return a solid output anchor (or null). */
    private suspend fun playAndAnchor(
        stim: LoopbackStimulus,
        events: List<LoopbackStimulus.Event>,
        totalFrames: Int,
    ): LoopbackStimulus.Anchor? {
        // Each phase scores only the onsets it produces. The capture thread accumulates
        // continuously across phases, so drop anything captured before this phase begins -
        // otherwise the scoring phase counts the latency/discrimination claps still in the
        // buffer as false positives. The previous phase finished (waited duration + tail)
        // before we got here, so nothing is mid-capture.
        synchronized(captured) { captured.clear() }
        if (!stim.playPlan(events, totalFrames)) return null
        var anchor: LoopbackStimulus.Anchor? = null
        var waited = 0L
        val duration = stim.durationMs()
        while (waited < duration + TAIL_MS) {
            delay(POLL_MS.milliseconds)
            waited += POLL_MS
            if (anchor == null) anchor = stim.tryAnchor()
        }
        return anchor
    }

    // ── Environment helpers ─────────────────────────────────────────────────────

    private suspend fun profileAmbient(): Float {
        val samples = ArrayList<Float>()
        var waited = 0L
        while (waited < PROFILE_MS) {
            delay(POLL_MS.milliseconds)
            waited += POLL_MS
            samples += normalizedRms
        }
        if (samples.isEmpty()) return 0f
        samples.sort()
        return samples[samples.size / 2]
    }

    private fun systemVolumeFraction(): Float {
        val am = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val cur = am.getStreamVolume(AudioManager.STREAM_MUSIC)
        return cur.toFloat() / max
    }

    // ── Matching + stats ────────────────────────────────────────────────────────

    /**
     * Onsets classified as claps. With no arguments, uses the live capture-time
     * classification (the shipped default thresholds - the only option before this run's
     * own discrimination phase has derived anything). When [ratioThreshold] /
     * [flatnessThreshold] are supplied (this run's just-derived per-device thresholds,
     * see [deviceClapBandRatio] / [deviceClapFlatnessMin]), onsets are RE-classified
     * against them instead - so a later phase can judge the device on what it will
     * actually do once calibrated, not on the defaults it is about to stop using.
     */
    private fun capturedClapsCopy(ratioThreshold: Float? = null, flatnessThreshold: Float? = null): List<CapOnset> =
        synchronized(captured) {
            captured.filter { onset ->
                if (ratioThreshold == null && flatnessThreshold == null) {
                    onset.isClap
                } else {
                    (flatnessThreshold != null && onset.flatness >= flatnessThreshold) ||
                        (ratioThreshold != null && onset.ratio >= ratioThreshold)
                }
            }.sortedBy { it.bootMs }
        }

    /** Every captured onset (clicks included), for the spectral-margin diagnostics. */
    private fun capturedAllCopy(): List<CapOnset> =
        synchronized(captured) { captured.sortedBy { it.bootMs } }

    /** Nearest captured onset of any class within the match window, or null. */
    private fun nearestAny(list: List<CapOnset>, target: Double): CapOnset? {
        var best: CapOnset? = null
        var bestErr = MATCH_WIN_MS
        for (c in list) {
            val e = abs(c.bootMs - target)
            if (e <= bestErr) { bestErr = e; best = c }
        }
        return best
    }

    private fun nearestClap(claps: List<CapOnset>, target: Double, lo: Double, hi: Double): CapOnset? {
        var best: CapOnset? = null
        var bestErr = Double.MAX_VALUE
        for (c in claps) {
            val d = c.bootMs - target
            if (d !in lo..hi) continue
            val e = abs(d)
            if (e < bestErr) { bestErr = e; best = c }
        }
        return best
    }

    private fun nearestClapIndex(claps: List<CapOnset>, target: Double, used: Set<Int>): Int {
        var best = -1
        var bestErr = Double.MAX_VALUE
        for (i in claps.indices) {
            if (i in used) continue
            val e = abs(claps[i].bootMs - target)
            if (e <= MATCH_WIN_MS && e < bestErr) { bestErr = e; best = i }
        }
        return best
    }

    /** Collapse the repeated trials per offset into one averaged point for display. */
    private fun aggregateByOffset(points: List<ScoringPoint>): List<ScoringPoint> =
        points.groupBy { it.injectedMs }.toSortedMap().map { (offset, group) ->
            val reported = group.mapNotNull { it.reportedMs }
            ScoringPoint(offset, reported.takeIf { it.isNotEmpty() }?.average()?.toFloat())
        }

    private fun median(v: List<Float>): Float {
        if (v.isEmpty()) return 0f
        val s = v.sorted()
        return s[s.size / 2]
    }

    private fun stddev(v: List<Float>): Float {
        if (v.size < 2) return 0f
        val m = v.average()
        return sqrt(v.sumOf { (it - m) * (it - m) } / v.size).toFloat()
    }

    private fun p95(absValues: List<Float>): Float {
        if (absValues.isEmpty()) return 0f
        val s = absValues.sorted()
        return s[((s.size - 1) * 0.95f).toInt().coerceIn(0, s.size - 1)]
    }

    // ── State plumbing ──────────────────────────────────────────────────────────

    private fun update(
        phase: SelfTestPhase = _state.value.phase,
        running: Boolean = _state.value.running,
        status: String = _state.value.statusLine,
        liveLatency: Float? = _state.value.liveLatencyMs,
    ) {
        _state.value = _state.value.copy(
            phase = phase, running = running, statusLine = status, liveLatencyMs = liveLatency,
        )
    }

    private fun finishAbort(route: AudioRoute, notes: List<NoteCode>, ambient: Float, vol: Float) {
        stopCapture()
        finishReport(
            route, CheckStatus.ABORT, ambient, vol,
            speakerPath = CheckStatus.PENDING, notes = notes,
        )
    }

    /** Early-exit report (abort or a non-timestamp speaker FAIL): no latency was measured. */
    private fun finishReport(
        route: AudioRoute, environment: CheckStatus, ambient: Float, vol: Float,
        speakerPath: CheckStatus, notes: List<NoteCode>,
    ) {
        val report = SelfTestReport(
            deviceModel = "${Build.MANUFACTURER} ${Build.MODEL}",
            timestampMs = System.currentTimeMillis(),
            route = route,
            environment = environment, ambientFloor = ambient, systemVolumeFraction = vol,
            speakerPath = speakerPath, latencyMs = null, latencyJitterMs = null,
            discrimination = CheckStatus.PENDING, clickRejectRate = null, clapDetectRate = null,
            detectionRecall = null, falsePositives = 0,
            scoring = CheckStatus.PENDING, scoringPoints = emptyList(),
            meanAbsResidualMs = null, p95AbsResidualMs = null,
            notes = notes,
        )
        persistAndPublish(report, latencyMs = null, route)
    }

    companion object {
        private const val READ_CHUNK = 1024
        private const val AMP_GAIN = 1f / 8000f
        private const val OVERRUN_MARGIN_FRAMES = 4096L

        private const val POLL_MS = 20L
        private const val PROFILE_MS = 1000L
        private const val PAD_MS = 250
        private const val SPACING_MS = 450
        private const val TAIL_MS = 400L
        private const val MATCH_WIN_MS = 80.0
        private const val LATENCY_MATCH_LO_MS = -30.0

        private const val LATENCY_CLAPS = 8
        /** Emitted and discarded before the measured claps so the speaker/codec path is warm, not cold. */
        private const val LATENCY_WARMUP_CLAPS = 3
        private const val DISC_PAIRS = 12
        private const val DISC_CLAP_GAP_MS = 120

        // Device-tuned threshold placement (see deviceClapBandRatio / deviceClapFlatnessMin).
        /** Ratio threshold floor: high must exceed the tonal band to be a clap. No ceiling by design. */
        private const val CLAP_RATIO_MIN = 1.1f
        /** No-gap placement: ratio threshold sits this factor above the clicks' maximum. */
        private const val RATIO_NO_GAP_BUMP = 1.15f
        /** No-gap placement: flatness threshold sits this far above the clicks' maximum. */
        private const val FLAT_NO_GAP_BUMP = 0.03f
        /** Flatness threshold band: floor rejects broadband room noise, ceiling stays below the hard 1.0. */
        private const val FLAT_THRESHOLD_MIN = 0.60f
        private const val FLAT_THRESHOLD_MAX = 0.98f
        private const val SCORING_REPS = 3
        private val SCORING_OFFSETS_MS = floatArrayOf(-120f, -80f, -40f, -20f, 0f, 20f, 40f, 80f, 120f)
    }
}

/** Live UI state streamed from [MicSelfTest]. */
data class SelfTestUiState(
    val phase: SelfTestPhase = SelfTestPhase.IDLE,
    val running: Boolean = false,
    val statusLine: String = "",
    val liveLatencyMs: Float? = null,
    val report: SelfTestReport? = null,
)
