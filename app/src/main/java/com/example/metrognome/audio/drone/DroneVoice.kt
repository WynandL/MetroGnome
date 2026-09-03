package com.example.metrognome.audio.drone

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * What a drone tone is made of, as pure data.
 *
 * Nothing here knows about Android, AudioTrack or a sample rate. A [DroneTimbre] and a
 * [DroneBlend] compile into a [VoiceLayout] (a flat list of detuned harmonic strands plus
 * the gain that normalises them), and [DroneRenderer] turns that into samples. Keeping the
 * two apart is what makes the whole synthesis testable on the JVM, and it is why adding a
 * timbre is a data edit rather than an audio-thread edit.
 *
 * The three ideas the timbres are built out of:
 *
 *  - **Partials.** A sustained tone is heard as its harmonic series, not its fundamental.
 *    A bare sine gives a musician almost nothing to tune against, because the only cue is
 *    the slow beat against their own fundamental. Adding partials multiplies the places
 *    where beats can be heard, which is exactly what makes a drone useful.
 *  - **Strands.** The same harmonic series sounded two or three times, a few cents apart
 *    and panned outward. This is what "rich" means in a drone: the shimmer is beating
 *    between the strands, not an effect bolted on afterwards.
 *  - **Movement.** Static detuning settles into one fixed beat rate that the ear stops
 *    hearing after a minute. Each side strand's detuning breathes on its own slow LFO so
 *    the texture never freezes.
 *
 * The one rule the whole design bends around: **the centre strand never moves and is never
 * detuned.** Its fundamental is the note, exactly, forever. Movement lives only on the side
 * strands, so the tone stays a usable pitch reference no matter how alive it sounds.
 */

/** One sine component of a strand: [ratio] times the strand frequency, at relative [amp]. */
data class Partial(val ratio: Double, val amp: Double)

/**
 * One detuned copy of a whole harmonic series.
 *
 * @property detuneCents fixed offset from the strand's nominal pitch
 * @property pan         -1 hard left, 0 centre, +1 hard right (equal-power)
 * @property gain        level relative to the centre strand
 * @property moveCents   how far [detuneCents] breathes either side of itself
 * @property moveRateHz  how fast it breathes; deliberately far below 1 Hz
 */
data class StrandSpec(
    val detuneCents: Double,
    val pan: Double,
    val gain: Double,
    val moveCents: Double = 0.0,
    val moveRateHz: Double = 0.0,
)

/** The single always-exact strand every timbre is built around. */
private val CENTRE = StrandSpec(detuneCents = 0.0, pan = 0.0, gain = 1.0)

/**
 * The drone's voices.
 *
 * To add one: append an entry. Settings renders the chip, the caption and the audio from
 * this declaration alone, and [DroneVoiceTest] will check its normalisation automatically.
 */
enum class DroneTimbre(
    val displayName: String,
    /**
     * One line under the chip row, in plain words: what it sounds like, then what it is
     * for. No synthesis vocabulary. "One clean sine", "soft harmonium" and "odd-harmonic"
     * were all tried and all failed on a real reader, who could parse the engineering and
     * still not know which chip to press. The technical description belongs in the KDoc on
     * each entry, which is read by people who are changing the sound rather than choosing it.
     */
    val caption: String,
    val partials: List<Partial>,
    val strands: List<StrandSpec>,
) {
    /**
     * A single sine at the exact note frequency. No partials, no strands, nothing moving.
     * The honest reference: what a tuning fork's fundamental would be if it never decayed.
     */
    PURE(
        displayName = "Pure",
        caption = "A thin, plain tone, like a hearing test. The simplest reference.",
        partials = listOf(Partial(1.0, 1.0)),
        strands = listOf(CENTRE),
    ),

    /**
     * Soft reed-organ / harmonium. Even and odd partials rolling off quickly, three strands
     * a few cents apart. Sits under an instrument for a long practice without fatiguing.
     */
    WARM(
        displayName = "Warm",
        caption = "Soft and organ-like. Best for long practice sessions.",
        partials = listOf(
            Partial(1.0, 1.00),
            Partial(2.0, 0.42),
            Partial(3.0, 0.22),
            Partial(4.0, 0.11),
            Partial(5.0, 0.05),
        ),
        strands = listOf(
            CENTRE,
            StrandSpec(-3.5, pan = -0.55, gain = 0.55, moveCents = 1.2, moveRateHz = 0.071),
            StrandSpec(+3.5, pan = +0.55, gain = 0.55, moveCents = 1.2, moveRateHz = 0.094),
        ),
    ),

    /**
     * Odd-harmonic heavy, like a shawm or a squeezebox reed. The brightest voice, and the
     * one to reach for when the note being tuned is hard to hear against: every extra
     * partial is another place where a beat becomes audible.
     */
    REED(
        displayName = "Reed",
        caption = "Bright and reedy, like an accordion. Easy to hear yourself.",
        partials = listOf(
            Partial(1.0, 1.00),
            Partial(2.0, 0.16),
            Partial(3.0, 0.52),
            Partial(4.0, 0.09),
            Partial(5.0, 0.28),
            Partial(6.0, 0.05),
            Partial(7.0, 0.15),
        ),
        strands = listOf(
            CENTRE,
            StrandSpec(-5.0, pan = -0.60, gain = 0.50, moveCents = 1.8, moveRateHz = 0.053),
            StrandSpec(+5.0, pan = +0.60, gain = 0.50, moveCents = 1.8, moveRateHz = 0.083),
        ),
    ),

    /**
     * A bowed string: a dense, near-sawtooth harmonic series, the sound an orchestra
     * actually tunes to. The richest voice, and the best one for hearing a beat, because
     * every harmonic the player's own instrument produces has one here to beat against.
     *
     * A struck-bowl voice was tried here first and removed. Its stretched partials (an
     * octave 2.2 % sharp, the classic bell inharmonicity) sounded lovely and made the
     * app's own tuner read the tone 13 cents sharp, because an inharmonic series has no
     * single period to find. Beautiful is not the bar for a pitch reference; unambiguous
     * is, and every partial here is an exact integer multiple of the note.
     */
    STRING(
        displayName = "String",
        caption = "A bowed string. The sound an orchestra tunes to.",
        partials = listOf(
            Partial(1.0, 1.00),
            Partial(2.0, 0.50),
            Partial(3.0, 0.33),
            Partial(4.0, 0.24),
            Partial(5.0, 0.17),
            Partial(6.0, 0.12),
            Partial(7.0, 0.08),
            Partial(8.0, 0.05),
        ),
        strands = listOf(
            CENTRE,
            StrandSpec(-4.0, pan = -0.50, gain = 0.58, moveCents = 1.5, moveRateHz = 0.067),
            StrandSpec(+4.0, pan = +0.50, gain = 0.58, moveCents = 1.5, moveRateHz = 0.101),
        ),
    ),
}

/** One pitch a blend sounds, as a frequency ratio above the chosen note. */
data class BlendTone(val ratio: Double, val gain: Double)

/**
 * What else sounds alongside the root.
 *
 * The intervals are **just**, not equal-tempered: 3:2 for the fifth, 2:1 for the octave.
 * That is the whole point of a drone. A beatless just fifth is the thing a wind or string
 * player tunes their own fifth against, and an equal-tempered 1.4983:1 would sit two cents
 * flat of it and beat slowly against itself, which is precisely the cue being listened for.
 */
enum class DroneBlend(
    val displayName: String,
    val caption: String,
    val tones: List<BlendTone>,
) {
    ROOT(
        displayName = "Root",
        caption = "Just the note.",
        tones = listOf(BlendTone(1.0, 1.0)),
    ),
    OCTAVE(
        displayName = "Octave",
        caption = "The note plus the same note higher. Fuller and easier to hear.",
        tones = listOf(BlendTone(1.0, 1.0), BlendTone(2.0, 0.42)),
    ),
    FIFTH(
        displayName = "Fifth",
        caption = "The note plus a perfect fifth. The classic tuning drone.",
        tones = listOf(BlendTone(1.0, 1.0), BlendTone(1.5, 0.48)),
    ),
}

/**
 * One compiled strand: a harmonic series at [frequencyRatio] times the drone's note,
 * offset by [detuneCents], with its gain already split into the two channels.
 */
data class VoiceStrand(
    val frequencyRatio: Double,
    val detuneCents: Double,
    val moveCents: Double,
    val moveRateHz: Double,
    val gainL: Float,
    val gainR: Float,
    val partials: List<Partial>,
)

/**
 * A timbre and a blend, flattened into everything the renderer needs.
 *
 * [normalisation] is the scalar that brings the whole layout to [TARGET_RMS] per channel,
 * so switching timbre or blend changes the colour of the tone and not its loudness. It is
 * computed from summed *power*, not summed amplitude: the partials are mutually
 * incoherent, so their powers add, and normalising on amplitude instead would make the
 * many-partial voices audibly quieter than Pure.
 */
data class VoiceLayout(
    val strands: List<VoiceStrand>,
    val normalisation: Float,
) {
    val oscillatorCount: Int get() = strands.sumOf { it.partials.size }
}

/**
 * Target RMS per channel before the user's level is applied.
 *
 * A sustained tone at a given peak sounds far louder than a click at the same peak, so this
 * sits well below the metronome's levels. It also leaves the headroom that makes the worst
 * case (every oscillator momentarily in phase) still land under full scale.
 */
const val TARGET_RMS = 0.22

/** Compile [timbre] and [blend] into the flat strand list the renderer walks. */
fun buildVoice(timbre: DroneTimbre, blend: DroneBlend): VoiceLayout {
    val strands = blend.tones.flatMap { tone ->
        timbre.strands.map { strand ->
            val theta = (strand.pan.coerceIn(-1.0, 1.0) + 1.0) * PI / 4.0
            val gain = tone.gain * strand.gain
            VoiceStrand(
                frequencyRatio = tone.ratio,
                detuneCents = strand.detuneCents,
                moveCents = strand.moveCents,
                moveRateHz = strand.moveRateHz,
                // Equal-power pan: the two channel gains square-sum to one, so moving a
                // strand outward changes where it sits and not how loud it is.
                gainL = (gain * cos(theta)).toFloat(),
                gainR = (gain * sin(theta)).toFloat(),
                partials = timbre.partials,
            )
        }
    }
    return VoiceLayout(strands, normalisationFor(strands))
}

/** Scalar bringing [strands] to [TARGET_RMS] in whichever channel carries the most power. */
private fun normalisationFor(strands: List<VoiceStrand>): Float {
    var powerL = 0.0
    var powerR = 0.0
    for (strand in strands) {
        for (partial in strand.partials) {
            // A sine of amplitude a carries a^2 / 2 of power.
            val shared = (partial.amp * partial.amp) / 2.0
            powerL += shared * strand.gainL * strand.gainL
            powerR += shared * strand.gainR * strand.gainR
        }
    }
    val loudest = maxOf(powerL, powerR)
    if (loudest <= 0.0) return 0f
    return (TARGET_RMS / sqrt(loudest)).toFloat()
}

/** Frequency ratio for a cents offset, e.g. +1200 cents doubles the frequency. */
fun centsRatio(cents: Double): Double = 2.0.pow(cents / 1200.0)
