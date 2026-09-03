package com.example.metrognome.audio.drone

import com.example.metrognome.audio.NoteNames
import kotlin.math.sqrt
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the drone's voice specification and note maths.
 *
 * These check the invariants the audio path is allowed to assume, so a future timbre
 * cannot quietly break them: every voice keeps an exact centre strand, every voice lands
 * on the same loudness, and every blend interval is the just ratio it claims to be.
 */
class DroneVoiceTest {

    // ── The centre-strand invariant ─────────────────────────────────────────────

    /**
     * The reason the drone can be used as a pitch reference at all: whatever a timbre does
     * with detuning and movement, one strand sits on the note exactly and never moves.
     */
    @Test
    fun everyTimbreKeepsOneExactUnmovingStrand() {
        DroneTimbre.entries.forEach { timbre ->
            val exact = timbre.strands.filter {
                it.detuneCents == 0.0 && it.moveCents == 0.0
            }
            assertEquals("${timbre.name} must have exactly one exact strand", 1, exact.size)
            assertEquals("${timbre.name}'s exact strand must be centred", 0.0, exact.first().pan, 0.0)
            assertEquals("${timbre.name}'s exact strand must lead", 1.0, exact.first().gain, 0.0)
        }
    }

    @Test
    fun everyTimbreStartsOnItsFundamental() {
        DroneTimbre.entries.forEach { timbre ->
            val first = timbre.partials.first()
            assertEquals("${timbre.name} must start at 1x", 1.0, first.ratio, 1e-9)
            assertEquals("${timbre.name}'s fundamental must lead", 1.0, first.amp, 1e-9)
            assertTrue(
                "${timbre.name} must not put a partial above its fundamental",
                timbre.partials.all { it.amp <= 1.0 },
            )
        }
    }

    @Test
    fun detunedStrandsAreMirroredAroundTheCentre() {
        DroneTimbre.entries.forEach { timbre ->
            val sum = timbre.strands.sumOf { it.detuneCents }
            assertEquals("${timbre.name}'s detuning must balance", 0.0, sum, 1e-9)
            val pan = timbre.strands.sumOf { it.pan }
            assertEquals("${timbre.name}'s panning must balance", 0.0, pan, 1e-9)
        }
    }

    @Test
    fun movingStrandsAllBreatheAtDifferentRates() {
        DroneTimbre.entries.forEach { timbre ->
            val rates = timbre.strands.map { it.moveRateHz }.filter { it > 0.0 }
            assertEquals(
                "${timbre.name}'s strands must not share a movement rate",
                rates.size, rates.distinct().size,
            )
            assertTrue(
                "${timbre.name}'s movement must stay well below a perceptible vibrato",
                rates.all { it < 0.5 },
            )
        }
    }

    // ── Normalisation ───────────────────────────────────────────────────────────

    /**
     * Switching timbre must change the colour of the tone and not its loudness, or the
     * comparison a musician is making gets contaminated by a level change.
     */
    @Test
    fun everyVoiceNormalisesToTheSameLoudness() {
        forEveryVoice { timbre, blend, layout ->
            var powerL = 0.0
            var powerR = 0.0
            layout.strands.forEach { strand ->
                strand.partials.forEach { partial ->
                    val shared = partial.amp * partial.amp / 2.0
                    val gain = layout.normalisation.toDouble()
                    powerL += shared * (strand.gainL * gain) * (strand.gainL * gain)
                    powerR += shared * (strand.gainR * gain) * (strand.gainR * gain)
                }
            }
            val loudest = sqrt(maxOf(powerL, powerR))
            assertEquals("$timbre/$blend should reach the target RMS", TARGET_RMS, loudest, 1e-6)
        }
    }

    /**
     * Equal-power panning: however far a strand is pushed to one side, the two channel
     * gains still square-sum to the gain it was asked for, so moving a strand outward
     * changes where it sits and not how loud the voice is.
     */
    @Test
    fun panningPreservesEachStrandsGain() {
        DroneTimbre.entries.forEach { timbre ->
            val layout = buildVoice(timbre, DroneBlend.ROOT)
            assertEquals(timbre.strands.size, layout.strands.size)
            timbre.strands.forEachIndexed { index, spec ->
                val strand = layout.strands[index]
                val power = strand.gainL * strand.gainL + strand.gainR * strand.gainR
                assertEquals(
                    "${timbre.name} strand $index changed level when panned",
                    spec.gain * spec.gain, power.toDouble(), 1e-6,
                )
            }
        }
    }

    // ── Blends ──────────────────────────────────────────────────────────────────

    @Test
    fun blendsUseJustIntervalsNotTemperedOnes() {
        assertEquals(listOf(1.0), DroneBlend.ROOT.tones.map { it.ratio })
        assertEquals(listOf(1.0, 2.0), DroneBlend.OCTAVE.tones.map { it.ratio })
        assertEquals(listOf(1.0, 1.5), DroneBlend.FIFTH.tones.map { it.ratio })

        // An equal-tempered fifth is about two cents narrow of 3:2; sounding that instead
        // would beat against the interval the musician is trying to hear as beatless.
        val tempered = Math.pow(2.0, 7.0 / 12.0)
        assertNotEquals(1.5, tempered, 1e-4)
    }

    @Test
    fun everyBlendLeadsWithItsRoot() {
        DroneBlend.entries.forEach { blend ->
            assertEquals("${blend.name} must start on the root", 1.0, blend.tones.first().ratio, 0.0)
            assertEquals("${blend.name}'s root must lead", 1.0, blend.tones.first().gain, 0.0)
            assertTrue(
                "${blend.name} must not let a companion tone outweigh the root",
                blend.tones.drop(1).all { it.gain < 1.0 },
            )
        }
    }

    @Test
    fun blendMultipliesTheStrandCount() {
        val root = buildVoice(DroneTimbre.WARM, DroneBlend.ROOT)
        val fifth = buildVoice(DroneTimbre.WARM, DroneBlend.FIFTH)
        assertEquals(DroneTimbre.WARM.strands.size, root.strands.size)
        assertEquals(root.strands.size * 2, fifth.strands.size)
        assertTrue(fifth.strands.any { it.frequencyRatio == 1.5 })
    }

    // ── Note maths ──────────────────────────────────────────────────────────────

    @Test
    fun midiToFrequencyIsTheInverseOfFrequencyToMidi() {
        for (midi in DroneState.MIN_MIDI..DroneState.MAX_MIDI) {
            val hz = NoteNames.frequencyOf(midi)
            assertEquals("MIDI $midi did not round-trip", midi, NoteNames.nearestMidi(hz))
        }
        assertEquals(440f, NoteNames.frequencyOf(69), 1e-3f)
        assertEquals(220f, NoteNames.frequencyOf(57), 1e-3f)
        assertEquals("A3", NoteNames.labelOf(57))
    }

    @Test
    fun theDroneFollowsTheTunersReferencePitch() {
        val state = DroneState(midi = 57)
        assertEquals(220f, state.frequencyHz(440f), 1e-3f)
        assertEquals(221f, state.frequencyHz(442f), 1e-3f)
    }

    @Test
    fun octaveShiftsStopAtTheEndsInsteadOfChangingNote() {
        val lowest = DroneState(midi = DroneState.MIN_MIDI)
        assertEquals(lowest, lowest.shiftedOctave(-1))
        assertFalse(lowest.canOctaveDown)
        assertEquals(DroneState.MIN_MIDI + 12, lowest.shiftedOctave(1).midi)

        val highest = DroneState(midi = DroneState.MAX_MIDI)
        assertEquals(highest, highest.shiftedOctave(1))
        assertFalse(highest.canOctaveUp)
    }

    @Test
    fun everyNoteIsReachableInEveryOctave() {
        for (octave in DroneState.OCTAVES) {
            val base = DroneState(midi = (octave + 1) * 12)
            for (pitchClass in 0..11) {
                val moved = base.withPitchClass(pitchClass)
                assertEquals(
                    "pitch class $pitchClass unreachable in octave $octave",
                    pitchClass, moved.pitchClass,
                )
                assertEquals("octave changed picking a note", octave, moved.octave)
            }
        }
    }

    private fun forEveryVoice(check: (DroneTimbre, DroneBlend, VoiceLayout) -> Unit) {
        DroneTimbre.entries.forEach { timbre ->
            DroneBlend.entries.forEach { blend ->
                check(timbre, blend, buildVoice(timbre, blend))
            }
        }
    }
}
