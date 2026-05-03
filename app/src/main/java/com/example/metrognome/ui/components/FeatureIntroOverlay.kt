package com.example.metrognome.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.whats_new.AppWhatsNew
import kotlinx.coroutines.launch

/**
 * Picks the correct overlay composable for [versionKey] and shows it.
 *
 * To add a future version popup:
 *   1. Add its key to [AppWhatsNew].
 *   2. Write a private composable below (e.g. `V4FeatureIntroOverlay`).
 *   3. Add a `when` branch here.
 */
@Composable
fun WhatsNewOverlayDispatcher(versionKey: String, onDismiss: () -> Unit) {
    when (versionKey) {
        AppWhatsNew.V3 -> V3FeatureIntroOverlay(onDismiss)
        // AppWhatsNew.V4 -> V4FeatureIntroOverlay(onDismiss)
    }
}

// ── V3 — "Metro Got a Glow-Up!" ───────────────────────────────────────────────

@Composable
private fun V3FeatureIntroOverlay(onDismiss: () -> Unit) {
    val cardScale = remember { Animatable(0.15f) }
    val overlayAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        launch { overlayAlpha.animateTo(0.88f, tween(280)) }
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    val confettiTime by rememberInfiniteTransition(label = "introConfetti")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "introConfettiTime",
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
                    alpha = (cardScale.value - 0.15f) / 0.85f
                },
            shape = RoundedCornerShape(28.dp),
            color = AppColors.surfaceDeep,
            shadowElevation = 28.dp,
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 28.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "✦  NEW IN VERSION 3  ✦",
                    color = AppColors.gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )

                Spacer(Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(width = 220.dp, height = 150.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(AppColors.previewBgTop, AppColors.previewBgBottom)
                            )
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "✨", fontSize = 52.sp)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "Items & Surprises",
                            color = AppColors.gold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Metro Got a Glow-Up!",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Brand-new cosmetic items",
                    color = AppColors.textMutedBlue,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Keep the metronome running, play some rhythm games, and check back often. Metro and his world are full of hidden surprises. Use the app, and watch what appears...",
                    color = AppColors.textSecondary,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp,
                )

                Spacer(Modifier.height(26.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.primaryPurple),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(0.6f),
                ) {
                    Text(
                        text = "Let's Explore!",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
