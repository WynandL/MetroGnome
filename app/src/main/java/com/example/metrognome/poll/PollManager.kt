package com.example.metrognome.poll

import android.content.Context
import androidx.core.content.edit

/**
 * Determines which poll (if any) should currently be shown to the user.
 *
 * Uses a dedicated SharedPreferences file so poll state is fully isolated from
 * other app prefs. A real answer ("up"/"down") or an explicit "dismissed" (the
 * X button) retires the poll permanently.
 *
 * There is no timeout. The banner stays until the user answers it, closes it,
 * starts playing or leaves the tab; the last three hide it without recording
 * anything. What is recorded is the *show*: [recordShown] counts one per day
 * (a 24 h cooldown follows every show), and after [MAX_SHOWS] unanswered shows
 * the poll retires itself and [retireIgnored] hands its id back exactly once so
 * the caller can report a single "ignored" row. The previous design auto-dismissed
 * after 25 s and re-asked daily, which logged one "auto_dismissed" row per day
 * for the same user and measured only that they were busy on the screen.
 *
 * Usage:
 *   retireIgnored().forEach { report(it, "ignored") }
 *   val poll = pendingPoll(gnotes)
 *   if (poll != null) show PollBanner; on first appearance call recordShown;
 *   in onResponse call recordAnswered.
 */
class PollManager(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** The first poll the user is eligible for, not retired, not on cooldown, and not shown out. */
    fun pendingPoll(gnotes: Int): PollConfig? =
        ALL_POLLS.firstOrNull { poll ->
            gnotes >= poll.minGnotes &&
                !prefs.getBoolean(answeredKey(poll.id), false) &&
                prefs.getInt(shownCountKey(poll.id), 0) < MAX_SHOWS &&
                System.currentTimeMillis() >= prefs.getLong(nextEligibleKey(poll.id), 0L)
        }

    /** Counts one appearance of [pollId] and starts the daily cooldown. */
    fun recordShown(pollId: String) {
        prefs.edit {
            putInt(shownCountKey(pollId), prefs.getInt(shownCountKey(pollId), 0) + 1)
            putLong(nextEligibleKey(pollId), System.currentTimeMillis() + SHOW_COOLDOWN_MS)
        }
    }

    /** How many times [pollId] has appeared so far (for the analytics show_number). */
    fun shownCount(pollId: String): Int = prefs.getInt(shownCountKey(pollId), 0)

    /** Retires [pollId] on any explicit outcome: "up", "down" or "dismissed". */
    fun recordAnswered(pollId: String) {
        prefs.edit { putBoolean(answeredKey(pollId), true) }
    }

    /**
     * Polls shown [MAX_SHOWS] times without an answer. Retires each one and returns
     * its id, so a second call returns nothing: the caller reports "ignored" once.
     */
    fun retireIgnored(): List<String> =
        ALL_POLLS.filter { poll ->
            !prefs.getBoolean(answeredKey(poll.id), false) &&
                prefs.getInt(shownCountKey(poll.id), 0) >= MAX_SHOWS
        }.map { poll ->
            prefs.edit { putBoolean(answeredKey(poll.id), true) }
            poll.id
        }

    companion object {
        private const val PREFS_NAME = "poll_state"
        private const val MAX_SHOWS = 5
        private const val SHOW_COOLDOWN_MS = 24L * 60 * 60 * 1000
        private fun answeredKey(id: String) = "answered_$id"
        private fun nextEligibleKey(id: String) = "next_eligible_$id"
        private fun shownCountKey(id: String) = "shown_count_$id"
    }
}
