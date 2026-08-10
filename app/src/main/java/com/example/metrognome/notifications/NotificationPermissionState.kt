package com.example.metrognome.notifications

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Live POST_NOTIFICATIONS permission state plus the request/settings actions, mirroring
 * the mic-permission pattern in TunerScreen.kt (checkSelfPermission + a launcher +
 * shouldShowRequestPermissionRationale to tell "denied" from "permanently denied").
 *
 * Below API 33 there is no runtime notification permission - [granted] is always true
 * there (the user's only control is the OS per-app notification toggle).
 */
class NotificationPermissionState internal constructor(
    private val grantedState: MutableState<Boolean>,
    private val permanentlyDeniedState: MutableState<Boolean>,
    private val launcher: ActivityResultLauncher<String>,
    private val openSettings: () -> Unit,
) {
    val granted: Boolean get() = grantedState.value
    val permanentlyDenied: Boolean get() = permanentlyDeniedState.value

    fun request() {
        if (permanentlyDenied) openSettings()
        else launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}

@Composable
fun rememberNotificationPermissionState(): NotificationPermissionState {
    val context = LocalContext.current
    val activity = LocalActivity.current

    fun hasPermission() = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    val grantedState = remember { mutableStateOf(hasPermission()) }
    val permanentlyDeniedState = remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        grantedState.value = isGranted
        if (!isGranted && activity != null) {
            permanentlyDeniedState.value = !ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        }
        if (isGranted) NotificationTopics.subscribeToAllUsers()
    }

    return remember(launcher) {
        NotificationPermissionState(
            grantedState = grantedState,
            permanentlyDeniedState = permanentlyDeniedState,
            launcher = launcher,
            openSettings = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            },
        )
    }
}
