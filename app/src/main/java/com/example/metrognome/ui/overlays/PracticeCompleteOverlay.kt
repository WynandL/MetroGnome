package com.example.metrognome.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.components.PerformanceBonusReward
import com.example.metrognome.ui.components.StreakIcon
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.viewmodel.PracticeResult
import kotlinx.coroutines.launch

@Composable
fun PracticeCompleteOverlay(
    result: PracticeResult,
    onDismiss: () -> Unit,
) {
    val cardScale    = remember { Animatable(0.15f) }
    val overlayAlpha = remember { Animatable(0f) }

    LaunchedEffect(result) {
        launch { overlayAlpha.animateTo(0.88f, tween(280)) }
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMediumLow,
            ),
        )
    }

    val confettiTime by rememberInfiniteTransition(label = "confetti")
        .animateFloat(
            initialValue = 0f,
            targetValue  = 1f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "confettiTime",
        )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = overlayAlpha.value)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawConfetti(confettiTime)
        }

        Surface(
            modifier = Modifier
                .padding(horizontal = 22.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha  = (cardScale.value - 0.15f) / 0.85f
                },
            shape          = RoundedCornerShape(28.dp),
            color          = AppColors.surfaceDeep,
            shadowElevation = 28.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text         = "✦  PRACTICE COMPLETE  ✦",
                    color        = AppColors.gold,
                    fontSize     = 12.sp,
                    fontWeight   = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text       = "${result.durationMinutes}",
                    color      = Color.White,
                    fontSize   = 72.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = (-2).sp,
                )
                Text(
                    text      = "minutes",
                    color     = AppColors.textMuted,
                    fontSize  = 14.sp,
                    modifier  = Modifier.padding(top = 0.dp, bottom = 16.dp),
                )

                Row(
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
                ) {
                    StreakIcon(Modifier.size(20.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text       = "Day ${result.streak} streak",
                        color      = AppColors.gold,
                        fontSize   = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // Timing bonus from mic mode, when earned. Hidden at 0 (no mic / misfire).
                if (result.performanceBonus > 0) {
                    Spacer(Modifier.height(20.dp))
                    PerformanceBonusReward(
                        bonus = result.performanceBonus,
                        fraction = result.performanceFraction,
                    )
                }

                Spacer(Modifier.height(28.dp))

                Button(
                    onClick = onDismiss,
                    colors  = ButtonDefaults.buttonColors(containerColor = AppColors.primaryPurple),
                    shape   = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(0.65f),
                ) {
                    Text(
                        text       = "Sweet!",
                        fontWeight = FontWeight.Bold,
                        fontSize   = 16.sp,
                        color      = Color.White,
                        modifier   = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
