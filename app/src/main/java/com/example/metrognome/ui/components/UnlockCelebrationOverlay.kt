package com.example.metrognome.ui.components

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
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

// ── Confetti definitions ──────────────────────────────────────────────────────

private val confettiColors = AppColors.confetti

private data class ConfettiDef(
    val baseX: Float,
    val phaseOffset: Float,
    val speed: Float,
    val hWobble: Float,
    val wobbleFreq: Float,
    val rotBase: Float,
    val rotRate: Float,
    val size: Float,
    val isRect: Boolean,
    val color: Color,
)

private val confettiDefs: List<ConfettiDef> = run {
    val rng = Random(seed = 7331)
    List(65) { i ->
        ConfettiDef(
            baseX = rng.nextFloat(),
            phaseOffset = rng.nextFloat(),
            speed = 0.35f + rng.nextFloat() * 1.1f,
            hWobble = (rng.nextFloat() - 0.5f) * 0.08f,
            wobbleFreq = 1f + rng.nextFloat() * 3f,
            rotBase = rng.nextFloat() * 360f,
            rotRate = (rng.nextFloat() - 0.5f) * 600f,
            size = 0.010f + rng.nextFloat() * 0.016f,
            isRect = rng.nextBoolean(),
            color = confettiColors[i % confettiColors.size],
        )
    }
}

private fun DrawScope.drawConfetti(time: Float) {
    for (p in confettiDefs) {
        val t = ((time * p.speed + p.phaseOffset) % 1f + 1f) % 1f
        val px = (p.baseX + p.hWobble * sin(t * p.wobbleFreq * 2.0 * PI).toFloat()) * size.width
        val py = (-0.15f + t * 1.3f) * size.height
        val s = p.size * size.width

        withTransform({
            translate(px, py)
            rotate(p.rotBase + p.rotRate * t, pivot = Offset.Zero)
        }) {
            if (p.isRect) {
                drawRect(
                    color = p.color.copy(alpha = 0.9f),
                    topLeft = Offset(-s * 0.28f, -s * 0.5f),
                    size = Size(s * 0.56f, s),
                )
            } else {
                drawCircle(
                    color = p.color.copy(alpha = 0.9f),
                    radius = s * 0.5f,
                    center = Offset.Zero,
                )
            }
        }
    }
}

// ── Main overlay ──────────────────────────────────────────────────────────────

/**
 * Full-screen animated celebration overlay shown when a new cosmetic item unlocks.
 *
 * Handles its own entrance animation (spring-bounced card, fading backdrop) and
 * confetti that runs via InfiniteTransition so there are no per-frame state updates.
 * Each item's actual draw() function is used in the preview canvas so the user
 * sees exactly what they unlocked.
 */
@Composable
fun UnlockCelebrationOverlay(
    entry: MetroItemEntry,
    onDismiss: () -> Unit,
) {
    val cardScale = remember { Animatable(0.15f) }
    val overlayAlpha = remember { Animatable(0f) }

    LaunchedEffect(entry) {
        launch { overlayAlpha.animateTo(0.88f, tween(280)) }
        cardScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMediumLow,
            ),
        )
    }

    val confettiTime by rememberInfiniteTransition(label = "confetti")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
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
                    text = "✦  NEW ITEM UNLOCKED  ✦",
                    color = AppColors.gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )

                Spacer(Modifier.height(20.dp))

                ItemPreviewCanvas(entry = entry)

                Spacer(Modifier.height(20.dp))

                Text(
                    text = entry.item.displayName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(6.dp))

                Text(
                    text = entry.item.unlockCondition,
                    color = AppColors.textMutedBlue,
                    fontSize = 12.sp,
                    fontStyle = FontStyle.Italic,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = entry.item.earnedMessage,
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
                        text = "Sweet!",
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

// ── Item preview canvas ───────────────────────────────────────────────────────

@Composable
private fun ItemPreviewCanvas(entry: MetroItemEntry) {
    val item = entry.item

    Box(
        modifier = Modifier
            .size(width = 220.dp, height = 170.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(AppColors.previewBgTop, AppColors.previewBgBottom))
            ),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx    = size.width / 2f
            val halfW = size.width / 2f
            val halfH = size.height / 2f

            if (!item.isBodyAttached) {
                // Background item: draws at canvas-proportional positions using size.width/height.
                // Compute its visual centre + radius, then centre+zoom it to fill the box.
                val u     = size.height / 17f
                val baseY = size.height * 0.97f
                val center = item.previewCenter(size.width, size.height, u, baseY)
                val radius = item.previewRadius(u).coerceAtLeast(u * 0.5f)
                val zoom   = (size.minDimension * 0.40f / radius).coerceIn(1.0f, 10f)
                val dx     = halfW - center.x
                val dy     = halfH - center.y
                withTransform({
                    translate(dx, dy)
                    // pivot is in post-translate space → screen-space pivot = canvas centre
                    scale(zoom, zoom, pivot = Offset(center.x, center.y))
                }) {
                    with(item) { draw(u, cx, baseY) }
                }
            } else {
                // Body-attached wearable: boost u so the item is large enough to see.
                // Compute where hitCenter lands in canvas space, then centre+zoom it.
                val boostedU    = size.height / 5f
                val hitCenter   = item.hitCenter(boostedU) ?: Offset.Zero
                val bodyOriginX = halfW
                val bodyOriginY = size.height * 0.97f
                // Item screen position (before outer transform)
                val itemX = bodyOriginX + hitCenter.x
                val itemY = bodyOriginY + hitCenter.y
                val radius = item.hitRadius(boostedU).coerceAtLeast(boostedU * 0.3f)
                val zoom   = (size.minDimension * 0.38f / radius).coerceIn(1.5f, 15f)
                val dx     = halfW - itemX
                val dy     = halfH - itemY
                withTransform({
                    translate(dx, dy)
                    scale(zoom, zoom, pivot = Offset(itemX, itemY))
                }) {
                    withTransform({ translate(bodyOriginX, bodyOriginY) }) {
                        with(item) { draw(boostedU, bodyOriginX, bodyOriginY) }
                    }
                }
            }
        }
    }
}
