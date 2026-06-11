package com.example.metrognome.speedtrainer

import android.content.Context
import androidx.core.content.edit

class SpeedTrainerPrefs(context: Context) {

    private val prefs = context.getSharedPreferences("speed_trainer", Context.MODE_PRIVATE)

    fun saveConfig(config: SpeedTrainerConfig) {
        prefs.edit {
            putInt("start_bpm", config.startBpm)
            putInt("target_bpm", config.targetBpm)
            putFloat("step_size", config.stepSize)
            putString("increment_mode", config.incrementMode.name)
            putInt("bars_per_step", config.barsPerStep)
            putInt("repeats_per_step", config.repeatsPerStep)
            putInt("auto_advance_window_ms", config.autoAdvanceWindowMs)
        }
    }

    fun loadConfig(): SpeedTrainerConfig = SpeedTrainerConfig(
        startBpm = prefs.getInt("start_bpm", 60),
        targetBpm = prefs.getInt("target_bpm", 120),
        stepSize = prefs.getFloat("step_size", 5f),
        incrementMode = runCatching {
            SpeedTrainerConfig.IncrementMode.valueOf(
                prefs.getString("increment_mode", "FIXED") ?: "FIXED"
            )
        }.getOrDefault(SpeedTrainerConfig.IncrementMode.FIXED),
        barsPerStep = prefs.getInt("bars_per_step", 4),
        repeatsPerStep = prefs.getInt("repeats_per_step", 1),
        autoAdvanceWindowMs = prefs.getInt("auto_advance_window_ms", 30),
    )

    fun saveReachedBpm(startBpm: Int, targetBpm: Int, reachedBpm: Int) {
        prefs.edit { putInt("reached_${startBpm}_${targetBpm}", reachedBpm) }
    }

    fun loadReachedBpm(startBpm: Int, targetBpm: Int): Int? {
        val key = "reached_${startBpm}_${targetBpm}"
        return if (prefs.contains(key)) prefs.getInt(key, startBpm) else null
    }

    /**
     * Every saved "reached BPM" personal best, keyed "start_target" → reached BPM.
     * These are append-only records (a session only ever raises a value), so they are
     * part of the user's portable progress.
     */
    fun allReachedRecords(): Map<String, Int> =
        prefs.all.entries
            .filter { it.key.startsWith("reached_") && it.value is Int }
            .associate { it.key.removePrefix("reached_") to (it.value as Int) }
}
