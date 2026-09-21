package com.example.metrognome.debug.chords

import android.os.Build
import com.example.metrognome.BuildConfig
import com.example.metrognome.audio.NoteNames
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * DEV ONLY: serialises a [ChordLoopDiagnostic.State] to JSON, at the same granularity the
 * overlay observes (every capture/reading/state change, not just the collapsed per-note
 * fault), so a run can be shared outside the phone instead of transcribed from a screenshot.
 * [ChordLoopDiagnosticOverlay]'s "Copy Log" button is the one caller.
 */
fun ChordLoopDiagnostic.State.toJsonReport(): String {
    val root = JSONObject()
    root.put("device", "${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    root.put("app", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
    root.put("savedAt", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
    root.put("mode", mode.name)
    root.put("status", status.name)
    root.put("message", message)
    root.put("sequence", ChordArpeggioTestTone.description)
    root.put("expectedSymbols", JSONArray(ChordArpeggioTestTone.EXPECTED_SYMBOLS))
    root.put("rounds", JSONArray(rounds.map { it.toJson() }))
    root.put("legato", JSONArray(legato.map { it.toJson() }))
    return root.toString(2)
}

private fun ChordLoopDiagnostic.RoundReport.toJson(): JSONObject = JSONObject().apply {
    put("round", round)
    put("engine", engine.name)
    put("timings", timings.toString())
    put("pass", pass)
    put("diagnosis", diagnosis)
    put("adjustment", adjustment)
    put("chords", JSONArray(chords.map { it.toJson() }))
}

private fun ChordLoopDiagnostic.LegatoReport.toJson(): JSONObject = JSONObject().apply {
    put("engine", engine.name)
    put("pass", pass)
    put("summary", summary)
    put("chords", JSONArray(chords.map { it.toJson() }))
}

private fun ChordLoopDiagnostic.ChordResult.toJson(): JSONObject = JSONObject().apply {
    put("expectedSymbol", expectedSymbol)
    put("expectedNotes", JSONArray(expectedNotes.map { NoteNames.labelOf(it) }))
    put("gotSymbol", gotSymbol)
    put("gotNotes", JSONArray(gotNotes.map { NoteNames.labelOf(it) }))
    put("extras", JSONArray(extras.map { NoteNames.labelOf(it) }))
    put("pass", pass)
    put("notes", JSONArray(notes.map { it.toJson() }))
    put("events", JSONArray(events.map { it.toJson() }))
    // One row per hop as a plain array: at ~86 hops a second the key names would be most of the file.
    put("frameColumns", JSONArray(FRAME_COLUMNS))
    put("frames", JSONArray(frames.map { it.toJson() }))
}

private val FRAME_COLUMNS = listOf(
    "tMs", "rms", "flux", "onset", "pitchHz", "pitchClarity", "residualHz", "residualClarity", "state", "loud", "floor", "note", "newFraction",
)

private fun ChordLoopDiagnostic.FrameRow.toJson(): JSONArray = JSONArray().apply {
    put(tMs)
    put(rms.round(6))
    put(flux.round(3))
    put(onset)
    put(pitchHz?.round(1) ?: JSONObject.NULL)
    put(pitchClarity?.round(3) ?: JSONObject.NULL)
    put(residualHz?.round(1) ?: JSONObject.NULL)
    put(residualClarity?.round(3) ?: JSONObject.NULL)
    put(state.name)
    put(loud)
    put(floor.round(6))
    put(note ?: JSONObject.NULL)
    put(newFraction?.round(3) ?: JSONObject.NULL)
}

private fun Float.round(decimals: Int): Double {
    val scale = Math.pow(10.0, decimals.toDouble())
    return Math.round(this * scale) / scale
}

private fun ChordLoopDiagnostic.NoteResult.toJson(): JSONObject = JSONObject().apply {
    put("expected", NoteNames.labelOf(expected))
    put("fault", fault.name)
    put("captured", captured?.let { NoteNames.labelOf(it) })
    put("captureDelayMs", captureDelayMs)
    put("heard", JSONArray(heardMidis.map { NoteNames.labelOf(it) }))
    put("statesSeen", JSONArray(statesSeen.map { it.name }))
}

private fun ChordLoopDiagnostic.TimelineEvent.toJson(): JSONObject = JSONObject().apply {
    put("tMs", tMs)
    put("kind", kind.name)
    put("value", value)
}
