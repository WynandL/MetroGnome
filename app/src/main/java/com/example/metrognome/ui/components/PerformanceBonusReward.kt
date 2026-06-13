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
 * Groove Check result shown after a mic-enabled Practice or Speed Trainer session.
 *
 * It leads with the [grooveScore] (0..100), a LENGTH-INDEPENDENT quality grade, so even a quick
 * demo shows a meaningful, satisfying number. [read] is the plain-language explanation underneath
 * ("Locked in", "Steady but behind the beat"), which fills the "why is it that?" gap. The Gnotes
 * [bonus] rides below as the smaller, honest economy reward and is hidden when 0 (a sub-cap
 * misfire or the daily cap already reached) - the grade still shows.
 *
 * Callers render this whenever [grooveScore] > 0 (a qualifying mic session), not gated on [bonus].
 */
@Composable
fun PerformanceBonusReward(
    grooveScore: Int,
    read: String,
    bonus: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(AppColors.gold.copy(alpha = 0.10f), RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "GROOVE SCORE",
            color = AppColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            letterSpacing = 1.sp,
        )
        Spacer(Modifier.size(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = "$grooveScore",
                color = AppColors.gold,
                fontSize = 44.sp,
                fontWeight = FontWeight.Black,
            )
            Text(
                text = " / 100",
                color = AppColors.textMuted,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 7.dp),
            )
        }
        if (read.isNotEmpty()) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = read,
                color = AppColors.textSecondary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
        if (bonus > 0) {
            Spacer(Modifier.size(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = AppColors.gold,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    text = "+$bonus",
                    color = AppColors.gold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = if (bonus == 1) PointsConfig.CURRENCY_NAME_SINGULAR else PointsConfig.CURRENCY_NAME,
                    color = AppColors.gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
