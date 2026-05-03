package com.example.metrognome.whats_new

/**
 * Registry of all "What's New" version keys.
 *
 * To add a popup for a future version:
 *   1. Add a new key constant here (e.g. `const val V4 = "v4"`).
 *   2. Append it to [ALL].
 *   3. Add a composable in FeatureIntroOverlay.kt and a branch in WhatsNewOverlayDispatcher.
 *   Nothing else needs to change — the tracker and ViewModel wire it up automatically.
 */
object AppWhatsNew {
    const val V3 = "v3"
    // const val V4 = "v4"

    /** All version keys, oldest first. The first unconfirmed key is shown to the user. */
    val ALL: List<String> = listOf(V3)
}
