package com.example.metrognome.audio.metronome

import org.junit.Assert.assertEquals
import org.junit.Test

class BeatPresentationTest {

    @Test
    fun aBeatAheadOfTheAnchorIsPresentedThatMuchLater() {
        // Frame 44_100 was presented at boot 10 s; a beat starting at frame 52_920 (0.2 s of
        // audio later) is presented at 10.2 s, however early the callback for it ran.
        assertEquals(10_200L, BeatPresentation.fromTimestamp(52_920, 44_100, 10_000_000_000L, 44_100))
    }

    @Test
    fun aBeatBehindTheAnchorWasPresentedEarlier() {
        assertEquals(9_900L, BeatPresentation.fromTimestamp(39_690, 44_100, 10_000_000_000L, 44_100))
    }

    @Test
    fun theQueueFallbackAddsOnlyTheFramesStillAheadOfThePlayhead() {
        // 8_820 frames (200 ms at 44.1 kHz) queued ahead of the playhead.
        assertEquals(5_200L, BeatPresentation.fromQueue(52_920, 44_100, 5_000L, 44_100))
        // A playhead already past the frame never yields a time in the past of "now".
        assertEquals(5_000L, BeatPresentation.fromQueue(40_000, 44_100, 5_000L, 44_100))
    }
}
