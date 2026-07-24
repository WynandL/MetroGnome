package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.random.Random

/**
 * A cheerful patch of wildflowers spanning the full width of the canvas at ground level.
 * Ground-level background item — no body-attachment needed.
 *
 * The layout (position, colour, scale, petal count, lean of every bloom) is generated once
 * from a fixed seed at class-init, not re-rolled per frame or recomposition. That keeps the
 * patch stable while the metronome runs, but varied enough that it doesn't read as a repeating
 * tile — every bloom's position is jittered within its slot rather than sitting on a grid.
 */
object ForestFloorFlowers : MetroItem {

    override val id             = "forest_floor_flowers"
    override val displayName    = "Wildflowers"
    override val description    = "A cheerful patch of wildflowers that sprouted beside Metro."
    override val earnedMessage  = "You've been with Metro for 3 days! The forest floor is starting to bloom! These little wildflowers sprouted up beside his shoes to welcome you back."
    override val isBodyAttached  = false
    // Drawn after Metro so the patch reads in front of his shoes rather than tucked behind them.
    override val isForeground    = true

    // GnomeCanvas defines u = canvas height / 17 and baseY = canvas height * 0.97 — the gap between
    // baseY and the literal bottom edge is exactly (1 - 0.97) * 17 = 0.51 units, in any canvas size.
    // Dropping the patch's ground line by this amount plants it flush against the canvas bottom.
    private const val GROUND_DROP = 0.51f

    // Tap detection stays a modest circle near canvas centre rather than the full visual span —
    // widening it to match the full-width bloom would swallow taps meant for other items whose
    // hit circles sit further up the body (MusicStand, TuningFork, LapelPin, StudioMic, etc.),
    // since this item is checked earlier in the registry than those.
    override fun hitCenter(u: Float) = Offset(0f, (GROUND_DROP - 0.5f) * u)
    override fun hitRadius(u: Float) = u * 1.8f

    // Show a cluster of blooms close-up, centred on the patch
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * 0.5f, baseY + (GROUND_DROP - 0.6f) * u)
    override fun previewRadius(u: Float) = u * 2.2f

    private val stemGreen   = Color(0xFF4A7C3F)
    private val yellow      = Color(0xFFFFE066)
    private val pink        = Color(0xFFFF8FAB)
    private val white        = Color(0xFFF5F5F5)
    private val orange      = Color(0xFFFF9900)
    private val lightYellow = Color(0xFFFFFF88)
    private val leafGreen   = Color(0xFF5E9E52)
    private val centerDark  = Color(0xFFCC7000)   // stamen dots

    private val colorPairs = listOf(
        white to orange,
        yellow to centerDark,
        pink to lightYellow,
        yellow to lightYellow,
        pink to orange,
        white to lightYellow,
        yellow to orange,
    )

    private class BloomSpec(
        val xFrac: Float, val isBud: Boolean,
        val petalColor: Color, val centerColor: Color,
        val scale: Float, val petals: Int, val lean: Float,
    )

    // Fixed seed so the scatter is deterministic (same layout every launch) but not a visible grid.
    private val blooms: List<BloomSpec> = run {
        val rng = Random(20260724L)
        val slots = 20
        (0 until slots).map { i ->
            val slotW   = 1f / slots
            val jitter  = (rng.nextFloat() - 0.5f) * slotW * 0.8f
            val xFrac   = ((i + 0.5f) * slotW + jitter).coerceIn(0.015f, 0.985f)
            val (petal, center) = colorPairs[rng.nextInt(colorPairs.size)]
            BloomSpec(
                xFrac       = xFrac,
                isBud       = rng.nextFloat() < 0.18f,
                petalColor  = petal,
                centerColor = center,
                scale       = 0.40f + rng.nextFloat() * 0.28f,
                petals      = if (rng.nextBoolean()) 5 else 6,
                lean        = (rng.nextFloat() - 0.5f) * 0.10f,
            )
        }
    }

    private class GrassCluster(val xFrac: Float, val heights: List<Float>)

    // Height range for individual grass blades — capped at GRASS_MAX_HEIGHT so no blade towers
    // over the flowers, but varied blade-to-blade so the tuft doesn't read as a mowed hedge.
    private const val GRASS_MIN_HEIGHT = 0.32f
    private const val GRASS_MAX_HEIGHT = 0.78f

    private val grassClusters: List<GrassCluster> = run {
        val rng = Random(20260724L + 1)
        val slots = 28
        (0 until slots).map { i ->
            val slotW  = 1f / slots
            val jitter = (rng.nextFloat() - 0.5f) * slotW * 0.9f
            val xFrac  = ((i + 0.5f) * slotW + jitter).coerceIn(0f, 1f)
            val heights = List(4) { GRASS_MIN_HEIGHT + rng.nextFloat() * (GRASS_MAX_HEIGHT - GRASS_MIN_HEIGHT) }
            GrassCluster(xFrac, heights)
        }
    }

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val groundY = baseY + GROUND_DROP * u

        // Grass tufts first (behind blooms), spread across the full width.
        grassClusters.forEach { c -> drawGrassBlades(u, size.width * c.xFrac, groundY, c.heights) }

        // Buds and blooms, left to right across the full width.
        blooms.forEach { b ->
            val x = size.width * b.xFrac
            if (b.isBud) {
                drawBud(u, x, groundY, b.petalColor, b.scale * 0.78f)
            } else {
                drawFlower(u, x, groundY, b.petalColor, b.centerColor, b.scale, b.petals, b.lean)
            }
        }
    }

    private fun DrawScope.drawFlower(
        u: Float, x: Float, groundY: Float,
        petalColor: Color, centerColor: Color, scale: Float,
        petals: Int, lean: Float
    ) {
        val s        = u * scale
        val topX     = x + lean * s
        val stemTopY = groundY - 1.05f * s

        // Stem (gently leaning)
        drawLine(
            color = stemGreen,
            start = Offset(x, groundY),
            end   = Offset(topX, stemTopY),
            strokeWidth = 0.09f * s,
            cap = StrokeCap.Round
        )

        // Two leaves on the stem
        leafAt(x - 0.01f * s, groundY - 0.42f * s, s * 0.95f, -1f)
        leafAt(x + 0.04f * s, groundY - 0.72f * s, s * 0.75f, 1f)

        // Petals — elongated ovals radiating from the bloom centre, with a soft tip sheen
        val petalW    = 0.18f * s
        val petalH    = 0.34f * s
        val petalDist = 0.24f * s
        val step      = 360f / petals
        for (i in 0 until petals) {
            withTransform({ rotate(i * step, pivot = Offset(topX, stemTopY)) }) {
                val cyP = stemTopY - petalDist
                drawOval(
                    color   = petalColor,
                    topLeft = Offset(topX - petalW / 2f, cyP - petalH / 2f),
                    size    = Size(petalW, petalH)
                )
                drawOval(
                    color   = white.copy(alpha = 0.35f),
                    topLeft = Offset(topX - petalW * 0.28f, cyP - petalH * 0.42f),
                    size    = Size(petalW * 0.56f, petalH * 0.40f)
                )
            }
        }

        // Bloom centre + ring of stamen dots + a small highlight
        drawCircle(centerColor, radius = 0.16f * s, center = Offset(topX, stemTopY))
        for (i in 0 until 5) {
            val a  = Math.toRadians(i * 72.0)
            val dx = (Math.cos(a) * 0.07f * s).toFloat()
            val dy = (Math.sin(a) * 0.07f * s).toFloat()
            drawCircle(centerDark, radius = 0.028f * s, center = Offset(topX + dx, stemTopY + dy))
        }
        drawCircle(white.copy(alpha = 0.5f), radius = 0.05f * s, center = Offset(topX - 0.04f * s, stemTopY - 0.04f * s))
    }

    /** A small leaf on the stem. dir = -1 for left, +1 for right. */
    private fun DrawScope.leafAt(ax: Float, ay: Float, s: Float, dir: Float) {
        val leaf = Path().apply {
            moveTo(ax, ay)
            cubicTo(ax + dir * 0.30f * s, ay - 0.06f * s, ax + dir * 0.34f * s, ay - 0.24f * s, ax + dir * 0.10f * s, ay - 0.26f * s)
            cubicTo(ax + dir * 0.06f * s, ay - 0.14f * s, ax + dir * 0.02f * s, ay - 0.05f * s, ax, ay)
            close()
        }
        drawPath(leaf, color = leafGreen)
        drawLine(
            color = stemGreen.copy(alpha = 0.5f),
            start = Offset(ax, ay),
            end   = Offset(ax + dir * 0.18f * s, ay - 0.19f * s),
            strokeWidth = 0.02f * s,
            cap = StrokeCap.Round
        )
    }

    /** A closed flower bud — short stem, green calyx, coloured tip. */
    private fun DrawScope.drawBud(u: Float, x: Float, groundY: Float, color: Color, scale: Float) {
        val s    = u * scale
        val topY = groundY - 0.85f * s
        drawLine(stemGreen, Offset(x, groundY), Offset(x + 0.03f * s, topY), strokeWidth = 0.08f * s, cap = StrokeCap.Round)
        leafAt(x, groundY - 0.34f * s, s * 0.8f, -1f)
        drawOval(leafGreen, topLeft = Offset(x - 0.10f * s, topY - 0.02f * s), size = Size(0.20f * s, 0.22f * s))
        drawOval(color, topLeft = Offset(x - 0.085f * s, topY - 0.16f * s), size = Size(0.17f * s, 0.22f * s))
        drawOval(white.copy(alpha = 0.35f), topLeft = Offset(x - 0.05f * s, topY - 0.14f * s), size = Size(0.06f * s, 0.12f * s))
    }

    private fun DrawScope.drawGrassBlades(u: Float, x: Float, groundY: Float, heights: List<Float>) {
        val offsets = listOf(-0.15f, 0f, 0.18f, -0.08f)
        offsets.forEachIndexed { i, dx ->
            val height = heights.getOrElse(i) { 0.55f }
            drawLine(
                color = leafGreen,
                start = Offset(x + dx * u, groundY),
                end   = Offset(x + dx * u + 0.1f * u, groundY - height * u),
                strokeWidth = 0.07f * u,
                cap = StrokeCap.Round
            )
        }
    }
}
