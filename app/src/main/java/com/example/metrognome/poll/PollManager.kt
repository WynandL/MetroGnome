package com.example.metrognome.poll

import android.content.Context
import androidx.core.content.edit

/**
 * Determines which poll (if any) should currently be shown to the user.
 *
 * Uses a dedicated SharedPreferences file so poll state is fully isolated from
 * other app prefs. A real answer ("up"/"down") or an explicit "dismissed" (the
 * X button) retires the poll permanently. A silent "auto_dismissed" (25 s
 * timeout, no interaction) is treated as "probably never seen it" rather than
 * an opinion: it gets re-asked once a day, up to [MAX_AUTO_DISMISS_RETRIES]
 * times, after which it also retires permanently so it can never turn into a
 * recurring nag. The retry count is generous - drummers/musicians using the
 * app mid-practice are a likely reason for a silent timeout, not disinterest.
 *
 * Usage:
 *   val poll = PollManager(context).pendingPoll(gnotes)
 *   if (poll != null) show PollBanner; in onResponse call recordResponse.
 */
class PollManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Returns the first poll the user is eligible for, not retired, and not currently snoozed. */
    fun pendingPoll(gnotes: Int): PollConfig? =
        ALL_POLLS.firstOrNull { poll ->
            gnotes >= poll.minGnotes &&
                !prefs.getBoolean(answeredKey(poll.id), false) &&
                System.currentTimeMillis() >= prefs.getLong(nextEligibleKey(poll.id), 0L)
        }

    /** Records the outcome of showing [pollId]; see class kdoc for retry behavior. */
    fun recordResponse(pollId: String, response: String) {
        if (response != "auto_dismissed") {
            prefs.edit { putBoolean(answeredKey(pollId), true) }
            return
        }
        val retries = prefs.getInt(retryCountKey(pollId), 0)
        if (retries >= MAX_AUTO_DISMISS_RETRIES) {
            prefs.edit { putBoolean(answeredKey(pollId), true) }
        } else {
            prefs.edit {
                putInt(retryCountKey(pollId), retries + 1)
                putLong(nextEligibleKey(pollId), System.currentTimeMillis() + RETRY_COOLDOWN_MS)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "poll_state"
        private const val MAX_AUTO_DISMISS_RETRIES = 5
        private const val RETRY_COOLDOWN_MS = 24L * 60 * 60 * 1000
        private fun answeredKey(id: String) = "answered_$id"
        private fun nextEligibleKey(id: String) = "next_eligible_$id"
        private fun retryCountKey(id: String) = "retry_count_$id"
    }
}
