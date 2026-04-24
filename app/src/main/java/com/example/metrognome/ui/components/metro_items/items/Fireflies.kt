package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.sin

/**
 * Six bioluminescent fireflies drifting around Metro's forest scene.
 *
 * Animation uses System.currentTimeMillis() — safe here because GnomeCanvas
 * recomposes continuously via its infinite breathAnim transition.
 *
 * Home positions are fractions of (size.width, size.height) so they are spread
 * naturally across the full scene regardless of device aspect ratio.
 */
object Fireflies : MetroItem {

    override val id             = "fireflies"
    override val displayName    = "Fireflies"
    override val description    = "Tiny bioluminescent visitors drawn to Metro's music on long practice nights."
    override val unlockCondition = "Play the metronome for 3 hours total"
    override val earnedMessage  = "Three whole hours of music! These fireflies were drawn to Metro's rhythmic light — they drift around him at dusk, enchanted by the beat."
    override val isBodyAttached  = false

    // Not individually tappable — too spread out
    override fun hitCenter(u: Float): Offset? = null

    // Home positions as (widthFraction, heightFraction) — computed against size at draw time.
    // Spread across the full canvas: some in sky corners, some in mid-scene.
    private val homeWH = listOf(
        0.15f to 0.35f,   // left, mid-height
        0.78f to 0.38f,   // right, mid-height
        0.08f to 0.55f,   // far left, lower-mid
        0.82f to 0.22f,   // right, upper
        0.42f to 0.12f,   // centre-right, high sky
        0.88f to 0.50f,   // far right, mid
    )

    private val glowYellow = Color(0xFFE8FF70)
    private val coreWhite  = Color(0xFFF5FFAA)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t = (System.currentTimeMillis() % 100_000L) / 1000f

        homeWH.forEachIndexed { i, (wf, hf) ->
            val phase = i * 1.31f
            // Drift radius scaled to screen so it looks proportionate on any device
            val driftX = sin(t * 0.38f + phase) * size.width  * 0.025f
            val driftY = sin(t * 0.27f + phase * 1.73f) * size.height * 0.018f
            val blink  = ((sin(t * 1.6f + phase * 2.1f) + 1f) / 2f).coerceIn(0.08f, 1.0f)

            val fx = size.width  * wf + driftX
            val fy = size.height * hf + driftY

            // Soft outer glow halo
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(glowYellow.copy(alpha = 0.38f * blink), Color.Transparent),
                    center = Offset(fx, fy),
                    radius = u * 0.65f
                ),
                radius = u * 0.65f,
                center = Offset(fx, fy)
            )

            // Bright inner core
            drawCircle(
                color  = coreWhite.copy(alpha = blink),
                radius = u * 0.075f,
                center = Offset(fx, fy)
            )
        }
    }
}
