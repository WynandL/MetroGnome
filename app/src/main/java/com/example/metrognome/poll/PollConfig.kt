package com.example.metrognome.poll

/**
 * Defines one in-app poll question.
 *
 * [id] is the SharedPrefs key and the Firestore pollId field. Change it if the
 * question wording changes substantially — old answered state should not suppress
 * a meaningfully different new question.
 *
 * [minGnotes] gates eligibility: a user below this threshold has not interacted
 * with the app enough for their opinion to be representative.
 *
 * Add future polls to [ALL_POLLS]. PollManager shows the first eligible,
 * unanswered entry in list order.
 */
data class PollConfig(
    val id: String,
    val question: String,
    val subtext: String,
    val minGnotes: Int,
)

val ALL_POLLS: List<PollConfig> = listOf(
    PollConfig(
        id        = "leaderboard_v1",
        question  = "Want a Gnotes leaderboard?",
        subtext   = "A free account would be needed",
        minGnotes = 250,
    ),
)
