package com.example.metrognome.audio.chords

import com.example.metrognome.audio.NoteNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * The onset engine end to end on the JVM: [ChordVoice] renders the notes, [NoteAnalyzer]
 * listens, and what comes out must be exactly the notes that went in, in order, each
 * within a fraction of a second of its start. The cases are the ones the tuner's gate
 * cannot do: notes that ring on under the next one, fast arpeggios, a descending line
 * where the plain pitch reads the pair's common period, and a chord left to ring.
 */
class NoteAnalyzerTest {

    private val sr = ChordVoice.SAMPLE_RATE

    /** Silence before the first note: the tracker profiles the room for 900 ms and must be done before anything sounds. */
    private val LEAD_MS = 1200

    /** A captured note: MIDI and the time (ms into the buffer) it was decided. */
    private data class Captured(val midi: Int, val atMs: Double)

    private fun run(pcm: FloatArray): List<Captured> {
        val analyzer = NoteAnalyzer(sr)
        val out = ArrayList<Captured>()
        var fed = 0
        val chunk = 512
        val buf = FloatArray(chunk)
        var hops = 0
        while (fed < pcm.size) {
            val n = minOf(chunk, pcm.size - fed)
            System.arraycopy(pcm, fed, buf, 0, n)
            fed += n
            analyzer.feed(buf, n) { obs ->
                hops++
                obs.note?.let { out += Captured(NoteNames.nearestMidi(it.frequency, 440f), fed * 1000.0 / sr) }
            }
        }
        return out
    }

    private fun render(events: List<ChordVoice.ToneEvent>, timbre: ChordTimbre = ChordTimbre.LEGATO_TEST, tailMs: Int = 800, leadMs: Int = LEAD_MS): FloatArray {
        // A lead-in of silence so the tracker profiles the room before anything sounds.
        val shifted = events.map { it.copy(startMs = it.startMs + leadMs) }
        val pcm = ChordVoice.render(shifted, 440f, timbre, tailMs)
        return FloatArray(pcm.size) { pcm[it] / 32768f }
    }

    private fun withNoise(pcm: FloatArray, rms: Float, seed: Int = 7): FloatArray {
        val r = Random(seed)
        return FloatArray(pcm.size) { pcm[it] + (r.nextFloat() * 2f - 1f) * rms * 1.73f }
    }

    private fun legato(notes: List<Int>, stepMs: Int, holdMs: Int, gain: Float = 0.8f, startMs: Int = 0) =
        notes.mapIndexed { i, midi -> ChordVoice.ToneEvent(midi, startMs + i * stepMs, holdMs, gain) }

    /** Mains hum with two harmonics, the way a room and a phone's own electronics put it into the mic. */
    private fun withHum(pcm: FloatArray, hz: Double, amp: Float): FloatArray = FloatArray(pcm.size) { i ->
        pcm[i] + amp * (sin(2 * PI * hz * i / sr) + 0.5 * sin(2 * PI * 2 * hz * i / sr) + 0.3 * sin(2 * PI * 3 * hz * i / sr)).toFloat()
    }

    /**
     * A microphone's automatic gain, crudely: the gain climbs toward [maxGain] with a time
     * constant of [riseTauMs] while the input is quiet and drops back at once when it is
     * loud, so the room floor swells up in every gap after a note.
     */
    private fun withAgc(pcm: FloatArray, maxGain: Float, riseTauMs: Double): FloatArray {
        val out = FloatArray(pcm.size)
        var gain = 1f
        var env = 0f
        val envDecay = exp(-1.0 / (sr * 0.005)).toFloat()
        val rise = exp(-1000.0 / (sr * riseTauMs)).toFloat()
        for (i in pcm.indices) {
            val x = pcm[i]
            env = maxOf(abs(x), env * envDecay)
            gain = if (env * gain > 0.3f) maxOf(1f, gain * 0.999f) else minOf(maxGain, gain / rise)
            out[i] = x * gain
        }
        return out
    }

    /** Every expected note, in order, nothing else, each decided within [withinMs] of its start (the [LEAD_MS] lead-in included). */
    private fun assertCaptured(got: List<Captured>, events: List<ChordVoice.ToneEvent>, withinMs: Int = 260, label: String = "") {
        assertEquals("$label notes: got ${got.map { NoteNames.labelOf(it.midi) }}", events.map { it.midi }, got.map { it.midi })
        events.forEachIndexed { i, e ->
            val delay = got[i].atMs - (e.startMs + LEAD_MS)
            assertTrue("$label note ${NoteNames.labelOf(e.midi)} decided ${delay.toInt()} ms after its start", delay in 0.0..withinMs.toDouble())
        }
    }

    @Test
    fun aFastLegatoArpeggioIsHeardNoteByNote() {
        val events = legato(listOf(60, 64, 67), stepMs = 450, holdMs = 800)
        assertCaptured(run(render(events)), events, label = "C E G legato")
    }

    @Test
    fun theHardestCaseNotesThatNeverDecay() {
        // Each note stays as loud as the next for the whole chord: the plain pitch of the
        // mix is the chord's common period, so only the residual can hear the new note.
        val events = legato(listOf(60, 64, 67), stepMs = 450, holdMs = 1400)
        assertCaptured(run(render(events, ChordTimbre.TEST_TONE)), events, label = "C E G sustained")
    }

    @Test
    fun aDescendingLineOverRingingNotes() {
        // G4 then E4 then C4: every new note is *below* what rings, the reading MPM alone gets wrong.
        val events = legato(listOf(67, 64, 60), stepMs = 450, holdMs = 1000)
        assertCaptured(run(render(events)), events, label = "G E C descending")
    }

    @Test
    fun aSpacedArpeggioStillWorks() {
        val events = legato(listOf(55, 59, 62, 65), stepMs = 900, holdMs = 500)
        assertCaptured(run(render(events)), events, label = "G7 spaced")
    }

    @Test
    fun theChordLoopsSpacedTestToneOverAgcLiftedHumAddsNothing() {
        // The Chord Loop's spaced defaults (1700 ms notes, 1300 ms gaps, 90%) in the test-tone
        // timbre, over a faint 50 Hz hum that a mic's automatic gain lifts in every gap.
        // On a real phone this put a low phantom (A1..C3) in each gap, and being below the
        // bass, each one restarted the chord. The phantom's clarity was ~0.6; a note's is >0.9.
        val events = legato(listOf(60, 64, 67), stepMs = 3000, holdMs = 1700, gain = 0.9f)
        val room = withHum(withNoise(render(events, ChordTimbre.TEST_TONE, tailMs = 1300, leadMs = 1500), 0.002f), 50.0, 0.003f)
        val pcm = withAgc(room, maxGain = 8f, riseTauMs = 400.0)
        val got = run(pcm)
        assertEquals("notes: ${got.map { NoteNames.labelOf(it.midi) }}", events.map { it.midi }, got.map { it.midi })
    }

    /** A phone limiter's re-trigger: the level dips by [depth] over 20 ms at [atMs] and recovers over 60 ms, a rise in every band. */
    private fun withDip(pcm: FloatArray, atMs: Int, depth: Float): FloatArray {
        val start = sr * atMs / 1000
        val fall = sr * 20 / 1000
        val rise = sr * 60 / 1000
        return FloatArray(pcm.size) { i ->
            val g = when {
                i < start -> 1f
                i < start + fall -> 1f - depth * (i - start) / fall
                i < start + fall + rise -> 1f - depth + depth * (i - start - fall) / rise
                else -> 1f
            }
            pcm[i] * g
        }
    }

    @Test
    fun aReTriggeredOnsetOnARingingChordAddsNothing() {
        // C E G legato, all ringing on, then the limiter's dip-and-recover 400 ms after the
        // last note: a genuine rise across every band, with nothing new in it. On a phone
        // this decided D#2, the chord's missing fundamental, read off the residual's ghost.
        val events = legato(listOf(60, 64, 67), stepMs = 450, holdMs = 2500)
        val pcm = withDip(render(events, ChordTimbre.TEST_TONE, tailMs = 800), atMs = LEAD_MS + 900 + 400, depth = 0.5f)
        val got = run(pcm)
        assertEquals("notes: ${got.map { NoteNames.labelOf(it.midi) }}", events.map { it.midi }, got.map { it.midi })
    }

    @Test
    fun aChordLeftToRingAddsNothing() {
        val events = legato(listOf(60, 64, 67), stepMs = 450, holdMs = 3000)
        val got = run(render(events, tailMs = 1000))
        assertCaptured(got, events, label = "C E G held")
    }

    @Test
    fun aSecondChordFromALowerBassIsHeardInFull() {
        val first = legato(listOf(60, 64, 67), stepMs = 450, holdMs = 1500)
        val second = legato(listOf(55, 59, 62, 65), stepMs = 450, holdMs = 1500, startMs = 2400)
        val events = first + second
        assertCaptured(run(render(events)), events, label = "C then G7")
    }

    @Test
    fun theSameNotePluckedTwiceIsTwoEvents() {
        val events = legato(listOf(64, 64), stepMs = 600, holdMs = 900)
        assertCaptured(run(render(events)), events, label = "E4 twice")
    }

    @Test
    fun lowGuitarNotesAreHeard() {
        val events = legato(listOf(40, 44, 47), stepMs = 500, holdMs = 1000)   // E2 G#2 B2
        assertCaptured(run(render(events)), events, withinMs = 300, label = "E major low")
    }

    @Test
    fun highNotesAreHeard() {
        val events = legato(listOf(76, 79, 83), stepMs = 400, holdMs = 800)   // E5 G5 B5
        assertCaptured(run(render(events)), events, label = "Em high")
    }

    @Test
    fun aQuietPlayerInANoisyRoom() {
        val events = legato(listOf(57, 60, 64), stepMs = 450, holdMs = 900, gain = 0.08f)   // about -22 dBFS peak
        val pcm = withNoise(render(events), rms = 0.004f)                                   // about -48 dBFS
        assertCaptured(run(pcm), events, label = "Am quiet over noise")
    }

    @Test
    fun aSlowSwellWithNoAttackIsStillANote() {
        // A bowed-style note: 250 ms linear rise, no transient. The steady-pitch fallback should land it.
        val n = sr * 2
        val pcm = FloatArray(n)
        val f = NoteNames.frequencyOf(62, 440f)
        var phase = 0.0
        for (i in 0 until n) {
            val t = i.toDouble() / sr
            val env = when {
                t < 0.6 -> 0.0
                t < 0.85 -> (t - 0.6) / 0.25
                else -> 1.0
            }
            phase += 2 * Math.PI * f / sr
            pcm[i] = (0.5 * env * (Math.sin(phase) + 0.4 * Math.sin(2 * phase))).toFloat()
        }
        val got = run(pcm)
        assertEquals("notes: ${got.map { NoteNames.labelOf(it.midi) }}", listOf(62), got.map { it.midi })
        assertTrue("decided at ${got[0].atMs}", got[0].atMs < 1200)
    }
}
