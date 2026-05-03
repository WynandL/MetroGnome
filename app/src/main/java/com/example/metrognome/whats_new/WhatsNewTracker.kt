package com.example.metrognome.whats_new

import android.content.Context
import androidx.core.content.edit

/**
 * Persists which "What's New" version popups the user has already confirmed.
 *
 * Each version is identified by a stable string key (e.g. "v3", "v4").
 * Confirmed keys are stored as a StringSet so old confirmations are never lost
 * when new version keys are added.
 *
 * Uses its own SharedPreferences file ("whats_new") separate from other prefs
 * so it is never accidentally wiped by dev reset tools.
 */
class WhatsNewTracker(context: Context) {

    private val prefs = context.getSharedPreferences("whats_new", Context.MODE_PRIVATE)
    private val KEY_SHOWN = "shown_versions"

    init {
        // One-time migration: port the old single-key approach from MetroItemTracker
        // (stored as "v3_intro_shown" boolean in "metro_cosmetics") into this tracker.
        val legacyPrefs = context.getSharedPreferences("metro_cosmetics", Context.MODE_PRIVATE)
        if (legacyPrefs.contains("v3_intro_shown")) {
            if (legacyPrefs.getBoolean("v3_intro_shown", false) && !isShown("v3")) {
                markShown("v3")
            }
            legacyPrefs.edit { remove("v3_intro_shown") }
        }
    }

    private fun shownVersions(): Set<String> =
        prefs.getStringSet(KEY_SHOWN, emptySet()) ?: emptySet()

    fun isShown(versionKey: String): Boolean = versionKey in shownVersions()

    fun markShown(versionKey: String) {
        prefs.edit { putStringSet(KEY_SHOWN, shownVersions() + versionKey) }
    }

    /**
     * Returns the first key in [allVersions] that the user hasn't confirmed yet,
     * or null if all have been seen. Pass [AppWhatsNew.ALL] here.
     */
    fun pendingKey(allVersions: List<String>): String? =
        allVersions.firstOrNull { !isShown(it) }
}
