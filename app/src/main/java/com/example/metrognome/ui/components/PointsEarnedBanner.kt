package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.points.PointsBannerData
import com.example.metrognome.points.PointsBannerQueue
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.delay

/**
 * A transient, non-intrusive pill that slides in from the top of the screen
 * after a points-earning activity is completed. Auto-dismisses after ~2.8s.
 *
 * Drop this inside a [androidx.compose.foundation.layout.Box] at
 * [Alignment.TopCenter] to float it above the current screen content.
 * It collects events from [PointsBannerQueue] so no state needs to be
 * passed in from the outside.
 */
@Composable
fun PointsEarnedBanner(modifier: Modifier = Modifier) {
    var bannerData by remember { mutableStateOf<PointsBannerData?>(null) }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PointsBannerQueue.events.collect { data ->
            if (show) {
                show = false
                delay(280)
            }
            bannerData = data
            show = true
            delay(2800)
            show = false
        }
    }

    AnimatedVisibility(
        visible  = show,
        enter    = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit     = slideOutVertically(tween(280)) { -it } + fadeOut(tween(280)),
        modifier = modifier,
    ) {
        bannerData?.let { BannerPill(it) }
    }
}

@Composable
fun LoyaltyMilestoneBanner(modifier: Modifier = Modifier) {
    var days by remember { mutableStateOf(0) }
    var show by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        PointsBannerQueue.milestones.collect { d ->
            if (show) {
                show = false
                delay(280)
            }
            days = d
            show = true
            delay(3500)
            show = false
        }
    }

    AnimatedVisibility(
        visible  = show,
        enter    = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit     = slideOutVertically(tween(280)) { -it } + fadeOut(tween(280)),
        modifier = modifier,
    ) {
        MilestonePill(days)
    }
}

@Composable
private fun MilestonePill(days: Int) {
    val shape = RoundedCornerShape(50.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .background(AppColors.surfaceDeep, shape)
            .border(1.dp, AppColors.primaryPurple.copy(alpha = 0.55f), shape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector        = Icons.Filled.Stars,
            contentDescription = null,
            tint               = AppColors.primaryPurple,
            modifier           = Modifier.size(13.dp),
        )
        Text(
            text       = "$days days",
            color      = AppColors.primaryPurple,
            fontWeight = FontWeight.ExtraBold,
            fontSize   = 14.sp,
        )
        Text("·", color = AppColors.textMuted, fontSize = 13.sp)
        Text(
            text     = milestoneLabel(days),
            color    = AppColors.textMuted,
            fontSize = 12.sp,
        )
    }
}

private fun milestoneLabel(days: Int) = when (days) {
    7    -> "One week with Metro"
    30   -> "One month strong"
    60   -> "Two months in"
    100  -> "100 days. Legend."
    365  -> "One full year"
    else -> "$days-day milestone"
}

@Composable
private fun BannerPill(data: PointsBannerData) {
    val atLimit = data.limitJustReached || (data.pointsEarned == 0 && data.todayCount >= data.dailyLimit)
    val shape   = RoundedCornerShape(50.dp)
    val border  = if (atLimit) AppColors.primaryPurple.copy(alpha = 0.55f)
                  else         AppColors.gold.copy(alpha = 0.45f)

    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .background(AppColors.surfaceDeep, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment    = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (data.pointsEarned > 0) {
            Icon(
                imageVector        = Icons.Filled.Bolt,
                contentDescription = null,
                tint               = AppColors.gold,
                modifier           = Modifier.size(13.dp),
            )
            Text(
                text       = "+${data.pointsEarned}",
                color      = AppColors.gold,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 14.sp,
            )
            Text(
                text          = PointsConfig.CURRENCY_NAME.uppercase(),
                color         = AppColors.gold.copy(alpha = 0.7f),
                fontWeight    = FontWeight.Bold,
                fontSize      = 10.sp,
                letterSpacing = 1.5.sp,
            )
        } else {
            Text(
                text       = "Daily limit reached",
                color      = AppColors.textSecondary,
                fontWeight = FontWeight.SemiBold,
                fontSize   = 13.sp,
            )
        }
        Text("·", color = AppColors.textMuted, fontSize = 13.sp)
        Text(
            text     = data.activityLabel,
            color    = AppColors.textMuted,
            fontSize = 12.sp,
        )
        Text("·", color = AppColors.textMuted, fontSize = 13.sp)
        Text(
            text       = "${data.todayCount} / ${data.dailyLimit} today",
            color      = if (atLimit) AppColors.textSecondary else AppColors.textMuted,
            fontWeight = if (atLimit) FontWeight.SemiBold else FontWeight.Normal,
            fontSize   = 12.sp,
        )
    }
}
