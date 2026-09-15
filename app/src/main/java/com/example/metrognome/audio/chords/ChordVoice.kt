package com.example.metrognome.audio.chords

import com.example.metrognome.audio.NoteNames
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * Pure-Kotlin synthesis of a few plucked-sounding notes, for hearing a chord back.
 *
 * Renders a list of [ToneEvent]s (a note, when it starts, how long it holds, how loud) into
 * one 16-bit mono buffer, mixing wherever events overlap. This is the whole sound of the
 * Chord Finder's "hear it" and of the dev tools' test chords; the two differ only in the
 * [ChordTimbre] they ask for and the events they schedule ([ChordPlaybackPlan]). Keeping it
 * free of Android, like [com.example.metrognome.audio.drone.DroneRenderer], is what lets
 * `ChordVoiceTest` check the output against the app's own pitch detector on the JVM.
 *
 * Each note is a sine at the fundamental plus a few decaying partials, with a short attack,
 * a decay to a sustain level (the "pluck") and a release after the hold: the shape of the
 * web chord finder's playback, which the dev liked the sound of. Everything is harmonic,
 * for the drone's reason: an inharmonic series has no single period, and the mic path
 * listens to the test-tone timbre with the tuner.
 */
object ChordVoice {

    const val SAMPLE_RATE = 44_100

    /** One note to sound: [midi], starting [startMs] into the buffer, held for [holdMs], at [gain] (0..1). */
    data class ToneEvent(val midi: Int, val startMs: Int, val holdMs: Int, val gain: Float)

    /**
     * Render [events] at [referenceHz] with [timbre]. The buffer ends [tailMs] after the
     * last hold ends, so a release is never cut. The mix is normalised only if it would
     * clip, so a single quiet note stays quiet and a dense chord cannot distort.
     */
    fun render(
        events: List<ToneEvent>,
        referenceHz: Float,
        timbre: ChordTimbre = ChordTimbre.PLAYBACK,
        tailMs: Int = RELEASE_TAIL_MS,
    ): ShortArray {
        if (events.isEmpty()) return ShortArray(0)
        val endMs = events.maxOf { it.startMs + it.holdMs } + tailMs
        val total = SAMPLE_RATE * endMs / 1000
        val mix = FloatArray(total)
        val partialSum = timbre.partials.sumOf { it.second }.toFloat()
        val attack = SAMPLE_RATE * ATTACK_MS / 1000f
        val decayTau = SAMPLE_RATE * timbre.decayMs / 1000f
        val releaseTau = SAMPLE_RATE * RELEASE_MS / 1000f

        for (e in events) {
            val f = NoteNames.frequencyOf(e.midi, referenceHz).toDouble()
            val start = SAMPLE_RATE * e.startMs / 1000
            val hold = SAMPLE_RATE * e.holdMs / 1000
            val end = min(total, start + hold + SAMPLE_RATE * tailMs / 1000)
            val omega = 2 * PI * f / SAMPLE_RATE
            for (i in start until end) {
                val n = i - start
                // Pluck: rise over the attack, settle exponentially to the sustain level,
                // then die away exponentially once the hold is over.
                val env = when {
                    n < attack -> n / attack
                    n < hold -> timbre.sustain + (1f - timbre.sustain) * exp(-(n - attack) / decayTau)
                    else -> (timbre.sustain + (1f - timbre.sustain) * exp(-(hold - attack) / decayTau)) *
                        exp(-(n - hold) / releaseTau)
                }
                var v = 0.0
                for ((harmonic, amp) in timbre.partials) v += amp * sin(omega * harmonic * n)
                mix[i] += (v / partialSum * env * e.gain).toFloat()
            }
        }

        var peak = 0f
        for (s in mix) peak = maxOf(peak, abs(s))
        val scale = if (peak > CEILING) CEILING / peak else 1f
        return ShortArray(total) { i -> (mix[i] * scale * Short.MAX_VALUE).toInt().toShort() }
    }

    private const val ATTACK_MS = 15
    private const val RELEASE_MS = 60

    /** Buffer added after the last hold so the final release is heard out (~5 time constants). */
    const val RELEASE_TAIL_MS = 320

    /** Normalisation ceiling as a fraction of full scale. */
    private const val CEILING = 0.92f
}

/**
 * The harmonic recipe of a note: partials as (harmonic number, relative amplitude), the
 * level the pluck settles to, and how long it takes to get there.
 */
data class ChordTimbre(
    val partials: List<Pair<Int, Double>>,
    val sustain: Float,
    val decayMs: Int,
) {
    companion object {
        /** The web chord finder's voice: mellow, a little of the guitar in it. What "hear it" plays. */
        val PLAYBACK = ChordTimbre(
            partials = listOf(1 to 1.0, 2 to 0.38, 3 to 0.20, 4 to 0.09),
            sustain = 0.55f,
            decayMs = 350,
        )

        /**
         * The dev tools' test tone: partials bright up to the fifth and no decay, because a
         * phone speaker is far louder above 1 kHz than at a fundamental and the tuner locks
         * on a steady note faster than on a fading one.
         */
        val TEST_TONE = ChordTimbre(
            partials = listOf(1 to 1.0, 2 to 0.60, 3 to 0.40, 4 to 0.25, 5 to 0.15),
            sustain = 1f,
            decayMs = 1,
        )
    }
}
