package com.example.metrognome.haptics

import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf

/**
 * Composition-local that carries the app-wide [HapticEngine].
 *
 * Provided from [com.example.metrognome.MainActivity]. The default is a no-op
 * engine (null context) so Compose Previews never crash.
 *
 * Usage inside any composable:
 *   val haptics = LocalHaptics.current
 *   haptics.fire(HapticPattern.LONG_PRESS)
 */
val LocalHaptics: ProvidableCompositionLocal<HapticEngine> =
    compositionLocalOf { HapticEngine(null) }
