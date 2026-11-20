package com.example.metrognome.presets

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "bpm_presets"
private const val KEY_PRESETS = "presets_json"
const val MAX_PRESETS = 10

class BpmPresetsManager(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun loadPresets(): List<BpmPreset> {
        val json = prefs.getString(KEY_PRESETS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                val bpm = obj.getInt("bpm")
                BpmPreset(legacyName(obj.getString("name")), bpm)
            }
        } catch (_: Exception) { emptyList() }
    }

    fun savePreset(name: String, bpm: Int): Boolean {
        val current = loadPresets().toMutableList()
        if (current.size >= MAX_PRESETS) return false
        current.add(BpmPreset(name.trim(), bpm))
        persist(current)
        return true
    }

    fun deletePreset(index: Int) {
        val current = loadPresets().toMutableList()
        if (index !in current.indices) return
        current.removeAt(index)
        persist(current)
    }

    private fun persist(presets: List<BpmPreset>) {
        val arr = JSONArray()
        presets.forEach { p ->
            arr.put(JSONObject().apply { put("name", p.name); put("bpm", p.bpm) })
        }
        prefs.edit { putString(KEY_PRESETS, arr.toString()) }
    }

    fun debugClear() = prefs.edit { remove(KEY_PRESETS) }

    /**
     * Before 2026-11-20 an unnamed preset was saved as a quarter-note character plus its BPM.
     * Those read back as unnamed (blank), and a note character typed in front of a name is
     * dropped, so the pill draws the app's own note mark instead. The cleaned name is written
     * back the next time the list is saved.
     */
    private fun legacyName(name: String): String {
        val t = name.trim()
        if (!t.startsWith(LEGACY_NOTE)) return t
        val rest = t.removePrefix(LEGACY_NOTE).trim()
        return if (rest.all { it.isDigit() }) "" else rest
    }

    private companion object {
        const val LEGACY_NOTE = "♩" // the quarter-note character
    }
}
