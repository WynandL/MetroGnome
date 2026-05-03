package com.example.metrognome.dev

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.metrognome.BuildConfig

/**
 * Developer Easter Egg — tap [tapCount] times within [windowMs] ms to toggle dev mode.
 *
 * Works in both debug (always active) and release builds (toggle via rapid taps).
 * State persists across app restarts via SharedPreferences.
 *
 * ── Usage ─────────────────────────────────────────────────────────────────────
 *
 * Wrap any composable that the user can tap (e.g. the About section):
 *
 *   var isDevMode by remember { mutableStateOf(DevEasterEgg.isDevModeActive(context)) }
 *
 *   DevTapTarget(onToggled = { isDevMode = it }) {
 *       Text("Version 1.0")
 *   }
 *
 *   if (isDevMode) {
 *       // dev-only UI
 *   }
 *
 * To copy to another project: update the package name and BuildConfig import only.
 */

// ── State object ──────────────────────────────────────────────────────────────

object DevEasterEgg {

    private const val PREFS_NAME  = "dev_easter_egg"
    private const val KEY_ENABLED = "dev_mode_enabled"

    /**
     * True when dev mode is active — always true in debug builds,
     * true in release when manually unlocked via rapid taps.
     */
    fun isDevModeActive(context: Context): Boolean =
        BuildConfig.DEBUG || isManuallyEnabled(context)

    /** True only when the user has manually unlocked dev mode in a release build. */
    fun isManuallyEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    /**
     * Toggles the manual dev-mode flag and returns the new state.
     * Internal — called by [DevTapTarget].
     */
    internal fun toggle(context: Context): Boolean {
        val next = !isManuallyEnabled(context)
        prefs(context).edit().putBoolean(KEY_ENABLED, next).apply()
        return next
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

// ── Composable tap wrapper ────────────────────────────────────────────────────

/**
 * Invisible tap detector. Wrap your About / version text with this composable.
 * After [tapCount] taps within [windowMs] milliseconds the manual dev-mode flag
 * is toggled, a toast is shown, and [onToggled] is called with the new state.
 *
 * Calling it again with the same speed disables dev mode — the toggle is symmetric.
 *
 * @param tapCount       Number of taps needed to trigger the toggle (default 10).
 * @param windowMs       Time window in which all taps must land (default 3 000 ms).
 * @param toastEnabled   Show a short Toast on toggle (default true).
 * @param enabledMessage Toast text when dev mode is turned ON.
 * @param disabledMessage Toast text when dev mode is turned OFF.
 * @param onToggled      Called with the new [DevEasterEgg.isDevModeActive] value.
 */
@Composable
fun DevTapTarget(
    modifier: Modifier = Modifier,
    tapCount: Int = 10,
    windowMs: Long = 3_000L,
    toastEnabled: Boolean = true,
    enabledMessage: String = "Developer mode enabled 🛠️",
    disabledMessage: String = "Developer mode disabled",
    onToggled: (enabled: Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val tapTimes = remember { ArrayDeque<Long>() }

    Box(
        modifier = modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,          // no ripple — invisible to casual users
        ) {
            val now = System.currentTimeMillis()
            tapTimes.removeAll { now - it > windowMs }
            tapTimes.addLast(now)

            if (tapTimes.size >= tapCount) {
                tapTimes.clear()
                val enabled = DevEasterEgg.toggle(context)
                if (toastEnabled) {
                    Toast.makeText(
                        context,
                        if (enabled) enabledMessage else disabledMessage,
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                onToggled(DevEasterEgg.isDevModeActive(context))
            }
        },
    ) {
        content()
    }
}
