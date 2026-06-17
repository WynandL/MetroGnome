package com.example.metrognome.poll

import android.content.Context
import androidx.core.content.edit

/**
 * Determines which poll (if any) should currently be shown to the user.
 *
 * Uses a dedicated SharedPreferences file so poll state is fully isolated from
 * other app prefs. One boolean per poll id — written as soon as the user
 * interacts (any button including X and auto-dismiss) so the poll never
 * re-appears even if the app is killed mid-animation.
 *
 * Usage:
 *   val poll = PollManager(context).pendingPoll(gnotes)
 *   if (poll != null) show PollBanner; in onResponse call markAnswered.
 */
class PollManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the first poll the user is eligible for and has not yet answered. */
    fun pendingPoll(gnotes: Int): PollConfig? =
        ALL_POLLS.firstOrNull { poll ->
            gnotes >= poll.minGnotes && !prefs.getBoolean(answeredKey(poll.id), false)
        }

    fun markAnswered(pollId: String) {
        prefs.edit { putBoolean(answeredKey(pollId), true) }
    }

    companion object {
        private const val PREFS_NAME = "poll_state"
        private fun answeredKey(id: String) = "answered_$id"
    }
}
