package com.example.metrognome.cloud

import android.os.Build
import com.example.metrognome.BuildConfig

/**
 * Submits one poll response to Firestore anonymously.
 *
 * response values:
 *   "up"             — thumbs up (interested)
 *   "down"           — thumbs down (not interested)
 *   "dismissed"      — X button (no opinion given)
 *   "ignored"        — shown five times (one per day) and never touched; written once
 *                      when PollManager retires it. Replaced "auto_dismissed", which was
 *                      a 25 s timeout logged once per day for the same user.
 *
 * The gnotes snapshot is stored alongside the vote so results can be weighted
 * or filtered by engagement level (a 5000-Gnote user is a more invested signal
 * than someone who just crossed the 500-Gnote gate).
 *
 * Firestore rule (Firebase console):
 *
 *   match /poll_responses/{doc} {
 *     allow create: if request.auth != null;
 *     allow read, update, delete: if false;
 *   }
 */
object PollReporter {

    private const val COLLECTION = "poll_responses"

    fun submit(pollId: String, response: String, gnotes: Int) {
        if (!CloudReportConfig.POLLS_ENABLED) return
        CloudReporter.submit(
            COLLECTION,
            mapOf(
                "pollId"      to pollId,
                "response"    to response,
                "gnotes"      to gnotes,
                "versionCode" to BuildConfig.VERSION_CODE,
                "versionName" to BuildConfig.VERSION_NAME,
                "androidApi"  to Build.VERSION.SDK_INT,
                "debug"       to BuildConfig.DEBUG,
                "ts"          to System.currentTimeMillis(),
            ),
        )
    }
}
