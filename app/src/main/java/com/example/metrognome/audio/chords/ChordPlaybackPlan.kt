package com.example.metrognome.audio.chords

import com.example.metrognome.audio.chords.ChordVoice.ToneEvent
import kotlin.math.sqrt

/**
 * How fast "hear it" walks through the chord. The user's choice; persisted by the
 * Chord Finder's ViewModel.
 *
 * [QUICK] is the web chord finder's pace, a ripple. [SLOW] gives a beginner time to hear
 * each note land before the next, and to sing or find it on the instrument.
 */
enum class ChordPlaybackPace(
    val displayName: String,
    /** Time between one arpeggio note starting and the next. */
    val stepMs: Int,
    /** How long each arpeggio note holds before its release. */
    val noteHoldMs: Int,
    /** How long the chord holds when all its notes sound together at the end. */
    val chordHoldMs: Int,
) {
    SLOW("Slow", stepMs = 700, noteHoldMs = 950, chordHoldMs = 1_800),
    QUICK("Quick", stepMs = 320, noteHoldMs = 550, chordHoldMs = 1_400),
}

/** Builds the [ToneEvent] lists that [ChordVoice] renders; the "what" to its "how". */
object ChordPlaybackPlan {

    /**
     * The arpeggio from the bass up, one note per [ChordPlaybackPace.stepMs], then the whole
     * chord together after a short breath. The arpeggio is deliberate rather than a strum:
     * it is how a chord's notes are easiest to hear one at a time, and it is what the mic
     * path listens for, so hearing one teaches playing one.
     */
    fun arpeggioThenChord(midiNotes: Collection<Int>, pace: ChordPlaybackPace): List<ToneEvent> {
        val sorted = midiNotes.distinct().sorted()
        if (sorted.isEmpty()) return emptyList()
        val events = ArrayList<ToneEvent>(sorted.size * 2)
        sorted.forEachIndexed { i, midi ->
            events += ToneEvent(midi, startMs = i * pace.stepMs, holdMs = pace.noteHoldMs, gain = NOTE_GAIN)
        }
        val together = sorted.size * pace.stepMs + BREATH_MS
        // Power-summed so the chord is about as loud as one note, not n times louder.
        val chordGain = NOTE_GAIN / sqrt(sorted.size.toFloat())
        sorted.forEach { midi ->
            events += ToneEvent(midi, startMs = together, holdMs = pace.chordHoldMs, gain = chordGain)
        }
        return events
    }

    /** Total sounding time of [events], release tail included: how long the "hear it" button stays lit. */
    fun durationMs(events: List<ToneEvent>): Int =
        if (events.isEmpty()) 0 else events.maxOf { it.startMs + it.holdMs } + ChordVoice.RELEASE_TAIL_MS

    private const val NOTE_GAIN = 0.6f
    private const val BREATH_MS = 150
}
