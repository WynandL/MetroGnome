package com.example.metrognome.adfree

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.provider.Settings

/** Reads the device's clocks for [AdFreeWindow]. */
class SystemTime(context: Context) {
    private val resolver = context.applicationContext.contentResolver

    fun read(): TimeReading = TimeReading(
        wallMs = System.currentTimeMillis(),
        elapsedMs = SystemClock.elapsedRealtime(),
        bootCount = try {
            Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT)
        } catch (_: Exception) {
            TimeReading.UNKNOWN_BOOT
        },
    )
}

/**
 * Saves and loads one [AdFreeWindow] under a prefix. The anchor (elapsed clock and boot) goes in
 * a separate file kept out of backup ([ANCHOR_PREFS], excluded in both backup rule files): it
 * means "this boot of this phone", and restored elsewhere it would be wrong. A window loaded
 * without one gets [AdFreeWindow.NO_ANCHOR] and measures on the wall clock until it re-anchors.
 */
internal object WindowPrefs {
    const val ANCHOR_PREFS = "ad_free_anchors"

    private fun anchors(context: Context) = context.getSharedPreferences(ANCHOR_PREFS, Context.MODE_PRIVATE)

    fun load(context: Context, prefs: SharedPreferences, prefix: String): AdFreeWindow? {
        if (!prefs.contains("${prefix}_start_wall")) return null
        val a = anchors(context)
        val anchored = a.contains("${prefix}_anchor_boot")
        return AdFreeWindow(
            startWallMs = prefs.getLong("${prefix}_start_wall", 0L),
            durationMs = prefs.getLong("${prefix}_duration", 0L),
            anchorElapsedMs = if (anchored) a.getLong("${prefix}_anchor_elapsed", 0L) else 0L,
            anchorBoot = if (anchored) a.getInt("${prefix}_anchor_boot", AdFreeWindow.NO_ANCHOR) else AdFreeWindow.NO_ANCHOR,
            consumedMs = prefs.getLong("${prefix}_consumed", 0L),
        )
    }

    /**
     * Puts [w] (or its removal) into [editor] for the backed-up part, and writes the anchor
     * file itself: with `commit` when [durable], else `apply`.
     */
    fun save(context: Context, editor: SharedPreferences.Editor, prefix: String, w: AdFreeWindow?, durable: Boolean) {
        val a = anchors(context).edit()
        if (w == null) {
            listOf("start_wall", "duration", "consumed").forEach { editor.remove("${prefix}_$it") }
            a.remove("${prefix}_anchor_elapsed").remove("${prefix}_anchor_boot")
        } else {
            editor.putLong("${prefix}_start_wall", w.startWallMs)
            editor.putLong("${prefix}_duration", w.durationMs)
            editor.putLong("${prefix}_consumed", w.consumedMs)
            a.putLong("${prefix}_anchor_elapsed", w.anchorElapsedMs).putInt("${prefix}_anchor_boot", w.anchorBoot)
        }
        // An anchor written by the first build of this feature, beside the rest; never backed up again.
        editor.remove("${prefix}_anchor_elapsed").remove("${prefix}_anchor_boot")
        if (durable) a.commit() else a.apply()
    }

    fun save(context: Context, prefs: SharedPreferences, prefix: String, w: AdFreeWindow?, durable: Boolean) {
        val e = prefs.edit()
        save(context, e, prefix, w, durable)
        if (durable) e.commit() else e.apply()
    }
}
