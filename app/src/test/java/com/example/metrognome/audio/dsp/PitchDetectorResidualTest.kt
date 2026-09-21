package com.example.metrognome.audio.dsp

import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.chords.ChordTimbre
import com.example.metrognome.audio.chords.ChordVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log2

/**
 * [PitchDetector.detectAbove]: the pitch of what is new above a reference spectrum.
 *
 * The first test documents the trap this exists for. Two harmonic notes a fifth apart,
 * sounding together at similar levels, repeat at their common period, so plain MPM reads
 * the octave below the root (the missing fundamental, the same effect CLAUDE.md notes for
 * the drone's Fifth blend). The rest show the residual hearing the new note alone.
 */
class PitchDetectorResidualTest {

    private val sr = ChordVoice.SAMPLE_RATE
    private val win = 4096
    private val detector = PitchDetector(sr, win)

    private fun cents(a: Float, midi: Int) = 1200 * log2(a / NoteNames.frequencyOf(midi, 440f))

    /** Render with the sustained test timbre and cut the window ending at [endMs]. */
    private fun mixWindow(events: List<ChordVoice.ToneEvent>, endMs: Int): FloatArray {
        val pcm = ChordVoice.render(events, 440f, ChordTimbre.TEST_TONE, tailMs = 200)
        val end = sr * endMs / 1000
        return FloatArray(win) { pcm[end - win + it] / 32768f }
    }

    @Test
    fun plainMpmReadsAFifthAsTheOctaveBelowItsRoot() {
        // C4 and G4 together, equal level, both steady.
        val events = listOf(ChordVoice.ToneEvent(60, 0, 1000, 0.7f), ChordVoice.ToneEvent(67, 0, 1000, 0.7f))
        val p = detector.detect(mixWindow(events, 600))
        assertNotNull(p)
        assertEquals("plain reading of C4+G4", 48, NoteNames.nearestMidi(p!!.frequency, 440f))   // C3
    }

    @Test
    fun theResidualHearsTheNewNoteOverTheRingingOne() {
        // C4 rings from 0; G4 arrives at 500 ms. Reference: the window ending just before it.
        val events = listOf(ChordVoice.ToneEvent(60, 0, 1500, 0.7f), ChordVoice.ToneEvent(67, 500, 1500, 0.7f))
        val reference = FloatArray(detector.spectrumSize)
        detector.magnitudeSpectrum(mixWindow(events, 480), reference)
        // 100 ms after the new note's onset, its window still holds a slice of old-only audio.
        val p = detector.detectAbove(mixWindow(events, 600), reference)
        assertNotNull("nothing heard above the reference", p)
        assertTrue("residual read ${p!!.frequency} Hz, ${cents(p.frequency, 67).toInt()} cents from G4", abs(cents(p.frequency, 67)) < 20)
    }

    @Test
    fun theResidualHearsALowerNoteOverAHigherRingingOne() {
        // G4 rings; C4 arrives. Plain MPM would give C4 anyway here (it is the common
        // period), which is why the descending case is tested through the tracker too;
        // this checks the residual does not break the easy direction.
        val events = listOf(ChordVoice.ToneEvent(67, 0, 1500, 0.7f), ChordVoice.ToneEvent(64, 500, 1500, 0.7f))
        val reference = FloatArray(detector.spectrumSize)
        detector.magnitudeSpectrum(mixWindow(events, 480), reference)
        val p = detector.detectAbove(mixWindow(events, 600), reference)
        assertNotNull(p)
        assertTrue("residual read ${cents(p!!.frequency, 64).toInt()} cents from E4", abs(cents(p.frequency, 64)) < 20)
    }

    @Test
    fun withAnEmptyReferenceTheResidualIsThePlainReading() {
        val events = listOf(ChordVoice.ToneEvent(57, 0, 1000, 0.7f))
        val window = mixWindow(events, 600)
        val silence = FloatArray(detector.spectrumSize)
        val plain = detector.detect(window)!!
        val above = detector.detectAbove(window, silence)!!
        assertEquals(plain.frequency, above.frequency, 0.5f)
    }

    @Test
    fun aReferenceOfTheSameNoteLeavesNothing() {
        // The reference holds exactly what still sounds: the residual should be nothing periodic
        // or at worst the same note (a re-pluck), never a different one.
        val events = listOf(ChordVoice.ToneEvent(60, 0, 2000, 0.7f))
        val reference = FloatArray(detector.spectrumSize)
        detector.magnitudeSpectrum(mixWindow(events, 500), reference)
        val p = detector.detectAbove(mixWindow(events, 700), reference)
        if (p != null) assertEquals(60, NoteNames.nearestMidi(p.frequency, 440f))
    }
}
