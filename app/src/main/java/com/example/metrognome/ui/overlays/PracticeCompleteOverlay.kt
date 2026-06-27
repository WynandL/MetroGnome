package com.example.metrognome.ui.overlays

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
        launch { overlayAlpha.animateTo(0.72f, tween(280)) }
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
                .padding(horizontal = 24.dp)
                .widthIn(min = 280.dp, max = 380.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha  = ((cardScale.value - 0.15f) / 0.85f).coerceIn(0f, 1f)
                },
            shape          = RoundedCornerShape(24.dp),
            color          = AppColors.surfaceDeep,
            shadowElevation = 32.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OverlayEyebrow("✦  PRACTICE COMPLETE  ✦", color = AppColors.textMuted)

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

                // Groove Check grade from mic mode, when a qualifying session ran. Shown whenever
                // the grade exists (not gated on the Gnote bonus, so a quick demo still shows it).
                if (result.grooveScore > 0) {
                    Spacer(Modifier.height(20.dp))
                    PerformanceBonusReward(
                        grooveScore = result.grooveScore,
                        read = result.grooveRead,
                        bonus = result.performanceBonus,
                    )
                }

                Spacer(Modifier.height(20.dp))

                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.Transparent,
                    border = BorderStroke(1.dp, AppColors.gold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            "Sweet!",
                            color = AppColors.gold,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
