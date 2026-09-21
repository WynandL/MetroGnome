package com.example.metrognome.audio.chords

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import com.example.metrognome.audio.NoteNames
import com.example.metrognome.audio.tuner.AmbientLevel
import com.example.metrognome.audio.tuner.AmbientReport
import com.example.metrognome.audio.tuner.ListeningState
import com.example.metrognome.audio.tuner.Tuner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The onset engine on a microphone: the Android half of [NoteAnalyzer], the way
 * [Tuner] is the Android half of the tuner's chain.
 *
 * Same capture as the tuner (raw `MIC`, no echo cancellation or noise suppression, which
 * would eat a sustained note), one coroutine on [Dispatchers.IO] doing the blocking read
 * and the analysis, and thread-safe flows out. It publishes the same shapes the tuner
 * does, [reading], [amplitude] and [ambient], so the Chords page's mic strip draws either
 * engine without knowing which is running, plus [notes], the events the tracker decides,
 * which is what the Chord Finder actually consumes.
 *
 * The mapping from a frequency to a note goes through [referenceHz] and
 * [calibrationFactor] exactly as in the tuner, so a note is named the same way by both
 * engines and by the Tuner tab.
 */
class NoteCapture {

    companion object {
        private val SAMPLE_RATE_CANDIDATES = intArrayOf(44_100, 48_000, 22_050, 16_000)

        /** One hop per read: the analyzer's hop, so every read is exactly one frame of work. */
        private const val READ_CHUNK = NoteAnalyzer.HOP

        /** Raw-RMS to 0..1 meter scaling, the tuner's value so the two meters agree. */
        private const val AMPLITUDE_GAIN = 1f / 8000f

        /** The cents band the strip's "in tune" tint uses; cosmetic here, the tuner's value. */
        private const val IN_TUNE_CENTS = 3f
    }

    /** A decided note, mapped through the reference pitch and calibration. */
    data class NoteEvent(val midi: Int, val frequency: Float, val clarity: Float, val source: NoteTracker.Source)

    private val _reading = MutableStateFlow<Tuner.Reading?>(null)
    /** What is being heard now, for the live readout, or null. */
    val reading: StateFlow<Tuner.Reading?> = _reading.asStateFlow()

    private val _amplitude = MutableStateFlow(0f)
    val amplitude: StateFlow<Float> = _amplitude.asStateFlow()

    private val _ambient = MutableStateFlow(AmbientReport.Idle)
    /** The tracker's state in the tuner's report shape, for the listening badge. */
    val ambient: StateFlow<AmbientReport> = _ambient.asStateFlow()

    private val _notes = MutableSharedFlow<NoteEvent>(extraBufferCapacity = 16)
    /** Each note the player sounds, as the tracker decides it. */
    val notes: SharedFlow<NoteEvent> = _notes.asSharedFlow()

    @Volatile var referenceHz: Float = 440f
    @Volatile var calibrationFactor: Float = 1f

    @Volatile private var listening = false
    val isListening: Boolean get() = listening

    private var record: AudioRecord? = null
    private var scope: CoroutineScope? = null
    @Volatile private var analyzer: NoteAnalyzer? = null

    /** Per-hop diagnostic sink, applied to the running analyzer and to any started later. Called on the capture thread. */
    @Volatile var trace: ((NoteAnalyzer.Trace) -> Unit)? = null
        set(value) { field = value; analyzer?.trace = value }

    /** Forget the current note and the tracker's recent memory (the user cleared the finder). */
    fun forget() {
        analyzer?.tracker?.forget()
    }

    /** Start listening. Caller must hold RECORD_AUDIO. Safe to call when already listening. */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun start() {
        if (listening) return
        val (rec, sampleRate) = openRecord() ?: run { listening = false; return }
        record = rec
        val an = NoteAnalyzer(sampleRate)
        an.trace = trace
        analyzer = an
        rec.startRecording()
        listening = true

        val captureScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = captureScope
        captureScope.launch {
            val buf = ShortArray(READ_CHUNK)
            val samples = FloatArray(READ_CHUNK)
            try {
                while (isActive) {
                    val read = rec.read(buf, 0, READ_CHUNK)
                    if (read <= 0) { if (read < 0) break else continue }
                    var sumSq = 0.0
                    for (i in 0 until read) {
                        val raw = buf[i].toFloat()
                        sumSq += raw.toDouble() * raw
                        samples[i] = raw / 32768f
                    }
                    _amplitude.value = (sqrt(sumSq / read).toFloat() * AMPLITUDE_GAIN).coerceIn(0f, 1f)
                    an.feed(samples, read) { obs -> publish(obs) }
                }
            } catch (_: Exception) {
                // AudioRecord released mid-read during stop(): expected, end quietly.
            }
        }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        try { record?.stop() } catch (_: IllegalStateException) { }
        record?.release()
        record = null
        analyzer = null
        listening = false
        _amplitude.value = 0f
        _reading.value = null
        _ambient.value = AmbientReport.Idle
    }

    private fun publish(obs: NoteTracker.Observation) {
        val heardHz = obs.heardHz?.let { it * calibrationFactor }
        _reading.value = heardHz?.let { toReading(it, obs.heardClarity) }
        _ambient.value = report(obs.state, heardHz)
        obs.note?.let { n ->
            val hz = n.frequency * calibrationFactor
            _notes.tryEmit(NoteEvent(NoteNames.nearestMidi(hz, referenceHz), hz, n.clarity, n.source))
        }
    }

    private fun toReading(frequency: Float, clarity: Float): Tuner.Reading {
        val midiExact = 69.0 + 12.0 * log2(frequency / referenceHz)
        val nearest = midiExact.roundToInt()
        val cents = ((midiExact - nearest) * 100.0).toFloat()
        return Tuner.Reading(
            frequency = frequency,
            noteName = NoteNames.nameOf(nearest),
            octave = NoteNames.octaveOf(nearest),
            cents = cents,
            clarity = clarity,
            inTune = abs(cents) <= IN_TUNE_CENTS,
        )
    }

    private fun report(state: ListeningState, hz: Float?): AmbientReport {
        val (headline, guidance) = when (state) {
            ListeningState.PROFILING -> "Getting ready" to "Measuring the room's level."
            ListeningState.QUIET -> "Listening" to "Play the chord as an arpeggio."
            ListeningState.NOISE -> "Background sound" to "Sound, but no note in it."
            ListeningState.UNSTABLE -> "Pitch present" to "A pitch, but no note began."
            ListeningState.ACQUIRING -> "Note starting" to "Deciding the note."
            ListeningState.LOCKED -> (hz?.let { "Hearing ${NoteNames.label(it, referenceHz)}" } ?: "Hearing a note") to
                "The next note is heard the moment it starts."
        }
        return AmbientReport(
            state = state,
            headline = headline,
            guidance = guidance,
            ambientLevel = AmbientLevel.QUIET,
            candidateHz = hz,
            stabilityCents = Float.NaN,
            locked = state == ListeningState.LOCKED,
        )
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun openRecord(): Pair<AudioRecord, Int>? {
        for (rate in SAMPLE_RATE_CANDIDATES) {
            val minBuf = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
            if (minBuf <= 0) continue
            val rec = try {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    rate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    minBuf.coerceAtLeast(READ_CHUNK * 16),
                )
            } catch (_: Exception) {
                null
            }
            if (rec != null && rec.state == AudioRecord.STATE_INITIALIZED) return rec to rate
            rec?.release()
        }
        return null
    }
}
