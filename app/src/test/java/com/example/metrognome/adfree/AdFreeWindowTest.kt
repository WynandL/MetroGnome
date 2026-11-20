package com.example.metrognome.adfree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The ad-free clock: time is used up by real time passing, open or closed, and no clock
 * change, reboot or restored backup returns time already used or stretches a window.
 */
class AdFreeWindowTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val t0 = 1_800_000_000_000L          // a wall clock in 2027
    private val boot1 = 41
    private val boot2 = 42

    private fun at(wall: Long, elapsed: Long, boot: Int = boot1) = TimeReading(wall, elapsed, boot)

    private val start = at(t0, 5 * hour)
    private fun fresh() = AdFreeWindow.start(start, day)

    @Test fun aNewWindowHasItsFullDurationLeft() {
        val w = fresh().advance(start)
        assertFalse(w.expired)
        assertEquals(day, w.remainingMs)
        assertEquals(t0 + day, w.endsAtWallMs(start))
    }

    @Test fun timePassingUsesItUpAndItExpiresAtExactlyTheDuration() {
        val w = fresh()
        assertEquals(day - hour, w.advance(at(t0 + hour, 6 * hour)).remainingMs)
        assertFalse(w.advance(at(t0 + day - 1, 5 * hour + day - 1)).expired)
        assertTrue(w.advance(at(t0 + day, 5 * hour + day)).expired)
    }

    @Test fun movingTheClockForwardInTheSameBootChangesNothing() {
        // Elapsed says one hour; the wall clock says a week. The elapsed clock decides.
        val w = fresh().advance(at(t0 + 7 * day, 6 * hour))
        assertEquals(day - hour, w.remainingMs)
    }

    @Test fun movingTheClockBackInTheSameBootDoesNotGiveTimeBack() {
        val w = fresh().advance(at(t0 + 10 * hour, 15 * hour))
        val back = w.advance(at(t0 - 30 * day, 16 * hour))
        assertEquals(day - 11 * hour, back.remainingMs)
    }

    @Test fun aRebootCountsTheTimeThePhoneWasOffByTheWallClock() {
        val before = fresh().advance(at(t0 + 2 * hour, 7 * hour))
        // Off for 5 hours, then booted: elapsed restarts near zero on a new boot.
        val after = before.advance(at(t0 + 7 * hour, 60_000L, boot2))
        assertEquals(day - 7 * hour, after.remainingMs)
        // From here the new boot's elapsed clock measures.
        val later = after.advance(at(t0 + 7 * hour, 60_000L + hour, boot2))
        assertEquals(day - 8 * hour, later.remainingMs)
    }

    @Test fun aClockSetBeforeTheStartAfterARebootEndsTheWindow() {
        val before = fresh().advance(at(t0 + hour, 6 * hour))
        val after = before.advance(at(t0 - hour, 60_000L, boot2))
        assertTrue(after.expired)
    }

    @Test fun aClockWoundBackWithinTheWindowAfterARebootCannotReturnUsedTime() {
        val before = fresh().advance(at(t0 + 20 * hour, 25 * hour))
        // Rebooted with the clock set to one hour after the start: the ratchet holds 20 hours used.
        val after = before.advance(at(t0 + hour, 60_000L, boot2))
        assertEquals(4 * hour, after.remainingMs)
    }

    @Test fun anUnknownBootCountStillDetectsARebootByTheElapsedClockGoingBack() {
        val unknown = TimeReading.UNKNOWN_BOOT
        val w = AdFreeWindow.start(at(t0, 5 * hour, unknown), day)
            .advance(at(t0 + hour, 6 * hour, unknown))
        // Elapsed is now below the anchor: a reboot. The wall clock (3 h on) decides.
        val after = w.advance(at(t0 + 3 * hour, 30_000L, unknown))
        assertEquals(day - 3 * hour, after.remainingMs)
    }

    @Test fun aWindowStoredTheOldWayCarriesOnFromItsEnd() {
        val legacy = AdFreeWindow.fromLegacyEnd(endWallMs = t0 + 2 * day, durationMs = 3 * day)
        val w = legacy.advance(at(t0, 5 * hour))
        assertEquals(2 * day, w.remainingMs)
        // And then measures on the elapsed clock like any other.
        assertEquals(2 * day - hour, w.advance(at(t0 + 50 * day, 6 * hour)).remainingMs)
    }

    @Test fun aWindowRestoredFromBackupMeasuresOnTheWallClockNotAnotherPhonesBoot() {
        // Backed up two hours in; the anchor is not in the backup (ad_free_anchors.xml is
        // excluded), so it loads as NO_ANCHOR. Restored onto a new phone five days later whose
        // boot count and uptime happen to match the old anchor's: only the wall clock is used.
        val backedUp = fresh().advance(at(t0 + 2 * hour, 7 * hour))
        val restored = backedUp.copy(anchorElapsedMs = 0L, anchorBoot = AdFreeWindow.NO_ANCHOR)
        val onNewPhone = restored.advance(at(t0 + 5 * day, 8 * hour, boot1))
        assertTrue(onNewPhone.expired)

        // Restored within the window: the time between backup and restore still counts.
        val soon = restored.advance(at(t0 + 10 * hour, 8 * hour, boot1))
        assertEquals(day - 10 * hour, soon.remainingMs)
    }

    @Test fun anExpiredWindowStaysExpiredWhateverTheClockSays() {
        val gone = fresh().advance(at(t0 + 2 * day, 5 * hour + 2 * day))
        assertTrue(gone.expired)
        assertTrue(gone.advance(at(t0, 0L, boot2)).expired)
        assertEquals(0L, gone.remainingMs)
    }

    @Test fun withRemainingSetsTheTimeLeftFromNow() {
        val now = at(t0 + hour, 6 * hour)
        val w = fresh().advance(now).withRemaining(now, 2 * 60_000L)
        assertEquals(2 * 60_000L, w.remainingMs)
        assertTrue(w.advance(at(t0 + hour + 2 * 60_000L, 6 * hour + 2 * 60_000L)).expired)
    }
}
