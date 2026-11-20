package com.example.metrognome.adfree

import com.example.metrognome.adfree.AdFreeTrialPolicy.DURATION_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The trial's rules: who is offered it, once ever, how it ends and what the user is told. */
class AdFreeTrialPolicyTest {

    private val t0 = 1_800_000_000_000L
    private val day0 = 20_000L
    private fun now(afterMs: Long = 0L, boot: Int = 7) = TimeReading(t0 + afterMs, 1_000_000L + afterMs, boot)

    private fun due(
        s: TrialState, days: Int = 3, today: Long = day0, purchased: Boolean = false, other: Boolean = false,
    ) = AdFreeTrialPolicy.cardDue(s, days, today, purchased, other)

    // ── Eligibility ──────────────────────────────────────────────────────────────────

    @Test fun notOfferedBeforeTheThirdDistinctDay() {
        assertNull(due(TrialState(), days = 0))
        assertNull(due(TrialState(), days = 2))
        assertEquals(TrialCard.OFFER, due(TrialState(), days = 3))
        assertEquals(TrialCard.OFFER, due(TrialState(), days = 40))
    }

    @Test fun neverOfferedToAnOwnerOrSomeoneAlreadyAdFree() {
        assertNull(due(TrialState(), purchased = true))
        assertNull(due(TrialState(), other = true))
    }

    @Test fun notNowEarnsExactlyOneReOfferAFewDaysLater() {
        val once = AdFreeTrialPolicy.decline(TrialState(), day0)
        assertNull(due(once, today = day0))
        assertNull(due(once, today = day0 + AdFreeTrialPolicy.REOFFER_AFTER_DAYS - 1))
        assertEquals(TrialCard.OFFER, due(once, today = day0 + AdFreeTrialPolicy.REOFFER_AFTER_DAYS))
        assertEquals(2, AdFreeTrialPolicy.offerNumber(once))

        val twice = AdFreeTrialPolicy.decline(once, day0 + 5)
        assertNull(due(twice, today = day0 + 500))
    }

    @Test fun acceptedOnceIsNeverOfferedAgainEvenAfterItEnds() {
        val taken = AdFreeTrialPolicy.accept(TrialState(), now())
        assertNull(due(taken, days = 99))
        val ended = AdFreeTrialPolicy.advance(taken, now(DURATION_MS), purchased = false).state
        val seen = AdFreeTrialPolicy.noticeSeen(ended)
        assertNull(due(seen, days = 99, today = day0 + 400))
    }

    @Test fun acceptingTwiceDoesNotRestartTheClock() {
        val taken = AdFreeTrialPolicy.accept(TrialState(), now())
        val again = AdFreeTrialPolicy.accept(taken, now(10 * 3_600_000L))
        assertEquals(taken, again)
    }

    // ── Running and ending ───────────────────────────────────────────────────────────

    @Test fun activeForTheDurationThenEndsWithANotice() {
        val taken = AdFreeTrialPolicy.accept(TrialState(), now())
        assertTrue(AdFreeTrialPolicy.active(taken))

        val midway = AdFreeTrialPolicy.advance(taken, now(DURATION_MS / 2), purchased = false)
        assertNull(midway.endedNow)
        assertTrue(AdFreeTrialPolicy.active(midway.state))

        val end = AdFreeTrialPolicy.advance(midway.state, now(DURATION_MS), purchased = false)
        assertEquals(TrialEndReason.EXPIRED, end.endedNow)
        assertFalse(AdFreeTrialPolicy.active(end.state))
        assertEquals(TrialCard.ENDED, due(end.state))

        // Reported once: later ticks do not end it again.
        assertNull(AdFreeTrialPolicy.advance(end.state, now(DURATION_MS * 3), purchased = false).endedNow)
    }

    @Test fun expiringWhileTheAppWasClosedIsSeenOnTheFirstTickAfter() {
        val taken = AdFreeTrialPolicy.accept(TrialState(), now())
        // Next opened three days later, after a reboot.
        val later = TimeReading(t0 + 3 * DURATION_MS, 5_000L, bootCount = 8)
        val step = AdFreeTrialPolicy.advance(taken, later, purchased = false)
        assertEquals(TrialEndReason.EXPIRED, step.endedNow)
        assertEquals(TrialCard.ENDED, due(step.state))
    }

    @Test fun buyingDuringTheTrialEndsItSilently() {
        val taken = AdFreeTrialPolicy.accept(TrialState(), now())
        val step = AdFreeTrialPolicy.advance(taken, now(3_600_000L), purchased = true)
        assertEquals(TrialEndReason.PURCHASED, step.endedNow)
        assertFalse(AdFreeTrialPolicy.active(step.state))
        assertFalse(step.state.endNoticePending)
        assertNull(due(step.state, purchased = true))
    }

    @Test fun theEndNoticeIsDroppedWhenItWouldBeUntrue() {
        val ended = AdFreeTrialPolicy.advance(
            AdFreeTrialPolicy.accept(TrialState(), now()), now(DURATION_MS), purchased = false,
        ).state
        // The daily-Gnotes ad-free days cover the user: ads are not back, so no card...
        assertNull(due(ended, other = true))
        // ...and the notice is dropped rather than kept for later, when it would be stale.
        val cleaned = AdFreeTrialPolicy.dropStaleNotice(ended, purchased = false, otherwiseAdFree = true)
        assertFalse(cleaned.endNoticePending)
        assertNull(due(cleaned))
        // Bought after it ended, before seeing the card: same.
        assertFalse(AdFreeTrialPolicy.dropStaleNotice(ended, purchased = true, otherwiseAdFree = false).endNoticePending)
    }

    @Test fun theEndNoticeShowsOnce() {
        val ended = AdFreeTrialPolicy.advance(
            AdFreeTrialPolicy.accept(TrialState(), now()), now(DURATION_MS), purchased = false,
        ).state
        assertEquals(TrialCard.ENDED, due(ended))
        assertNull(due(AdFreeTrialPolicy.noticeSeen(ended)))
    }

    @Test fun movingTheClockBackCannotReviveAnEndedTrial() {
        val ended = AdFreeTrialPolicy.advance(
            AdFreeTrialPolicy.accept(TrialState(), now()), now(DURATION_MS), purchased = false,
        ).state
        val wound = AdFreeTrialPolicy.advance(ended, TimeReading(t0, 1_000_000L, 7), purchased = false)
        assertFalse(AdFreeTrialPolicy.active(wound.state))
    }
}
