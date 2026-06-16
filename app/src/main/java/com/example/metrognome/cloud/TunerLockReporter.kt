package com.example.metrognome.cloud

import android.os.Build
import com.example.metrognome.BuildConfig
import com.example.metrognome.debug.tuner.TunerLockLog

/**
 * Reports one tuner session's lock-quality summary to Firestore, anonymously.
 *
 * Purpose: once the noise-robustness work ships, see across real devices and rooms how
 * well locks hold — average hold time, how often the presence probe and hijack ride-out
 * actually saved a lock, and what finally drops locks (silence vs noise vs instability).
 *
 * Aggregated to ONE document per session (never per lock) so the write volume stays
 * tiny. Every field is derived from data [TunerLockLog] already captured locally; nothing
 * new is measured here. Off by default (see [CloudReportConfig.TUNER_LOCK_ENABLED]).
 *
 * Firestore rule (Firebase console):
 *
 *   match /tuner_lock/{doc} {
 *     allow create: if request.auth != null;
 *     allow read, update, delete: if false;
 *   }
 */
object TunerLockReporter {

    private const val COLLECTION = "tuner_lock"

    /** Summarise and submit the just-ended session. No-op when disabled or nothing locked. */
    fun submitSession() {
        if (!CloudReportConfig.TUNER_LOCK_ENABLED) return
        val sessions = TunerLockLog.sessions.value
        if (sessions.isEmpty()) return
        CloudReporter.submit(COLLECTION, buildDocument(sessions))
    }

    private fun buildDocument(s: List<TunerLockLog.LockSession>): Map<String, Any?> {
        val holds = s.map { it.holdMs }
        val dropHistogram = s.groupingBy { it.dropReason }.eachCount()
        return mapOf(
            "lock_count"          to s.size,
            "total_locked_ms"     to holds.sum(),
            "avg_hold_ms"         to holds.average(),
            "max_hold_ms"         to (holds.maxOrNull() ?: 0L),
            "presence_saves"      to s.sumOf { it.presenceSaves },
            "hijack_survivals"    to s.sumOf { it.hijackSurvivals },
            "min_clarity_seen"    to s.minOf { it.minClarity }.toDouble(),
            "drop_reasons"        to dropHistogram,
            "noisiest_band"       to (s.map { it.ambientLevel }.maxByOrNull { rank(it) } ?: "QUIET"),

            // Build / platform context, so the dev can filter their own test device out.
            "android_api"      to Build.VERSION.SDK_INT,
            "app_version_code" to BuildConfig.VERSION_CODE,
            "app_version_name" to BuildConfig.VERSION_NAME,
            "debug_build"      to BuildConfig.DEBUG,
            "submitted_ms"     to System.currentTimeMillis(),
        )
    }

    private fun rank(band: String): Int = when (band) {
        "NOISY" -> 2
        "MODERATE" -> 1
        else -> 0
    }
}
