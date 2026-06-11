package com.example.metrognome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.points.PointsConfig
import com.example.metrognome.ui.theme.AppColors

/**
 * Congratulatory Gnotes reward shown after a mic-enabled Practice or Speed Trainer
 * session, in place of the old per-tempo accuracy bars. It deliberately shows only
 * the (positive) reward and an encouraging line, never a raw "you were Xms off"
 * stat that could discourage. Callers must only render it when [bonus] > 0; a zero
 * (a misfire or the daily cap already reached) is hidden by the caller.
 *
 * [fraction] is the 0..1 performance share that produced the bonus; it only tunes
 * the encouraging headline, never the number.
 */
@Composable
fun PerformanceBonusReward(
    bonus: Int,
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    // fraction is the session score in [0,1]: the mean of each hit's timing credit
    // (1.0 = every hit tight, 0 = none landed in time). The thresholds below gate how
    // effusive the praise is; "Perfectly in time!" is reserved for near-flawless play so
    // it never overstates a merely-decent session.
    val headline = when {
        fraction >= 0.95f -> "Perfectly in time!"
        fraction >= 0.80f -> "Great timing!"
        fraction >= 0.60f -> "Solid timing"
        fraction >= 0.40f -> "Good and steady"
        else              -> "You're doing great"
    }

    Column(
        modifier = modifier
            .background(AppColors.gold.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = headline,
            color = AppColors.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(6.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.MusicNote,
                contentDescription = null,
                tint = AppColors.gold,
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "+$bonus",
                color = AppColors.gold,
                fontSize = 30.sp,
                fontWeight = FontWeight.Black,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = if (bonus == 1) PointsConfig.CURRENCY_NAME_SINGULAR else PointsConfig.CURRENCY_NAME,
                color = AppColors.gold,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
