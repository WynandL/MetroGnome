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
                BpmPreset(obj.getString("name"), obj.getInt("bpm"))
            }
        } catch (_: Exception) { emptyList() }
    }

    fun savePreset(name: String, bpm: Int): Boolean {
        val current = loadPresets().toMutableList()
        if (current.size >= MAX_PRESETS) return false
        current.add(BpmPreset(name.trim().ifEmpty { "♩ $bpm" }, bpm))
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
}
