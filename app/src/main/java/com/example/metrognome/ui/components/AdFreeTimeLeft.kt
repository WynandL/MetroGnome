package com.example.metrognome.ui.components

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Locale

/**
 * Time left on a stretch of ad-free time, ticking every second: what it is ([title]), the time
 * left, and the moment ads return in the phone's own time format ("Ads return tomorrow at
 * 14:32"). Used on the Remove Ads card for the ad-free trial and the daily-Gnotes days.
 *
 * [endsAtWallMs] is the estimate the ad-free clock publishes every 20 s; this only counts down
 * to it, so the display can never keep ads off: expiry is decided by the clock, not here.
 */
@Composable
fun AdFreeTimeLeft(title: String, endsAtWallMs: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAtWallMs) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val left = (endsAtWallMs - now).coerceAtLeast(0L)
    val returns = "Ads return ${whenText(endsAtWallMs, now, DateFormat.is24HourFormat(context))}"

    AppInset(highlighted = true, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .semantics { contentDescription = "$title. ${spokenLeft(left)} left. $returns." },
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(AppIcons.Timer, contentDescription = null, tint = AppColors.gold, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, color = AppColors.gold, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.padding(top = 6.dp))
            Text(
                clockText(left),
                color = AppColors.textPrimary, fontSize = 26.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace,
            )
            Text(returns, color = AppColors.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
    }
}

/** 23:41:05, or 2d 23:41:05 past a day. */
internal fun clockText(ms: Long): String {
    val total = ms / 1000
    val d = total / 86_400
    val h = (total % 86_400) / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    val hms = String.format(Locale.US, "%02d:%02d:%02d", h, m, s)
    return if (d > 0) "${d}d $hms" else hms
}

private fun spokenLeft(ms: Long): String {
    val totalMin = ms / 60_000
    val d = totalMin / 1440
    val h = (totalMin % 1440) / 60
    val m = totalMin % 60
    return buildList {
        if (d > 0) add("$d day" + if (d == 1L) "" else "s")
        if (h > 0) add("$h hour" + if (h == 1L) "" else "s")
        add("$m minute" + if (m == 1L) "" else "s")
    }.joinToString(" ")
}

/** "today at 14:32", "tomorrow at 2:32 PM", or "on Fri at 14:32". */
private fun whenText(atMs: Long, nowMs: Long, h24: Boolean): String {
    val at = Calendar.getInstance().apply { timeInMillis = atMs }
    val now = Calendar.getInstance().apply { timeInMillis = nowMs }
    val time = java.text.SimpleDateFormat(if (h24) "HH:mm" else "h:mm a", Locale.getDefault()).format(at.time)
    val dayDiff = dayNumber(at) - dayNumber(now)
    return when (dayDiff) {
        0L -> "today at $time"
        1L -> "tomorrow at $time"
        else -> "on " + java.text.SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(at.time) + " at $time"
    }
}

/** Local calendar day as a running number, so year ends count as one day apart. */
private fun dayNumber(c: Calendar): Long =
    (c.timeInMillis + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L
