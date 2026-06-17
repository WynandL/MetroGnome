package com.example.metrognome.dev

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import com.example.metrognome.BuildConfig
import com.example.metrognome.haptics.HapticPattern
import com.example.metrognome.haptics.LocalHaptics
import androidx.core.content.edit

/**
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
        prefs(context).edit { putBoolean(KEY_ENABLED, next) }
        return next
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
}

// ── Composable tap wrapper ────────────────────────────────────────────────────

/**
 * Invisible tap detector. Wrap your About / version text with this composable.
 *
 * Two-step unlock so it cannot be triggered by accident (and is not obvious to onlookers):
 *   1. Long-press the target. After the system long-press is recognized a [HapticPattern.LONG_PRESS]
 *      buzz fires, confirming the egg is "armed".
 *   2. Within [armWindowMs] of that buzz, tap the target [tapCount] times. The manual dev-mode flag
 *      then toggles, a toast is shown, and [onToggled] is called with the new state.
 *
 * Taps without a preceding long-press (or after the arm window lapses) are ignored, so casual taps
 * do nothing. The toggle is symmetric — repeating the gesture disables dev mode again.
 *
 * @param tapCount       Number of taps needed once armed (default 10).
 * @param armWindowMs    Time after the buzz in which the taps must land (default 4 000 ms).
 * @param toastEnabled   Show a short Toast on toggle (default true).
 * @param enabledMessage Toast text when dev mode is turned ON.
 * @param disabledMessage Toast text when dev mode is turned OFF.
 * @param onToggled      Called with the new [DevEasterEgg.isDevModeActive] value.
 */
@Composable
fun DevTapTarget(
    modifier: Modifier = Modifier,
    tapCount: Int = 10,
    armWindowMs: Long = 4_000L,
    toastEnabled: Boolean = true,
    enabledMessage: String = "Developer mode enabled 🛠️",
    disabledMessage: String = "Developer mode disabled",
    onToggled: (enabled: Boolean) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val haptics = LocalHaptics.current
    val tapTimes = remember { ArrayDeque<Long>() }
    // Epoch ms until which taps are accepted; 0 = disarmed. Held in state but only read inside the
    // gesture lambdas, so a recomposition on arm/disarm is harmless.
    val armedUntil = remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onLongPress = {
                    // Arm the egg: confirm with a buzz and open the tap window.
                    armedUntil.longValue = System.currentTimeMillis() + armWindowMs
                    tapTimes.clear()
                    haptics.fire(HapticPattern.LONG_PRESS)
                },
                onTap = {
                    val now = System.currentTimeMillis()
                    if (now <= armedUntil.longValue) {
                        tapTimes.removeAll { now - it > armWindowMs }
                        tapTimes.addLast(now)
                        if (tapTimes.size >= tapCount) {
                            tapTimes.clear()
                            armedUntil.longValue = 0L
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
                    }
                },
            )
        },
    ) {
        content()
    }
}
