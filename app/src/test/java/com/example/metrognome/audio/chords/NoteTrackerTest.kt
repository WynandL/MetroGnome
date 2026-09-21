package com.example.metrognome.audio.chords

import com.example.metrognome.audio.dsp.PitchDetector
import com.example.metrognome.audio.tuner.ListeningState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [NoteTracker] on hand-made frames, so each rule is checked on its own: the onset path
 * decides from the residual, prefers unanimous early evidence, refuses disagreement; the
 * steady-pitch fallback lands a note with no attack but not a sub-harmonic of what is
 * ringing, nor a note that just sounded; silence ends a note. Frame timing is the
 * analyzer's real hop at 44.1 kHz.
 */
class NoteTrackerTest {

    private val hopMs = 512 * 1000.0 / 44_100   // 11.6 ms
    private val profileFrames = 78                // 900 ms
    private val quiet = 0.0008f
    private val loud = 0.05f

    private fun tracker() = NoteTracker(hopMs).also { t -> repeat(profileFrames + 1) { t.observe(frame(rms = quiet)) } }

    private fun frame(
        onset: Boolean = false,
        pitch: Float? = null,
        residual: Float? = null,
        rms: Float = loud,
        clarity: Float = 0.95f,
    ) = NoteTracker.Frame(
        onset = onset,
        pitch = pitch?.let { PitchDetector.Pitch(it, clarity) },
        residual = residual?.let { PitchDetector.Pitch(it, clarity) },
        rms = rms,
    )

    /** Feed [n] frames and return the first note emitted, with the frame index it came on. */
    private fun NoteTracker.feed(n: Int, make: (Int) -> NoteTracker.Frame): Pair<NoteTracker.Note?, Int> {
        for (i in 0 until n) {
            val o = observe(make(i))
            if (o.note != null) return o.note to i
        }
        return null to n
    }

    @Test
    fun profilesThenListens() {
        val t = NoteTracker(hopMs)
        assertEquals(ListeningState.PROFILING, t.observe(frame(rms = quiet)).state)
        repeat(profileFrames) { t.observe(frame(rms = quiet)) }
        assertEquals(ListeningState.QUIET, t.observe(frame(rms = quiet)).state)
    }

    @Test
    fun anOnsetDecidesFromTheResidualNotThePlainPitch() {
        val t = tracker()
        // Frame 0: onset. The plain pitch says C3 (the mix's common period); the residual says G4.
        val (note, at) = t.feed(20) { i -> frame(onset = i == 0, pitch = 130.8f, residual = 392f) }
        assertNotNull(note)
        assertEquals(392f, note!!.frequency, 1f)
        assertTrue(note.fromOnset)
        // Skip (3 frames) plus four unanimous candidates: decided on frame 6.
        assertEquals(6, at)
    }

    @Test
    fun disagreeingEvidenceDecidesNothing() {
        val t = tracker()
        val pitches = floatArrayOf(392f, 261f, 440f, 330f, 392f, 261f, 440f, 330f, 392f, 261f, 440f, 330f, 392f)
        val (note, _) = t.feed(20) { i -> frame(onset = i == 0, residual = pitches[i % pitches.size]) }
        assertNull(note)
    }

    @Test
    fun aMajorityAtTheCloseStillDecides() {
        val t = tracker()
        // Two stray octave blips among the candidates: not unanimous, so no early decision,
        // but a clear majority when the window closes.
        val (note, at) = t.feed(20) { i -> frame(onset = i == 0, residual = if (i == 4 || i == 8) 784f else 392f) }
        assertNotNull(note)
        assertEquals(392f, note!!.frequency, 1f)
        assertEquals(12, at)   // skip 3 + evidence 10, decided on the window's last frame
    }

    @Test
    fun lowClarityFramesAreNotEvidence() {
        val t = tracker()
        val (note, _) = t.feed(20) { i -> frame(onset = i == 0, residual = 392f, clarity = 0.3f) }
        assertNull(note)
    }

    @Test
    fun aRiseFromSilenceCountsAsAnOnset() {
        val t = tracker()
        t.observe(frame(rms = quiet))
        val (note, _) = t.feed(20) { frame(pitch = 220f) }   // loud from the first frame, no flux onset
        assertNotNull(note)
        assertEquals(220f, note!!.frequency, 1f)
        assertTrue(note.fromOnset)
        assertEquals(NoteTracker.Source.LEVEL, note.source)
    }

    @Test
    fun weakEvidenceMustHoldItsPitch() {
        // A bark: quiet (-40 dBFS), clarity 0.8, the pitch gliding up 150 cents over 200 ms,
        // then over. Strong-evidence speed would have named it inside 70 ms.
        val t = tracker()
        val (bark, _) = t.feed(40) { i ->
            if (i < 18) frame(onset = i == 0, residual = 460f * Math.pow(2.0, i * 8.0 / 1200).toFloat(), rms = 0.01f, clarity = 0.8f)
            else frame(rms = quiet)
        }
        assertNull(bark)
        // A soft note at the same level and clarity, holding its pitch: named, but only after it has held.
        val t2 = tracker()
        val (note, at) = t2.feed(40) { i -> frame(onset = i == 0, residual = 330f, rms = 0.01f, clarity = 0.8f) }
        assertNotNull(note)
        assertEquals(330f, note!!.frequency, 1f)
        assertTrue(note.patient)
        assertTrue("decided on frame $at", at in 16..20)   // skip 3 + 14 agreeing frames
        // Loud and clear: the fast rule, as before.
        val t3 = tracker()
        val (fast, at3) = t3.feed(20) { i -> frame(onset = i == 0, residual = 330f) }
        assertNotNull(fast)
        assertTrue(!fast!!.patient)
        assertEquals(6, at3)
    }

    @Test
    fun aRiseFromSilenceWithAMurkyPitchIsNotANote() {
        // Room hum swelling up under a mic's automatic gain: loud enough, steady enough,
        // but never clear. The flux path may take this clarity (a residual is never
        // pristine); the level path may not, since nothing is ringing for it to be a residual of.
        val t = tracker()
        t.observe(frame(rms = quiet))
        val (note, _) = t.feed(20) { frame(pitch = 49f, clarity = 0.6f) }
        assertNull(note)
        // The same evidence behind a detector onset is still a note.
        val t2 = tracker()
        val (note2, _) = t2.feed(20) { i -> frame(onset = i == 0, residual = 196f, clarity = 0.6f) }
        assertNotNull(note2)
        assertEquals(NoteTracker.Source.FLUX, note2!!.source)
    }

    @Test
    fun aSteadyNewPitchWithNoOnsetIsANote() {
        val t = tracker()
        // A note already sounding (from an onset), then, with no onset, a steady different pitch.
        t.feed(20) { i -> frame(onset = i == 0, residual = 220f) }
        val (note, at) = t.feed(30) { frame(pitch = 293.7f) }
        assertNotNull(note)
        assertEquals(293.7f, note!!.frequency, 1f)
        assertTrue(!note.fromOnset)
        assertTrue("decided on frame $at", at in 25..28)   // 300 ms of steadiness
    }

    @Test
    fun theSteadyPathRefusesASubharmonicOfWhatIsRinging() {
        val t = tracker()
        t.feed(20) { i -> frame(onset = i == 0, residual = 392f) }   // G4 sounds
        // The plain pitch settles on G3 (the octave below), clear and steady, with no onset:
        // the missing fundamental of a mix, not a played note.
        val (note, _) = t.feed(40) { frame(pitch = 196f) }
        assertNull(note)
        // ...and on C3 (392 / 3 = 130.7): the virtual fundamental of G4 with a C below it.
        val (note2, _) = t.feed(40) { frame(pitch = 130.7f) }
        assertNull(note2)
    }

    @Test
    fun theSteadyPathRefusesANoteThatJustSounded() {
        val t = tracker()
        t.feed(20) { i -> frame(onset = i == 0, residual = 262f) }   // C4
        t.feed(20) { i -> frame(onset = i == 0, residual = 330f) }   // E4 over it
        // E4 decays and the plain pitch returns to C4, steady: that is the old note re-emerging.
        val (note, _) = t.feed(40) { frame(pitch = 262f) }
        assertNull(note)
    }

    @Test
    fun anOnsetTakesNeitherGuard() {
        val t = tracker()
        t.feed(20) { i -> frame(onset = i == 0, residual = 392f) }   // G4
        // A real pluck of G3: onset, residual says G3. A sub-harmonic of G4, but plucked.
        val (note, _) = t.feed(20) { i -> frame(onset = i == 0, residual = 196f) }
        assertNotNull(note)
        assertEquals(196f, note!!.frequency, 1f)
    }

    @Test
    fun silenceEndsTheNote() {
        val t = tracker()
        t.feed(20) { i -> frame(onset = i == 0, residual = 262f) }
        var state = ListeningState.LOCKED
        repeat(25) { state = t.observe(frame(rms = quiet)).state }   // 290 ms of quiet
        assertEquals(ListeningState.QUIET, state)
        // The same note can then sound again from silence.
        val (note, _) = t.feed(20) { frame(pitch = 262f) }
        assertNotNull(note)
    }

    @Test
    fun aSecondOnsetSoonAfterTheFirstIsTheSameEvent() {
        val t = tracker()
        // Onsets on frames 0 and 2 (23 ms apart): the second must not restart the window.
        val (note, at) = t.feed(20) { i -> frame(onset = i == 0 || i == 2, residual = 330f) }
        assertNotNull(note)
        assertEquals(6, at)
    }

    @Test
    fun theLiveReadoutHoldsTheNoteNotTheMixsFundamental() {
        val t = tracker()
        t.feed(20) { i -> frame(onset = i == 0, residual = 392f) }
        // While G4 sustains, plain MPM drifts to the octave below; the readout should stay on G4.
        val o = t.observe(frame(pitch = 196f))
        assertEquals(ListeningState.LOCKED, o.state)
        assertEquals(392f, o.heardHz!!, 1f)
    }

    @Test
    fun aToneThroughProfilingIsTheRoomAndNeverANote() {
        // Mains hum at 49 Hz, clear and steady, present from the moment the mic opens and
        // sitting at the room's level. On a phone this was named G1 before any note.
        val t = NoteTracker(hopMs)
        repeat(profileFrames + 1) { t.observe(frame(pitch = 49f, rms = quiet)) }
        assertEquals(49f, t.roomToneHz, 0.1f)
        // Automatic gain lifts it above the loud bar: a "rise from silence", steady and clear. Not a note.
        val (hum, _) = t.feed(60) { frame(pitch = 49f, rms = quiet * 3.5f) }
        assertNull(hum)
        // A real note at the hum's own pitch, played loud, is still a note.
        val (note, _) = t.feed(20) { i -> frame(onset = i == 0, residual = 49f, rms = loud) }
        assertNotNull(note)
        // A quiet room with no tone learns none.
        assertEquals(0f, tracker().roomToneHz, 0f)
    }

    @Test
    fun aRoomThatGetsLouderBecomesTheFloor() {
        // Air-conditioning starts: tone-free sound at 20x the floor, for a minute. Frozen,
        // every later frame would be a "rise from silence" waiting to happen.
        val t = tracker()
        repeat(60_000 / 12) { t.observe(frame(rms = quiet * 30f)) }
        assertEquals(ListeningState.QUIET, t.observe(frame(rms = quiet * 30f)).state)
        // ...and a note above the new room is still heard.
        val (note, _) = t.feed(20) { i -> frame(onset = i == 0, residual = 262f, rms = loud) }
        assertNotNull(note)
    }

    @Test
    fun forgetClearsTheGuards() {
        val t = tracker()
        t.feed(20) { i -> frame(onset = i == 0, residual = 392f) }
        t.forget()
        val (note, _) = t.feed(40) { frame(pitch = 196f) }   // no longer a sub-harmonic of anything remembered
        assertNotNull(note)
    }
}
