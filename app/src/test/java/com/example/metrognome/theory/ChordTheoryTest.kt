package com.example.metrognome.theory

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [ChordTheory]: naming, ranking by bass, spelling, and the shape of every
 * reading the Chord Finder can show.
 */
class ChordTheoryTest {

    // MIDI: C3 = 48. Helpers spell the chords the way a player would enter them.
    private val C3 = 48; private val E3 = 52; private val G3 = 55; private val A3 = 57
    private val Bb3 = 58; private val B3 = 59; private val C4 = 60; private val D4 = 62
    private val Eb3 = 51; private val Gb3 = 54; private val E2 = 40; private val Ab3 = 56

    private fun best(vararg midi: Int): ChordMatch =
        (ChordTheory.identify(midi.toList()) as ChordReading.Identified).best

    private fun symbol(vararg midi: Int) = best(*midi).symbol

    // --- basic naming ---

    @Test
    fun triadsAreNamed() {
        assertEquals("C", symbol(C3, E3, G3))
        assertEquals("Cm", symbol(C3, Eb3, G3))
        assertEquals("Cdim", symbol(C3, Eb3, Gb3))
        assertEquals("Caug", symbol(C3, E3, Ab3))
        assertEquals("Csus2", symbol(C3, D4, G3))
        assertEquals("Csus4", symbol(C3, C3 + 5, G3))
    }

    @Test
    fun seventhsAreNamed() {
        assertEquals("Cmaj7", symbol(C3, E3, G3, B3))
        assertEquals("C7", symbol(C3, E3, G3, Bb3))
        assertEquals("Cm7", symbol(C3, Eb3, G3, Bb3))
        assertEquals("Cm7♭5", symbol(C3, Eb3, Gb3, Bb3))
        assertEquals("Cdim7", symbol(C3, Eb3, Gb3, A3))
        assertEquals("Cm(maj7)", symbol(C3, Eb3, G3, B3))
    }

    @Test
    fun extendedChordsAreNamed() {
        assertEquals("C9", symbol(C3, E3, G3, Bb3, D4))
        assertEquals("Cmaj9", symbol(C3, E3, G3, B3, D4))
        assertEquals("C7#9", symbol(C3, E3, G3, Bb3, Eb3 + 12))
        assertEquals("C6/9", symbol(C3, E3, G3, A3, D4))
        assertEquals("C13", symbol(C3, E3, G3, Bb3, D4, A3 + 12))
    }

    @Test
    fun octaveDoublingsDoNotChangeTheChord() {
        assertEquals("C", symbol(C3, E3, G3, C4, E3 + 12, G3 + 12))
    }

    @Test
    fun inputOrderDoesNotMatter() {
        assertEquals(symbol(C3, E3, G3, B3), symbol(B3, G3, E3, C3))
    }

    // --- the bass decides ---

    @Test
    fun bassNoteChoosesBetweenEquivalentReadings() {
        // C E G A: C6 with C lowest, Am7 with A lowest.
        assertEquals("C6", symbol(C3, E3, G3, A3))
        assertEquals("Am7", symbol(A3 - 12, C3, E3, G3))
    }

    @Test
    fun inversionsAreWrittenAsSlashChords() {
        assertEquals("C/E", symbol(E2, C3, G3))
        assertEquals("C/G", symbol(G3 - 12, C3, E3))
        assertEquals("C major over E", best(E2, C3, G3).fullName)
    }

    @Test
    fun theOtherReadingIsOfferedAsAnAlternative() {
        val reading = ChordTheory.identify(listOf(C3, E3, G3, A3)) as ChordReading.Identified
        assertTrue(reading.alternatives.any { it.symbol == "Am7/C" })
    }

    @Test
    fun symmetricalChordsResolveToTheBass() {
        // An augmented triad is three roots at once; the bass names it.
        assertEquals("Eaug", symbol(E3, Ab3, C4))
        assertEquals("E♭dim7", symbol(Eb3, Gb3, A3, C4))
    }

    // --- omitted fifths ---

    @Test
    fun seventhChordsWithoutTheFifthAreStillNamed() {
        assertEquals("C7(no 5)", symbol(C3, E3, Bb3))
        assertEquals("Cm7(no 5)", symbol(C3, Eb3, Bb3))
        assertEquals("C dominant seventh, no fifth", best(C3, E3, Bb3).fullName)
    }

    @Test
    fun aCompleteTriadBeatsAnOmittedFifth() {
        // C E A with C in the bass is Am/C before it is C6(no 5).
        assertEquals("Am/C", symbol(C3, E3, A3))
    }

    @Test
    fun triadsNeverLoseTheirFifth() {
        // C D E is not "Cadd9(no 5)"; it has no name.
        assertTrue(ChordTheory.identify(listOf(C3, D4 - 12, E3)) is ChordReading.Unnamed)
    }

    // --- spelling ---

    @Test
    fun chordTonesAreSpelledFromTheRoot() {
        val ebMajor = best(Eb3, G3, Bb3)
        assertEquals("E♭", ebMajor.spell(3))
        assertEquals("G", ebMajor.spell(7))
        assertEquals("B♭", ebMajor.spell(10))

        val bbSeven = best(Bb3 - 12, D4 - 12, C3 + 5, Ab3)
        assertEquals("A♭", bbSeven.spell(8))          // not G#

        val bDim7 = best(B3 - 12, D4 - 12, C3 + 5, Ab3)
        assertEquals("Bdim7", bDim7.symbol)
        assertEquals("A♭", bDim7.spell(8))            // the doubly-flattened seventh of B
    }

    @Test
    fun minorTypeRootsPreferSharps() {
        assertEquals("C#m", symbol(C3 + 1, E3, Ab3))
        assertEquals("D♭", symbol(C3 + 1, C3 + 5, Ab3))
        assertEquals("G#m", symbol(Ab3, B3, Eb3 + 12))
        assertEquals("A♭", symbol(Ab3, C4, Eb3 + 12))
        assertEquals("F#m", symbol(Gb3, A3, C3 + 13))
    }

    @Test
    fun degreesLabelEachTone() {
        val c7 = best(C3, E3, G3, Bb3)
        assertEquals("R", c7.degreeOf(0)?.label)
        assertEquals("3", c7.degreeOf(4)?.label)
        assertEquals("5", c7.degreeOf(7)?.label)
        assertEquals("♭7", c7.degreeOf(10)?.label)
        assertNull(c7.degreeOf(1))

        val cHendrix = best(C3, E3, G3, Bb3, Eb3 + 12)
        assertEquals("#9", cHendrix.degreeOf(3)?.label)
    }

    // --- the small readings ---

    @Test
    fun emptySingleAndDyadReadings() {
        assertEquals(ChordReading.Empty, ChordTheory.identify(emptyList()))

        val single = ChordTheory.identify(listOf(C3, C4)) as ChordReading.Single
        assertEquals("C", single.name)
        assertEquals(C3, single.midi)

        val fifth = ChordTheory.identify(listOf(C3, G3)) as ChordReading.Dyad
        assertEquals("Perfect fifth", fifth.intervalName)
        assertEquals("C5", fifth.powerChord?.symbol)

        val fourth = ChordTheory.identify(listOf(C3, C3 + 5)) as ChordReading.Dyad
        assertEquals("Perfect fourth", fourth.intervalName)
        assertNull(fourth.powerChord)                 // F5/C is not offered

        val tenth = ChordTheory.identify(listOf(C3, E3 + 12)) as ChordReading.Dyad
        assertEquals("Major tenth", tenth.intervalName)
    }

    @Test
    fun unnamedSetsReportIntervalsFromTheBass() {
        val cluster = ChordTheory.identify(listOf(C3, C3 + 1, D4 - 12)) as ChordReading.Unnamed
        assertEquals(0, cluster.bass)
        assertEquals("♭2", ChordTheory.degreeFromBass(1, cluster.bass))
    }

    // --- dictionary integrity ---

    @Test
    fun everyTypeStartsOnTheRootAndHasDistinctTones() {
        ChordDictionary.ALL.forEach { type ->
            assertEquals("${type.symbol} must start on the root", Degree(1, 0), type.degrees.first())
            assertEquals("${type.symbol} has a repeated pitch class",
                type.degrees.size, type.pitchClasses.size)
        }
    }

    @Test
    fun noTwoTypesShareAPitchClassSet() {
        val seen = HashMap<Set<Int>, String>()
        ChordDictionary.ALL.forEach { type ->
            val clash = seen.put(type.pitchClasses, type.symbol)
            assertNull("${type.symbol} and $clash have the same tones", clash)
        }
    }

    @Test
    fun everyTypeRoundTripsThroughIdentify() {
        ChordDictionary.ALL.forEach { type ->
            val notes = type.degrees.map { C3 + it.pitchClass + (if (it.number >= 9) 12 else 0) }
            val reading = ChordTheory.identify(notes)
            assertTrue("${type.symbol} was not identified", reading is ChordReading.Identified || reading is ChordReading.Dyad)
            val symbol = when (reading) {
                is ChordReading.Identified -> reading.best.symbol
                is ChordReading.Dyad -> reading.powerChord?.symbol
                else -> null
            }
            assertEquals("C${type.symbol}", symbol)
        }
    }
}
