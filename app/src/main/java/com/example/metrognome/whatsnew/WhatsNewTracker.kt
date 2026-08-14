package com.example.metrognome.whatsnew

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

        // Fresh-install guard: if no version has ever been confirmed (including via the
        // migration above), this is a clean install. Pre-mark EVERY version, including the
        // latest - a brand-new user has no "before" to compare a "NEW IN VERSION X" popup
        // against, and first cold open is the worst place in the whole funnel to spend a
        // blocking modal on one feature out of many they don't know about yet (changed
        // 2026-08-14; used to leave the latest version showing as a de facto feature ad).
        // An upgrading EXISTING user is unaffected: their prefs are never empty here.
        if (shownVersions().isEmpty()) {
            AppWhatsNew.ALL.forEach { markShown(it) }
        }
    }

    private fun shownVersions(): Set<String> =
        prefs.getStringSet(KEY_SHOWN, emptySet()) ?: emptySet()

    fun isShown(versionKey: String): Boolean = versionKey in shownVersions()

    fun markShown(versionKey: String) {
        prefs.edit { putStringSet(KEY_SHOWN, shownVersions() + versionKey) }
    }

    fun resetShown(versionKey: String) {
        prefs.edit { putStringSet(KEY_SHOWN, shownVersions() - versionKey) }
    }

    /**
     * Returns the most recent unshown version key, silently marking any older
     * unshown keys as seen first. This prevents a cascade of back-version popups
     * when a user upgrades across multiple versions in one jump.
     */
    fun pendingKey(allVersions: List<String>): String? {
        val pending = allVersions.filter { !isShown(it) }
        if (pending.isEmpty()) return null
        pending.dropLast(1).forEach { markShown(it) }
        return pending.last()
    }
}
