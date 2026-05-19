package com.example.metrognome.ui.overlays

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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.Canvas
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.whatsnew.AppWhatsNew
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
        AppWhatsNew.V4 -> V4FeatureIntroOverlay(onDismiss)
    }
}

// ── Mini tuner gauge — shared between V4 overlay and future uses ──────────────

private val tunerGreenInTune = Color(0xFF7BE87B)   // matches GameColors.good

private fun DrawScope.drawMiniTunerGauge() {
    val sweepDeg = 62f
    val cx = size.width / 2f
    val pivot = Offset(cx, size.height * 0.82f)
    val radius = minOf(size.width * 0.44f, pivot.y - 14.dp.toPx())
    val trackW = 5.dp.toPx()
    val arcBox = Offset(pivot.x - radius, pivot.y - radius)
    val arcSize = Size(radius * 2f, radius * 2f)

    drawArc(
        color = Color(0xFF2A2550),
        startAngle = 270f - sweepDeg,
        sweepAngle = sweepDeg * 2f,
        useCenter = false,
        topLeft = arcBox, size = arcSize,
        style = Stroke(width = trackW, cap = StrokeCap.Round),
    )

    val bandHalf = sweepDeg * (6f / 50f)
    drawArc(
        color = tunerGreenInTune.copy(alpha = 0.60f),
        startAngle = 270f - bandHalf,
        sweepAngle = bandHalf * 2f,
        useCenter = false,
        topLeft = arcBox, size = arcSize,
        style = Stroke(width = trackW, cap = StrokeCap.Round),
    )

    // Tick marks at centre (0¢) and ends (±50¢) — matches real gauge
    for (c in listOf(-50, 0, 50)) {
        val rad = Math.toRadians(270.0 + (c / 50.0) * sweepDeg)
        val cosA = cos(rad).toFloat()
        val sinA = sin(rad).toFloat()
        val isCentre = c == 0
        val len = if (isCentre) 12.dp.toPx() else 8.dp.toPx()
        val outerR = radius + trackW
        drawLine(
            color = if (isCentre) tunerGreenInTune else Color(0xFF5A4E80),
            start = Offset(pivot.x + cosA * (outerR - len), pivot.y + sinA * (outerR - len)),
            end = Offset(pivot.x + cosA * outerR, pivot.y + sinA * outerR),
            strokeWidth = if (isCentre) 2.5.dp.toPx() else 1.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }

    val needleAngle = Math.toRadians(270.0)
    val tip = Offset(
        pivot.x + cos(needleAngle).toFloat() * radius * 0.86f,
        pivot.y + sin(needleAngle).toFloat() * radius * 0.86f,
    )
    drawCircle(tunerGreenInTune.copy(alpha = 0.18f), radius * 0.28f, tip)
    drawLine(tunerGreenInTune, pivot, tip, strokeWidth = 4.dp.toPx(), cap = StrokeCap.Round)
    drawCircle(tunerGreenInTune, 8.dp.toPx(), pivot)
    drawCircle(Color(0xFF13102A), 3.5.dp.toPx(), pivot)
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
                    modifier = Modifier.fillMaxWidth(0.65f),
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

// ── V4 — "Meet Your Built-In Tuner" ──────────────────────────────────────────

@Composable
private fun V4FeatureIntroOverlay(onDismiss: () -> Unit) {
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

    val confettiTime by rememberInfiniteTransition(label = "v4Confetti")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
            label = "v4ConfettiTime",
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
                    text = "✦  NEW IN VERSION 4  ✦",
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
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Canvas(modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                        ) {
                            drawMiniTunerGauge()
                        }
                        Text(
                            text = "A4  ·  IN TUNE",
                            color = tunerGreenInTune,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.5.sp,
                            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp),
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "Meet Your Built-In Tuner",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = "Chromatic · Calibrated · Noise-aware",
                    color = AppColors.textMutedBlue,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "MetroGnome now has a real-time chromatic tuner. It locks onto your instrument, rides out background noise and voices, and can calibrate to a tuning fork or your own playing. Find it in the Tuner tab.",
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
                    modifier = Modifier.fillMaxWidth(0.65f),
                ) {
                    Text(
                        text = "Nice, let's go!",
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
