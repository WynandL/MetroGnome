package com.example.metrognome.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Shared microphone permission state used by every screen that needs mic access.
 *
 * Handles the permanently-denied case (user tapped "Don't ask again" or denied
 * twice on Android 11+): [request] opens the App Settings screen directly instead
 * of silently no-oping.
 *
 * Usage:
 * ```
 * val mic = rememberMicPermissionState { vm.toggleMic(true) }
 * // mic.isGranted           — current state
 * // mic.isPermanentlyDenied — true after Android stops showing the dialog
 * // mic.request()           — launches permission dialog, or opens Settings if blocked
 * ```
 */
class MicPermissionState(
    val isGranted: Boolean,
    val isPermanentlyDenied: Boolean,
    val request: () -> Unit,
)

@Composable
fun rememberMicPermissionState(onGranted: () -> Unit = {}): MicPermissionState {
    val context = LocalContext.current
    val activity = context as? Activity

    var isGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    var isPermanentlyDenied by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        isGranted = granted
        if (!granted && activity != null) {
            // After a denial, shouldShowRationale returns false only when the user has
            // permanently blocked the permission (checked "Don't ask again" or reached
            // Android's auto-deny threshold). That's our signal to switch to Settings.
            isPermanentlyDenied = !ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.RECORD_AUDIO
            )
        }
        if (granted) onGranted()
    }

    val openSettings: () -> Unit = {
        context.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
            }
        )
    }

    return remember(isGranted, isPermanentlyDenied) {
        MicPermissionState(
            isGranted = isGranted,
            isPermanentlyDenied = isPermanentlyDenied,
            request = {
                if (isPermanentlyDenied) openSettings()
                else launcher.launch(Manifest.permission.RECORD_AUDIO)
            },
        )
    }
}
