package com.example.metrognome.audio.rhythm

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [RoomNoiseMonitor] - verifies the asymmetric-EMA floor distinguishes brief
 * claps (which must NOT trip the noise indicator) from a sustained noisy room (which must).
 */
class RoomNoiseMonitorTest {

    private val quiet = 0.02f   // typical quiet-room amplitude on the detector's 0..1 scale
    private val loud = 0.30f    // a clearly, persistently loud room (well above the noisy threshold)

    /** Feed [n] samples at [level]. */
    private fun RoomNoiseMonitor.feed(level: Float, n: Int) {
        repeat(n) { update(level) }
    }

    @Test
    fun briefClapsOverAQuietRoomDoNotTripTheIndicator() {
        val m = RoomNoiseMonitor()
        m.feed(quiet, 50)   // settle on the quiet floor
        // Simulate clapping on the beat: a loud, 2-sample spike every ~20 samples (the gaps between
        // claps are far longer than the spike), repeated many times.
        repeat(30) {
            m.update(0.9f); m.update(0.8f)    // the clap
            m.feed(quiet, 20)                  // the gap before the next clap
        }
        assertFalse("claps over a quiet room must not read as noisy", m.noisy.value)
    }

    @Test
    fun sustainedNoiseTripsTheIndicator() {
        val m = RoomNoiseMonitor()
        m.feed(quiet, 50)
        assertFalse(m.noisy.value)
        // A fan / TV / conversation: a continuously raised level, no quiet gaps. The rise is slow by
        // design, so this needs to be sustained for a while before it trips.
        m.feed(loud, 500)
        assertTrue("sustained background noise must read as noisy", m.noisy.value)
    }

    @Test
    fun recoversToQuietAfterNoiseStops() {
        val m = RoomNoiseMonitor()
        m.feed(loud, 500)
        assertTrue(m.noisy.value)
        m.feed(quiet, 400)
        assertFalse("the indicator should clear once the room goes quiet again", m.noisy.value)
    }

    @Test
    fun resetClearsState() {
        val m = RoomNoiseMonitor()
        m.feed(loud, 500)
        assertTrue(m.noisy.value)
        m.reset()
        assertFalse(m.noisy.value)
    }
}
