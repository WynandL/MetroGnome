package com.example.metrognome.audio.chords

import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.dsp.PitchDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln

/**
 * The "hear it" synthesis, checked on the JVM the way the drone's is: the buffer is the
 * length the plan says, it never clips, and the app's own pitch detector hears each
 * arpeggio note at the pitch it was asked for.
 */
class ChordVoiceTest {

    private val cMajor = listOf(48, 52, 55)   // C3 E3 G3

    @Test
    fun bufferLengthMatchesThePlan() {
        for (pace in ChordPlaybackPace.entries) {
            val events = ChordPlaybackPlan.arpeggioThenChord(cMajor, pace)
            val pcm = ChordVoice.render(events, 440f)
            val expected = ChordVoice.SAMPLE_RATE * ChordPlaybackPlan.durationMs(events) / 1000
            assertEquals("$pace", expected, pcm.size)
        }
    }

    @Test
    fun nothingClipsEvenWithEightNotes() {
        val dense = listOf(40, 45, 50, 55, 59, 64, 67, 71)
        val pcm = ChordVoice.render(ChordPlaybackPlan.arpeggioThenChord(dense, ChordPlaybackPace.QUICK), 440f)
        val peak = pcm.maxOf { abs(it.toInt()) }
        assertTrue("peak $peak", peak <= (Short.MAX_VALUE * 0.93f).toInt())
        assertTrue("silent buffer", peak > Short.MAX_VALUE / 4)
    }

    @Test
    fun eachArpeggioNoteIsHeardAtItsPitch() {
        val pace = ChordPlaybackPace.SLOW
        val events = ChordPlaybackPlan.arpeggioThenChord(cMajor, pace)
        val pcm = ChordVoice.render(events, 440f)
        val detector = PitchDetector(ChordVoice.SAMPLE_RATE, WINDOW)
        cMajor.forEachIndexed { i, midi ->
            // A window in the middle of the note's hold, before the next note starts.
            val centreMs = i * pace.stepMs + pace.stepMs / 2
            val start = ChordVoice.SAMPLE_RATE * centreMs / 1000 - WINDOW / 2
            val window = FloatArray(WINDOW) { pcm[start + it] / Short.MAX_VALUE.toFloat() }
            val pitch = detector.detect(window)
            assertNotNull("note $i not heard", pitch)
            val cents = 1200 * ln(pitch!!.frequency / NoteNames.frequencyOf(midi, 440f)) / ln(2.0)
            assertTrue("note $i off by $cents cents", abs(cents) < 5)
        }
    }

    @Test
    fun theTestToneTimbreHoldsSteadyForTheTuner() {
        // No decay: the mic path needs a steady note to lock on, so the level a third of
        // the way through the hold equals the level near its end.
        val events = listOf(ChordVoice.ToneEvent(60, 0, 1_500, 0.9f))
        val pcm = ChordVoice.render(events, 440f, ChordTimbre.TEST_TONE, tailMs = 0)
        fun rms(fromMs: Int): Double {
            val from = ChordVoice.SAMPLE_RATE * fromMs / 1000
            var sum = 0.0
            for (i in from until from + 2048) sum += pcm[i].toDouble() * pcm[i]
            return Math.sqrt(sum / 2048)
        }
        val early = rms(500)
        val late = rms(1_300)
        assertTrue("early $early late $late", abs(early - late) / early < 0.05)
    }

    private companion object {
        const val WINDOW = 4096
    }
}
