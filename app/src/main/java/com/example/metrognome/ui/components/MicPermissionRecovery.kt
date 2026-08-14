package com.example.metrognome.ui.components

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.metrognome.audio.selftest.MicCalibration
import com.example.metrognome.audio.selftest.SelfTestCalibrationStore

/**
 * Shared live-permission recovery for the mic-scoring surfaces (Practice, Speed
 * Trainer, Rhythm Game). Groove Check can read as opted-in
 * ([MicCalibration.isActive], a SharedPreferences flag that survives an
 * uninstall/reinstall via backup) while the OS RECORD_AUDIO grant is actually gone.
 * This is the one place that gap gets closed, in two different ways depending on
 * what the user just did:
 *
 *  - [MicPermissionRecovery.startWithMicPermissionCheck] wraps a session-start action
 *    (Start Practice/Training, a Rhythm Game difficulty tap): re-requests permission
 *    right when the player starts, then continues into the action once it resolves.
 *  - [MicPermissionRecovery.fixPermission] is what [MicTimingNudge]'s "tap to fix"
 *    pill calls when the device is already calibrated but the grant has since been
 *    revoked - it re-requests permission (or opens App Settings if permanently
 *    denied) WITHOUT re-running the full Groove Check, since the device already
 *    proved itself once. Before this existed, every caller wired the pill straight to
 *    the full onboarding check, so a routine permission bump looked identical to a
 *    brand-new device to the user.
 *
 * [MicPermissionRecovery.refreshKey] increments on a resolved grant so a caller's
 * `MicTimingNudge(refreshKey = ...)` re-reads the SharedPreferences-backed
 * calibration/permission state in place, without needing that state to be
 * Compose-observable itself.
 */
class MicPermissionRecovery internal constructor(
    val micGranted: Boolean,
    val micPermanentlyDenied: Boolean,
    val refreshKey: Int,
    val startWithMicPermissionCheck: (start: () -> Unit) -> Unit,
    val fixPermission: () -> Unit,
)

@Composable
fun rememberMicPermissionRecovery(): MicPermissionRecovery {
    val context = LocalContext.current
    val activity = LocalActivity.current

    var micGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var micPermanentlyDenied by remember { mutableStateOf(false) }
    var refreshKey by remember { mutableIntStateOf(0) }
    var pendingStart by remember { mutableStateOf<(() -> Unit)?>(null) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        micGranted = granted
        if (granted) {
            SelfTestCalibrationStore(context).micModeEnabled = true
            refreshKey++
        } else if (activity != null) {
            micPermanentlyDenied = !ActivityCompat
                .shouldShowRequestPermissionRationale(activity, Manifest.permission.RECORD_AUDIO)
        }
        pendingStart?.invoke()
        pendingStart = null
    }

    return MicPermissionRecovery(
        micGranted = micGranted,
        micPermanentlyDenied = micPermanentlyDenied,
        refreshKey = refreshKey,
        startWithMicPermissionCheck = { start ->
            val cal = MicCalibration.read(context)
            if (cal.isActive && !micGranted && !micPermanentlyDenied) {
                pendingStart = start
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                start()
            }
        },
        fixPermission = {
            if (micPermanentlyDenied) {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", context.packageName, null)
                    }
                )
            } else {
                launcher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
    )
}
