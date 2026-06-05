package com.example.metrognome.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.example.metrognome.ui.theme.AppColors

/**
 * Animated unlock-progress bar for item cards.
 *
 * Draws a rounded-pill track with a purple-to-gold gradient fill that grows
 * with [progress]. A slow shimmer glint sweeps along the filled portion when
 * [progress] > 0. At full progress the fill goes full gold.
 *
 * Designed to sit below the item name / status text in ItemCatalogDialog.
 * Height is dictated by the caller's Modifier (typically 5–6 dp).
 */
@Composable
fun ItemProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val shimmer by rememberInfiniteTransition(label = "item_progress_shimmer")
        .animateFloat(
            initialValue  = -0.25f,
            targetValue   = 1.25f,
            animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
            label         = "shimmerPos",
        )

    // Fill gradient: deep purple → primary purple until ~60% progress, then
    // primary purple → gold for the final stretch, so nearly-complete bars
    // glow warm and draw the user's attention.
    val fillLeft = lerp(
        AppColors.deepPurple,
        AppColors.primaryPurple,
        (progress * 1.67f).coerceIn(0f, 1f),
    )
    val fillRight = lerp(
        AppColors.primaryPurple,
        AppColors.gold,
        ((progress - 0.4f) * 1.67f).coerceIn(0f, 1f),
    )

    Canvas(modifier = modifier) {
        val w  = size.width
        val h  = size.height
        val cr = CornerRadius(h / 2f)

        // ── Track ────────────────────────────────────────────────────────────
        drawRoundRect(
            color        = Color(0x20FFFFFF),
            cornerRadius = cr,
        )

        if (progress <= 0f) return@Canvas

        // ── Fill ─────────────────────────────────────────────────────────────
        val fillW = (w * progress).coerceAtLeast(h) // minimum pill-width = full height (capsule)
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(fillLeft, fillRight),
                startX = 0f,
                endX   = fillW,
            ),
            size         = Size(fillW, h),
            cornerRadius = cr,
        )

        // ── Shimmer glint — a soft white band sweeping left → right ──────────
        val glintCentre = shimmer * fillW
        val glintHalf   = fillW * 0.18f
        val glintStart  = (glintCentre - glintHalf).coerceAtLeast(0f)
        val glintEnd    = (glintCentre + glintHalf).coerceAtMost(fillW)
        if (glintEnd > glintStart) {
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x28FFFFFF),
                        Color.Transparent,
                    ),
                    startX = glintStart,
                    endX   = glintEnd,
                ),
                size         = Size(fillW, h),
                cornerRadius = cr,
            )
        }

        // ── Leading-edge glow — brighter dot at the tip of the fill ──────────
        if (fillW < w - h) {
            drawCircle(
                color  = fillRight.copy(alpha = 0.55f),
                radius = h * 0.65f,
                center = Offset(fillW, h / 2f),
            )
        }
    }
}
