package com.example.metrognome.audio.selftest

import android.content.Context
import androidx.core.content.edit

/**
 * Persists the outcome of a passed self-test: the device latency constant plus
 * the route it was measured on.
 *
 * This is the bridge from the dev tool to the production feature. Once a device
 * passes, every mic feature (Speed Trainer, Practice, Rhythm Game) reads the
 * single saved constant from here instead of trying to estimate latency from the
 * player - the entire human-in-the-loop calibration is replaced by this value.
 *
 * Stored in its own SharedPreferences file so it is independent of any feature's
 * settings and trivially clearable.
 */
class SelfTestCalibrationStore(context: Context) {

    private val prefs =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** True once a self-test has passed and a usable latency constant is stored. */
    val isCalibrated: Boolean
        get() = prefs.contains(KEY_LATENCY_MS)

    /** The measured acoustic round-trip latency in ms, or null if never calibrated. */
    val latencyMs: Float?
        get() = if (isCalibrated) prefs.getFloat(KEY_LATENCY_MS, 0f) else null

    /**
     * The verdict of the most recent finished run, or null if the test has never
     * produced a definitive result. Only PASS and FAIL are stored - an ABORT is
     * user-fixable and transient, so it never overwrites this.
     */
    val lastVerdict: CheckStatus?
        get() = prefs.getString(KEY_VERDICT, null)
            ?.let { runCatching { CheckStatus.valueOf(it) }.getOrNull() }

    /**
     * The user's single app-wide opt-in for mic mode. Independent of the calibration
     * constant: disabling keeps the constant so re-enabling needs no re-check. Whether
     * the mic actually runs is this flag AND an eligible device - see [MicCalibration.isActive].
     */
    var micModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_MIC_ENABLED, false)
        set(value) { prefs.edit { putBoolean(KEY_MIC_ENABLED, value) } }

    /**
     * DEV-ONLY override. When true, Practice / Speed Trainer sessions synthesize plausible
     * mic timing at completion if no real hits landed, so the timing bonus, result overlay,
     * and points flow can be exercised on a device (or emulator) that fails the self-test.
     * Real hits always take precedence. Cleared by [clear]; always gated behind dev mode at
     * the read site so it can never affect a production build.
     */
    var devSimulateTiming: Boolean
        get() = prefs.getBoolean(KEY_DEV_SIMULATE, false)
        set(value) { prefs.edit { putBoolean(KEY_DEV_SIMULATE, value) } }

    /**
     * Dev: enable mic mode with a usable latency (a forced calibration pass). NON-DESTRUCTIVE:
     * if a real calibration already exists it is kept (so forcing a pass on a phone that genuinely
     * passed never clobbers its measured bias); only a device with no constant gets the supplied
     * fallback value. Does NOT touch [devSimulateTiming] - the caller sets that explicitly, so
     * "enable the real mic path" and "synthesize a bonus" stay separate and testable.
     */
    fun devForcePass(fallbackLatencyMs: Float, route: AudioRoute) {
        if (!isCalibrated) save(fallbackLatencyMs, route)
        micModeEnabled = true
    }

    /** Store a passing calibration: the constant, the route it was measured on, and the verdict. */
    fun save(latencyMs: Float, route: AudioRoute) {
        prefs.edit {
            putFloat(KEY_LATENCY_MS, latencyMs)
            putString(KEY_ROUTE, route.name)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            putString(KEY_VERDICT, CheckStatus.PASS.name)
        }
    }

    /**
     * Record a device that could not pass. Clears any prior constant - the latest
     * evidence wins - and remembers the FAIL so the UI can say so instead of looking
     * like the test was never run.
     */
    fun recordFailure() {
        prefs.edit {
            remove(KEY_LATENCY_MS)
            remove(KEY_ROUTE)
            putLong(KEY_TIMESTAMP, System.currentTimeMillis())
            putString(KEY_VERDICT, CheckStatus.FAIL.name)
        }
    }

    fun clear() {
        prefs.edit { clear() }
    }

    companion object {
        private const val PREFS = "mic_selftest_calibration"
        private const val KEY_LATENCY_MS = "latency_ms"
        private const val KEY_ROUTE = "route"
        private const val KEY_TIMESTAMP = "timestamp"
        private const val KEY_VERDICT = "last_verdict"
        private const val KEY_MIC_ENABLED = "mic_mode_enabled"
        private const val KEY_DEV_SIMULATE = "dev_simulate_timing"
    }
}
