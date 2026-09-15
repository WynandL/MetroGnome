package com.example.metrognome.theory

import com.example.metrognome.audio.NoteNames

/**
 * Single source of truth for chord theory: naming a set of notes.
 *
 * Pure Kotlin (no Android dependencies), the sibling of [MeterTheory]. The Chord Finder
 * feeds it MIDI notes from either the drawn instruments or the tuner one note at a time,
 * and shows whatever comes back; nothing here knows where a note came from.
 *
 * Design notes:
 *  - A chord type is defined by scale **degrees** ("1 ♭3 5 ♭7"), not by semitone offsets.
 *    The degrees are what let every chord tone be spelled correctly from the root: the
 *    third of E♭ is G, the seventh of B♭7 is A♭ and not G#, and the flattened seventh of
 *    Bdim7 is A♭ (a doubly-flattened B seventh), all of which a semitone table gets wrong.
 *  - The **bass note decides** between readings that share the same pitch classes. C E G A
 *    is C6 when C is lowest and Am7 when A is, exactly as a musician would call it; every
 *    other reading is still offered as an alternative, written as a slash chord if its root
 *    is not the bass. The caller therefore passes real MIDI notes, never bare pitch classes.
 *  - Root spelling follows common practice rather than one rule: flats for E♭, A♭, B♭ and
 *    D♭ major-type chords, sharps for F# and for the minor-type chords players actually
 *    meet (C#m, G#m, F#m). See [rootName].
 *  - A dominant or minor seventh played without its fifth (the everyday guitar voicing) is
 *    still named, marked "(no 5)". Nothing else is guessed at: a set the dictionary cannot
 *    explain in full is reported as [ChordReading.Unnamed] with its intervals from the
 *    bass, which is more useful than a wrong name.
 */
object ChordTheory {

    /** Identify [midiNotes] (any order, octave duplicates fine). Empty input is [ChordReading.Empty]. */
    fun identify(midiNotes: Collection<Int>): ChordReading {
        if (midiNotes.isEmpty()) return ChordReading.Empty
        val sorted = midiNotes.distinct().sorted()
        val bassMidi = sorted.first()
        val pitchClasses = sorted.map { pc(it) }.distinct()

        if (pitchClasses.size == 1) {
            return ChordReading.Single(midi = bassMidi, name = NoteNames.nameOf(bassMidi))
        }

        val matches = matchesFor(pitchClasses.toSet(), bass = pc(bassMidi))

        if (pitchClasses.size == 2) {
            // Real interval between the two lowest distinct pitches, so a tenth reads as one.
            val highMidi = sorted.first { pc(it) != pc(bassMidi) }
            return ChordReading.Dyad(
                lowMidi = bassMidi,
                highMidi = highMidi,
                intervalName = intervalName(highMidi - bassMidi),
                // Only when the root is the bass: an inverted power chord (F5/C for C-F) is
                // technically right and practically confusing beside the interval name.
                powerChord = matches.firstOrNull { it.type.symbol == "5" && !it.isSlash },
            )
        }

        return if (matches.isEmpty()) {
            ChordReading.Unnamed(pitchClasses = pitchClasses, bass = pc(bassMidi))
        } else {
            ChordReading.Identified(best = matches.first(), alternatives = matches.drop(1).take(MAX_ALTERNATIVES))
        }
    }

    /** Every dictionary reading of [set] (relative pitch classes), best first. */
    internal fun matchesFor(set: Set<Int>, bass: Int): List<ChordMatch> {
        val found = ArrayList<ChordMatch>()
        for (root in set) {
            val relative = set.map { (it - root + 12) % 12 }.toSet()
            for (type in ChordDictionary.ALL) {
                if (type.pitchClasses == relative) {
                    found += ChordMatch(root, type, bass, omittedFifth = false)
                } else if (type.canOmitFifth && type.pitchClasses - 7 == relative) {
                    found += ChordMatch(root, type, bass, omittedFifth = true)
                }
            }
        }
        // Stable sort: dictionary order breaks any remaining tie, so simpler names come first.
        return found.sortedBy { it.score }
    }

    /** Name of the interval [semitones] wide, compound intervals named up to two octaves. */
    fun intervalName(semitones: Int): String {
        val n = kotlin.math.abs(semitones)
        return when {
            n <= 24 -> INTERVAL_NAMES[n]
            else -> INTERVAL_NAMES[n % 12].let { if (n % 12 == 0) "Octaves" else "$it, compound" }
        }
    }

    /** Degree label of [pitchClass] measured from [bass], for readings without a chord root. */
    fun degreeFromBass(pitchClass: Int, bass: Int): String = DEGREE_FROM_BASS[(pitchClass - bass + 12) % 12]

    /** Root name for [pitchClass] given whether the chord is minor-type (see class doc). */
    fun rootName(pitchClass: Int, minorType: Boolean): String = when (pitchClass) {
        1 -> if (minorType) "C#" else "D♭"
        3 -> "E♭"
        6 -> "F#"
        8 -> if (minorType) "G#" else "A♭"
        10 -> "B♭"
        else -> NoteNames.nameOf(pitchClass)
    }

    private fun pc(midi: Int) = ((midi % 12) + 12) % 12

    private const val MAX_ALTERNATIVES = 3

    private val INTERVAL_NAMES = arrayOf(
        "Unison", "Minor second", "Major second", "Minor third", "Major third",
        "Perfect fourth", "Tritone", "Perfect fifth", "Minor sixth", "Major sixth",
        "Minor seventh", "Major seventh", "Octave",
        "Minor ninth", "Major ninth", "Minor tenth", "Major tenth", "Perfect eleventh",
        "Augmented eleventh", "Perfect twelfth", "Minor thirteenth", "Major thirteenth",
        "Minor fourteenth", "Major fourteenth", "Two octaves",
    )

    private val DEGREE_FROM_BASS = arrayOf("R", "♭2", "2", "♭3", "3", "4", "♭5", "5", "♭6", "6", "♭7", "7")
}

// ── Degrees ──────────────────────────────────────────────────────────────────────

/**
 * A scale degree relative to a chord root, e.g. "♭3", "#11", "♭♭7".
 *
 * [number] is 1..13 and [alter] the accidental (-2..+2). Ninths, elevenths and
 * thirteenths keep their compound numbers for the label and the letter name, and fold
 * to a pitch class for matching.
 */
data class Degree(val number: Int, val alter: Int) {

    /** Semitones above the root, folded into one octave. */
    val pitchClass: Int get() = (NATURAL_SEMITONES[(number - 1) % 7] + alter + 12) % 12

    /** Letter steps above the root's letter (a third is two steps, a ninth is eight). */
    val letterSteps: Int get() = number - 1

    /** "R" for the root, otherwise accidental + number, e.g. "♭7". */
    val label: String get() = if (number == 1 && alter == 0) "R" else accidental(alter) + number

    override fun toString(): String = label

    companion object {
        private val NATURAL_SEMITONES = intArrayOf(0, 2, 4, 5, 7, 9, 11)

        /** Parse "1", "b3", "♭3", "#5", "bb7", "♭♭7", "13". */
        fun parse(text: String): Degree {
            var alter = 0
            var i = 0
            while (i < text.length) {
                when (text[i]) {
                    '♭', 'b' -> alter--
                    '#', '♯' -> alter++
                    else -> break
                }
                i++
            }
            val number = text.substring(i).toInt()
            require(number in 1..13) { "degree out of range: $text" }
            return Degree(number, alter)
        }

        fun accidental(alter: Int): String = when {
            alter < 0 -> "♭".repeat(-alter)
            alter > 0 -> "#".repeat(alter)
            else -> ""
        }
    }
}

// ── Chord types ──────────────────────────────────────────────────────────────────

/**
 * One chord quality.
 *
 * @property symbol   suffix written after the root ("m7♭5"); empty for a major triad
 * @property name     spoken name ("half-diminished seventh")
 * @property degrees  the chord tones, root first
 * @property rank     complexity used to order competing readings, lower is simpler
 * @property about    one line of context shown under the name
 */
data class ChordType(
    val symbol: String,
    val name: String,
    val degrees: List<Degree>,
    val rank: Int,
    val about: String,
) {
    /** Relative pitch classes of the chord tones (root = 0). */
    val pitchClasses: Set<Int> = degrees.map { it.pitchClass }.toSet()

    /** Has a lowered third: decides the root's spelling (C#m rather than D♭m). */
    val minorType: Boolean = degrees.any { it.number == 3 && it.alter == -1 }

    /**
     * A seventh chord (or taller) with a third and a perfect fifth: the fifth is the tone
     * everyone leaves out on guitar, because the third and seventh carry the chord. Triads,
     * added-tone chords and suspensions do not qualify: dropping their fifth leaves a set
     * some other reading explains better (C D E is not "D7sus2 with no fifth over C").
     */
    val canOmitFifth: Boolean =
        degrees.any { it.number == 3 } &&
            degrees.any { it.number == 7 } &&
            degrees.any { it.number == 5 && it.alter == 0 }

    fun degreeAt(relativePitchClass: Int): Degree? = degrees.firstOrNull { it.pitchClass == relativePitchClass }
}

/** The chord dictionary, ordered simple to complex. Order is the final tie-break in ranking. */
object ChordDictionary {

    private fun type(symbol: String, name: String, degrees: String, rank: Int, about: String) =
        ChordType(symbol, name, degrees.split(' ').map(Degree::parse), rank, about)

    val ALL: List<ChordType> = listOf(
        // ── Dyads and triads ─────────────────────────────────────────────────
        type("5", "power chord", "1 5", 1,
            "Root and fifth only, neither major nor minor. The backbone of rock guitar."),
        type("", "major", "1 3 5", 0,
            "The bright, settled home chord. Root, major third and perfect fifth."),
        type("m", "minor", "1 ♭3 5", 0,
            "The major chord with its third lowered a semitone. That one step turns the mood."),
        type("dim", "diminished", "1 ♭3 ♭5", 1,
            "Two minor thirds stacked. Tense and unstable, it wants to move somewhere."),
        type("aug", "augmented", "1 3 #5", 1,
            "Two major thirds stacked. Symmetrical, so any of its notes can be the root."),
        type("sus2", "suspended second", "1 2 5", 1,
            "The third is replaced by the second, leaving the chord open and undecided."),
        type("sus4", "suspended fourth", "1 4 5", 1,
            "The third is replaced by the fourth, which usually resolves down to it."),

        // ── Sixths and added tones ────────────────────────────────────────────
        type("6", "major sixth", "1 3 5 6", 2,
            "A major triad with the sixth on top. Same notes as the m7 chord a minor third below."),
        type("m6", "minor sixth", "1 ♭3 5 6", 2,
            "A minor triad with a major sixth: the sound of jazz minor and film noir."),
        type("add9", "added ninth", "1 3 5 9", 2,
            "A major triad with the ninth added and no seventh. Wide open, a favourite on guitar."),
        type("m(add9)", "minor added ninth", "1 ♭3 5 9", 2,
            "A minor triad with the ninth added, wistful rather than sad."),
        type("6/9", "six-nine", "1 3 5 6 9", 3,
            "Major with both the sixth and the ninth. The classic final chord of a jazz standard."),

        // ── Sevenths ──────────────────────────────────────────────────────────
        type("maj7", "major seventh", "1 3 5 7", 2,
            "A major triad with the major seventh. Soft and dreamy, at home in bossa nova and soul."),
        type("7", "dominant seventh", "1 3 5 ♭7", 2,
            "A major triad with the flattened seventh. The engine of blues; it pulls to the tonic."),
        type("m7", "minor seventh", "1 ♭3 5 ♭7", 2,
            "A minor triad with the flattened seventh. Mellow, the everyday minor chord of jazz."),
        type("m(maj7)", "minor-major seventh", "1 ♭3 5 7", 3,
            "A minor triad with a major seventh, home chord of harmonic minor. Uneasy, cinematic."),
        type("dim7", "diminished seventh", "1 ♭3 ♭5 ♭♭7", 3,
            "Minor thirds all the way up. Any note can be the root, so it resolves four ways."),
        type("m7♭5", "half-diminished seventh", "1 ♭3 ♭5 ♭7", 3,
            "A diminished triad with a minor seventh. The ii chord of a minor key, written ø7."),
        type("7sus4", "seventh suspended fourth", "1 4 5 ♭7", 3,
            "A dominant seventh with the fourth in place of the third. Funk and gospel live here."),
        type("7sus2", "seventh suspended second", "1 2 5 ♭7", 3,
            "A dominant seventh with the second in place of the third."),
        type("7#5", "augmented seventh", "1 3 #5 ♭7", 3,
            "A dominant seventh with a raised fifth, leaning even harder towards the tonic."),
        type("7♭5", "seventh flat five", "1 3 ♭5 ♭7", 3,
            "A dominant seventh with a lowered fifth. Shares its notes with the 7♭5 a tritone away."),
        type("maj7#5", "augmented major seventh", "1 3 #5 7", 3,
            "An augmented triad with a major seventh. Rare and luminous."),

        // ── Ninths ────────────────────────────────────────────────────────────
        type("9", "dominant ninth", "1 3 5 ♭7 9", 3,
            "A dominant seventh with the ninth. The funk chord."),
        type("maj9", "major ninth", "1 3 5 7 9", 3,
            "A major seventh with the ninth, lush and unhurried."),
        type("m9", "minor ninth", "1 ♭3 5 ♭7 9", 3,
            "A minor seventh with the ninth. Smooth, the sound of a slow jam."),
        type("m(maj9)", "minor-major ninth", "1 ♭3 5 7 9", 4,
            "A minor-major seventh with the ninth. On E, it is the James Bond chord."),
        type("7♭9", "seventh flat nine", "1 3 5 ♭7 ♭9", 4,
            "A dominant seventh with a flattened ninth, dark and Spanish-tinged."),
        type("7#9", "seventh sharp nine", "1 3 5 ♭7 #9", 4,
            "The Hendrix chord: a dominant seventh with a raised ninth, major and minor at once."),
        type("9sus4", "ninth suspended fourth", "1 4 5 ♭7 9", 4,
            "A 7sus4 with the ninth. Often what is meant by an eleventh chord."),
        type("7#11", "seventh sharp eleven", "1 3 5 ♭7 #11", 4,
            "A dominant seventh with a raised eleventh, the lydian dominant sound."),
        type("maj7#11", "major seventh sharp eleven", "1 3 5 7 #11", 4,
            "A major seventh with a raised eleventh. Floating, lydian, film-score bright."),

        // ── Elevenths and thirteenths ─────────────────────────────────────────
        type("11", "eleventh", "1 3 5 ♭7 9 11", 4,
            "A dominant ninth with the eleventh. Usually played without the third."),
        type("m11", "minor eleventh", "1 ♭3 5 ♭7 9 11", 4,
            "A minor ninth with the eleventh, a wide, modal minor sound."),
        type("13", "thirteenth", "1 3 5 ♭7 9 13", 4,
            "A dominant ninth with the thirteenth. The eleventh is left out by convention."),
        type("maj13", "major thirteenth", "1 3 5 7 9 13", 4,
            "A major ninth with the thirteenth, as rich as a major chord gets."),
        type("m13", "minor thirteenth", "1 ♭3 5 ♭7 9 13", 4,
            "A minor ninth with the thirteenth."),
    )
}

// ── Readings ─────────────────────────────────────────────────────────────────────

/**
 * One way of reading a set of notes as a chord: a root, a type, and the bass it sits on.
 *
 * [symbol] is the written name ("Am7/C", "G7(no 5)") and [fullName] the spoken one.
 * [spell] gives the correctly-spelled note name for any tone of the chord, from the root's
 * letter and the tone's degree, so the Chord Finder's note chips agree with the name.
 */
data class ChordMatch(
    val root: Int,
    val type: ChordType,
    val bass: Int,
    val omittedFifth: Boolean,
) {
    val isSlash: Boolean get() = root != bass

    val rootName: String get() = ChordTheory.rootName(root, type.minorType)

    /** Written name: "C", "Am7", "Dm7♭5/A♭", "G7(no 5)". */
    val symbol: String
        get() = buildString {
            append(rootName).append(type.symbol)
            if (omittedFifth) append("(no 5)")
            if (isSlash) append('/').append(spell(bass))
        }

    /** Spoken name: "A minor seventh over C". */
    val fullName: String
        get() = buildString {
            append(rootName).append(' ').append(type.name)
            if (omittedFifth) append(", no fifth")
            if (isSlash) append(" over ").append(spell(bass))
        }

    /** Degree of [pitchClass] in this chord, or null if it is not a chord tone. */
    fun degreeOf(pitchClass: Int): Degree? = type.degreeAt((pitchClass - root + 12) % 12)

    /**
     * Spell [pitchClass] in this chord's context. Letter comes from the root's letter plus the
     * degree's steps; the accidental is whatever closes the gap to the actual pitch class.
     * A pitch class that is not a chord tone falls back to the plain sharp name.
     */
    fun spell(pitchClass: Int): String {
        val (letter, alter) = spelling(pitchClass) ?: return NoteNames.nameOf(pitchClass)
        return LETTERS[letter] + Degree.accidental(alter)
    }

    /**
     * The octave number that goes with [spell] for the note [midi], in scientific pitch
     * notation, where the number follows the *letter*: C♭4 is the pitch B3, and B#3 is the
     * pitch C4. Reading the octave off the MIDI number alone would print "C♭3" for the
     * seventh of D♭7 played at B3, which is a different note.
     */
    fun spelledOctave(midi: Int): Int {
        val pitchClass = ((midi % 12) + 12) % 12
        val (_, alter) = spelling(pitchClass) ?: return NoteNames.octaveOf(midi)
        return NoteNames.octaveOf(midi - alter)
    }

    /** Letter index (0 = C) and accidental for a chord tone, or null if it is not one. */
    private fun spelling(pitchClass: Int): Pair<Int, Int>? {
        val degree = degreeOf(pitchClass) ?: return null
        val rootLetter = LETTERS.indexOf(rootName[0])
        val letter = (rootLetter + degree.letterSteps) % 7
        var alter = (pitchClass - LETTER_PITCH_CLASSES[letter] + 12) % 12
        if (alter > 6) alter -= 12
        return letter to alter
    }

    /** Lower is better: a root in the bass beats a slash chord, complete beats "(no 5)", simple beats complex. */
    internal val score: Int
        get() = type.rank + (if (isSlash) SLASH_PENALTY else 0) + (if (omittedFifth) OMISSION_PENALTY else 0)

    private companion object {
        const val LETTERS = "CDEFGAB"
        val LETTER_PITCH_CLASSES = intArrayOf(0, 2, 4, 5, 7, 9, 11)
        // An omission costs more than a slash: C E A with C in the bass is Am/C (a complete
        // triad, inverted) before it is C6 with no fifth.
        const val SLASH_PENALTY = 10
        const val OMISSION_PENALTY = 12
    }
}

/** What [ChordTheory.identify] concluded about a set of notes. */
sealed interface ChordReading {

    /** Nothing entered yet. */
    data object Empty : ChordReading

    /** One pitch class, possibly in several octaves. */
    data class Single(val midi: Int, val name: String) : ChordReading

    /** Two pitch classes: named as an interval, plus the power chord if that is what it is. */
    data class Dyad(
        val lowMidi: Int,
        val highMidi: Int,
        val intervalName: String,
        val powerChord: ChordMatch?,
    ) : ChordReading

    /** Three or more pitch classes the dictionary explains, best reading first. */
    data class Identified(val best: ChordMatch, val alternatives: List<ChordMatch>) : ChordReading

    /** Three or more pitch classes with no dictionary name. */
    data class Unnamed(val pitchClasses: List<Int>, val bass: Int) : ChordReading
}
