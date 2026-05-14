package com.example.metrognome.analytics

import com.google.firebase.Firebase
import com.google.firebase.analytics.analytics
import com.google.firebase.analytics.logEvent

/**
 * Central analytics event log. All Firebase Analytics calls go through here — nothing
 * else in the codebase imports firebase.analytics directly.
 *
 * Events use snake_case names and Long/String params per Firebase convention.
 * Firebase silently drops events with >25 params or names >40 chars.
 */
object AnalyticsTracker {

    private var metronomeSessionStartMs = 0L

    // ── Metronome ─────────────────────────────────────────────────────────────

    fun logMetronomeStarted(bpm: Int, soundType: Int, timeSig: Int) {
        metronomeSessionStartMs = System.currentTimeMillis()
        Firebase.analytics.logEvent("metronome_started") {
            param("bpm",        bpm.toLong())
            param("sound_type", soundType.toLong())
            param("time_sig",   timeSig.toLong())
        }
    }

    fun logMetronomeStopped() {
        val durationSec = if (metronomeSessionStartMs > 0L)
            (System.currentTimeMillis() - metronomeSessionStartMs) / 1000L else 0L
        metronomeSessionStartMs = 0L
        Firebase.analytics.logEvent("metronome_stopped") {
            param("duration_seconds", durationSec)
        }
    }

    fun logSoundChanged(soundType: Int) {
        Firebase.analytics.logEvent("sound_changed") {
            param("sound_type", soundType.toLong())
        }
    }

    // ── Rhythm game ───────────────────────────────────────────────────────────

    fun logGameStarted(difficulty: String, bpm: Int) {
        Firebase.analytics.logEvent("game_started") {
            param("difficulty", difficulty)
            param("bpm",        bpm.toLong())
        }
    }

    fun logGameCompleted(
        difficulty: String,
        score: Int,
        perfect: Int,
        good: Int,
        almost: Int,
        miss: Int,
        isNewRecord: Boolean
    ) {
        Firebase.analytics.logEvent("game_completed") {
            param("difficulty",  difficulty)
            param("score",       score.toLong())
            param("perfect",     perfect.toLong())
            param("good",        good.toLong())
            param("almost",      almost.toLong())
            param("miss",        miss.toLong())
            param("new_record",  if (isNewRecord) "true" else "false")
        }
    }

    // ── Practice ──────────────────────────────────────────────────────────────

    fun logPracticeStarted(goalMinutes: Int) {
        Firebase.analytics.logEvent("practice_started") {
            param("goal_minutes", goalMinutes.toLong())
        }
    }

    fun logPracticeCompleted(durationMinutes: Int, streak: Int, totalSessions: Int) {
        Firebase.analytics.logEvent("practice_completed") {
            param("duration_minutes", durationMinutes.toLong())
            param("streak",           streak.toLong())
            param("total_sessions",   totalSessions.toLong())
        }
    }

    fun logPracticeCancelled(secondsElapsed: Int, goalSeconds: Int) {
        Firebase.analytics.logEvent("practice_cancelled") {
            param("seconds_elapsed", secondsElapsed.toLong())
            param("goal_seconds",    goalSeconds.toLong())
        }
    }

    // ── BPM Presets ───────────────────────────────────────────────────────────

    fun logPresetSaved(bpm: Int) {
        Firebase.analytics.logEvent("preset_saved") {
            param("bpm", bpm.toLong())
        }
    }

    fun logPresetLoaded(bpm: Int) {
        Firebase.analytics.logEvent("preset_loaded") {
            param("bpm", bpm.toLong())
        }
    }

    // ── Items ─────────────────────────────────────────────────────────────────

    fun logItemUnlocked(itemId: String, itemName: String) {
        Firebase.analytics.logEvent("item_unlocked") {
            param("item_id",   itemId)
            param("item_name", itemName)
        }
    }
}
