package com.example.metrognome.ui.components

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.metrognome.audio.selftest.MicCalibration
import com.example.metrognome.ui.components.metro_items.MetroItemTracker
import com.example.metrognome.ui.theme.AppColors

/**
 * Dual-state microphone-timing strip for the mic-scoring surfaces (Speed Trainer,
 * Practice, Rhythm Game). One component, two jobs, driven by the single master flag
 * [MicCalibration.isActive] plus whether a Groove Check has ever been run:
 *
 *  - **Active** (opted in AND calibrated AND the OS still actually holds RECORD_AUDIO):
 *    an informational reminder that mic timing is on, pointing to the single control in
 *    Settings. Always available; pass no [onStartCheck] and this is all you get (e.g.
 *    the Rhythm card).
 *  - **On but permission gone** (opted in AND calibrated, but the live permission check
 *    fails): [MicCalibration.isActive] alone can't tell this apart from the true active
 *    state - it only reads the persisted opt-in flag, which (unlike the OS grant) survives
 *    an uninstall/reinstall via SharedPreferences backup. Without this branch the pill
 *    would just vanish silently the moment permission is missing, which is exactly what
 *    made the mic being dead invisible during a session. Tapping calls [onFixPermission]
 *    where the caller offers it - a lightweight re-request (see
 *    [com.example.metrognome.ui.components.MicPermissionRecovery.fixPermission]), NOT the
 *    full Groove Check: the device already proved itself once, so re-running the whole
 *    onboarding flow here would be a needless, confusing detour. No [onFixPermission] means
 *    a non-interactive note pointing at Settings instead.
 *  - **Never checked** (not active AND zero completed checks) AND an [onStartCheck] is
 *    supplied: a tappable onboarding CTA inviting the user to run the Groove Check
 *    right here. This is how we surface an otherwise Settings-buried feature at the
 *    moment it is relevant (the player is about to practise their timing).
 *  - **Checked but off** (ran a check that did not enable it, e.g. a device that is not
 *    a good fit): renders nothing. The CTA self-terminates after one run so a player is
 *    never nagged to retry something their phone already declined.
 *
 * This is an inline layout element inside the host card/dialog, not the transient
 * banner queue. Renders nothing when neither state applies, so callers drop it in
 * unconditionally; the leading spacing is only emitted when it actually shows.
 */
@Composable
fun MicTimingNudge(
    modifier: Modifier = Modifier,
    /**
     * Fixes a live-permission gap on an already-calibrated device: a lightweight
     * re-request (or a hop to App Settings if permanently denied), never the full
     * Groove Check. See [com.example.metrognome.ui.components.MicPermissionRecovery].
     */
    onFixPermission: (() -> Unit)? = null,
    /** When supplied, enables the off-state onboarding CTA that launches the Groove Check. */
    onStartCheck: (() -> Unit)? = null,
    /**
     * Bump this from the host after a Groove Check resolves, or after
     * [MicPermissionRecovery.fixPermission] resolves, so the strip re-reads its
     * (non-reactive, SharedPreferences-backed) state in place. Without it Compose skips
     * this subtree and a just-finished check would not clear the CTA until the host
     * dialog is closed and reopened.
     */
    refreshKey: Int = 0,
) {
    val context = LocalContext.current
    val cal = remember(refreshKey) { MicCalibration.read(context) }
    val micGranted = remember(refreshKey) {
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
    }
    val checksDone = remember(refreshKey) { MetroItemTracker(context).micChecksCompleted() }

    when {
        cal.isActive && micGranted -> ActiveReminder(modifier)
        cal.isActive && !micGranted -> PermissionNeededReminder(modifier, onFixPermission)
        onStartCheck != null && checksDone == 0 -> StartCheckCta(modifier, onStartCheck)
        // Off and already checked once (or no CTA wanted here): stay silent.
    }
}

@Composable
private fun ActiveReminder(modifier: Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.gold.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = AppColors.gold,
            modifier = Modifier.size(14.dp),
        )
        Text(
            "Groove Check is on",
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "· change in Settings",
            color = AppColors.textMuted,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun PermissionNeededReminder(modifier: Modifier, onFix: (() -> Unit)?) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.gold.copy(alpha = 0.12f))
            .let { if (onFix != null) it.clickable(onClick = onFix) else it }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = AppColors.gold,
            modifier = Modifier.size(16.dp),
        )
        // Title + subtitle stack instead of one wrapping row: the "tap to fix" hint
        // gets its own line so it can't end up stranded at an odd height once the
        // headline wraps to two lines on narrower screens or larger text scale.
        //
        // Every headline here sets lineHeight explicitly alongside fontSize. Material3
        // provides Typography.bodyLarge as the ambient text style, and that carries a FIXED
        // lineHeight of 24.sp to go with its 16.sp size. Overriding only fontSize leaves the
        // 24.sp behind, so a 12.sp headline is laid out on double-height lines — invisible
        // while it fits on one line, and a gaping hole the moment it wraps.
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "Groove Check needs microphone access",
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                lineHeight = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                if (onFix != null) "Tap to fix" else "Fix in Settings",
                color = AppColors.textMuted,
                fontSize = 10.sp,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        if (onFix != null) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = AppColors.gold,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun StartCheckCta(modifier: Modifier, onStartCheck: () -> Unit) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.gold.copy(alpha = 0.12f))
            .clickable(onClick = onStartCheck)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = null,
            tint = AppColors.gold,
            modifier = Modifier.size(15.dp),
        )
        Text(
            "Score your timing",
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "· run a quick Groove Check",
            color = AppColors.textMuted,
            fontSize = 10.sp,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = AppColors.gold,
            modifier = Modifier.size(16.dp),
        )
    }
}
