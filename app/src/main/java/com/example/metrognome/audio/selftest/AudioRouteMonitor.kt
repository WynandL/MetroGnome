package com.example.metrognome.audio.selftest

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Coarse classification of the active audio output route.
 *
 * The self-test's latency constant is only valid for the route it was measured
 * on: Bluetooth adds 100-300 ms (codec-dependent and variable), wired differs
 * from the built-in speaker. If the route changes after calibration the stored
 * constant is silently wrong - and silently-wrong scoring is the one outcome
 * this whole system exists to prevent.
 */
enum class AudioRoute {
    BUILTIN_SPEAKER,
    WIRED,
    BLUETOOTH,
    OTHER;

    val isBuiltInSpeaker get() = this == BUILTIN_SPEAKER

    val label: String
        get() = when (this) {
            BUILTIN_SPEAKER -> "Built-in speaker"
            WIRED -> "Wired output"
            BLUETOOTH -> "Bluetooth"
            OTHER -> "External output"
        }
}

/**
 * Reads the current output route and watches for changes - using only
 * [AudioManager.getDevices], which needs **no permission** and is available from
 * API 23 (well under the app's min SDK 24).
 *
 * One instance per use. Call [start] to begin watching, [stop] to release the
 * callback. [currentRoute] is safe to call at any time without starting.
 */
class AudioRouteMonitor(context: Context) {

    private val audioManager =
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var deviceCallback: AudioDeviceCallback? = null

    /** The output route active right now. */
    fun currentRoute(): AudioRoute {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        // Prefer the "most external" route present - if earbuds are connected the
        // system is routing to them, not the built-in speaker, even though the
        // speaker device is still enumerated.
        var sawWired = false
        var sawBluetooth = false
        var sawSpeaker = false
        for (d in outputs) {
            when (d.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> sawBluetooth = true

                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_USB_HEADSET,
                AudioDeviceInfo.TYPE_USB_DEVICE -> sawWired = true

                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> sawSpeaker = true

                else -> { /* every other output type resolves to OTHER below */ }
            }
        }
        return when {
            sawBluetooth -> AudioRoute.BLUETOOTH
            sawWired -> AudioRoute.WIRED
            sawSpeaker -> AudioRoute.BUILTIN_SPEAKER
            else -> AudioRoute.OTHER
        }
    }

    /**
     * Start watching. [onRouteChanged] fires (on the main thread) whenever a
     * device is added or removed and the resolved route differs from [from].
     */
    fun start(from: AudioRoute, onRouteChanged: (AudioRoute) -> Unit) {
        stop()
        val cb = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = check()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = check()
            private fun check() {
                val now = currentRoute()
                if (now != from) onRouteChanged(now)
            }
        }
        deviceCallback = cb
        audioManager.registerAudioDeviceCallback(cb, Handler(Looper.getMainLooper()))
    }

    fun stop() {
        deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }
        deviceCallback = null
    }
}
