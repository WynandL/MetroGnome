package com.example.metrognome.adfree

/*
 * The ad-free trial's rules, pure Kotlin (AdFreeTrialPolicyTest). AdFreeTrialManager holds
 * the state, persists it and runs these; nothing else decides anything about the trial.
 *
 * The product decisions (dev, 2026-11-20): 24 hours, offered from the 3rd distinct day the
 * app is opened, once per install, with a single re-offer a few days after a "Not now". Never
 * offered to anyone who owns Remove Ads or is ad-free from the daily Gnotes goal right now.
 */

enum class TrialEndReason(val analyticsName: String) {
    /** The 24 hours ran out. The only end that shows the "ads are back" card. */
    EXPIRED("expired"),
    /** Remove Ads was bought while the trial ran; it ends silently. */
    PURCHASED("purchased"),
}

/** Everything the trial remembers. Immutable; the manager saves each new value. */
data class TrialState(
    /** "Not now" taps (the Back key counts as one). */
    val declines: Int = 0,
    /** The epoch day of the last "Not now", or -1. */
    val lastDeclineDay: Long = -1L,
    /** Set the moment "Try it" is tapped, and never cleared: that is what makes it once ever. */
    val window: AdFreeWindow? = null,
    val ended: TrialEndReason? = null,
    /** The trial expired and the user has not yet seen the card that says so. */
    val endNoticePending: Boolean = false,
)

/** The full-screen card the trial wants shown, if any. */
enum class TrialCard { OFFER, ENDED }

object AdFreeTrialPolicy {
    const val DURATION_MS = 24L * 60 * 60 * 1000
    const val MIN_USAGE_DAYS = 3
    const val REOFFER_AFTER_DAYS = 3L
    /** The first "Not now" earns one re-offer; the second ends it for good. */
    const val MAX_DECLINES = 2

    /** The trial's own hours are counting down. */
    fun active(s: TrialState): Boolean =
        s.window != null && s.ended == null && !s.window.expired

    fun eligible(
        s: TrialState,
        usageDays: Int,
        today: Long,
        purchased: Boolean,
        otherwiseAdFree: Boolean,
    ): Boolean =
        s.window == null &&
            !purchased &&
            !otherwiseAdFree &&
            usageDays >= MIN_USAGE_DAYS &&
            when (s.declines) {
                0 -> true
                in 1 until MAX_DECLINES -> today >= s.lastDeclineDay + REOFFER_AFTER_DAYS
                else -> false
            }

    /** Which number offer this would be (1 or 2), for analytics. */
    fun offerNumber(s: TrialState): Int = s.declines + 1

    fun decline(s: TrialState, today: Long): TrialState =
        s.copy(declines = s.declines + 1, lastDeclineDay = today)

    /** Starts the trial. Ignored if it has ever been started. */
    fun accept(s: TrialState, now: TimeReading, durationMs: Long = DURATION_MS): TrialState =
        if (s.window != null) s else s.copy(window = AdFreeWindow.start(now, durationMs))

    /** The result of bringing the trial up to date: the new state, and how it ended if it just did. */
    data class Step(val state: TrialState, val endedNow: TrialEndReason?)

    fun advance(s: TrialState, now: TimeReading, purchased: Boolean): Step {
        val w = s.window ?: return Step(s, null)
        if (s.ended != null) return Step(s, null)
        if (purchased) return Step(s.copy(ended = TrialEndReason.PURCHASED), TrialEndReason.PURCHASED)
        val next = w.advance(now)
        return if (next.expired) {
            Step(s.copy(window = next, ended = TrialEndReason.EXPIRED, endNoticePending = true), TrialEndReason.EXPIRED)
        } else {
            Step(s.copy(window = next), null)
        }
    }

    /**
     * The card due now, or null. The "ads are back" card is due only if ads really are back:
     * if Remove Ads was bought or the Gnotes ad-free days cover the user, it would be untrue,
     * and [dropStaleNotice] clears it instead.
     */
    fun cardDue(
        s: TrialState,
        usageDays: Int,
        today: Long,
        purchased: Boolean,
        otherwiseAdFree: Boolean,
    ): TrialCard? = when {
        s.endNoticePending && !purchased && !otherwiseAdFree -> TrialCard.ENDED
        eligible(s, usageDays, today, purchased, otherwiseAdFree) -> TrialCard.OFFER
        else -> null
    }

    /** A pending end notice that would be untrue now (see [cardDue]) is dropped, not deferred. */
    fun dropStaleNotice(s: TrialState, purchased: Boolean, otherwiseAdFree: Boolean): TrialState =
        if (s.endNoticePending && (purchased || otherwiseAdFree)) s.copy(endNoticePending = false) else s

    fun noticeSeen(s: TrialState): TrialState = s.copy(endNoticePending = false)
}
