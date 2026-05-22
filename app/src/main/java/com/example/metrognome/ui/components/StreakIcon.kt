package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/**
 * Metronome-pendulum streak icon.
 *
 * A gold bob caught mid-rightward-swing (15° past vertical), trailing a 40°
 * arc that fades deep-purple → amber → gold — the pendulum's own comet tail.
 * Three short sparkle rays radiate from the bob at the apex of the swing.
 *
 * Geometry scales with canvas size. Designed for 15 dp (streak pill) and
 * 24 dp (standalone).  Pivot sits at 85 % height; arm = 72 % height so the
 * arc tip clears the top edge with room for the glow halo.
 *
 * Compose angle convention: 0° = right, 90° = down, 270° = up.
 * Pendulum rests at 270° (straight up). Rightward swing → angles > 270°.
 * Trail spans 245° (ghost, 25° left of up) → 285° (current, 15° right of up).
 */
@Composable
fun StreakIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val cx     = size.width  / 2f
        val pivotY = size.height * 0.85f
        val arm    = size.height * 0.72f

        // ── Arc trail: four 10° segments, deep-purple → gold ─────────────
        val arcTopLeft = Offset(cx - arm, pivotY - arm)
        val arcSize    = Size(arm * 2f, arm * 2f)

        data class Seg(val start: Float, val color: Color, val alpha: Float)
        listOf(
            Seg(245f, Color(0xFF7B4DB0), 0.13f),
            Seg(255f, Color(0xFFAA66EE), 0.30f),
            Seg(265f, Color(0xFFFFAA22), 0.62f),
            Seg(275f, Color(0xFFFFD700), 0.92f),
        ).forEach { seg ->
            drawArc(
                color      = seg.color.copy(alpha = seg.alpha),
                startAngle = seg.start,
                sweepAngle = 10f,
                useCenter  = false,
                topLeft    = arcTopLeft,
                size       = arcSize,
                style      = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }

        // ── Pendulum rod ─────────────────────────────────────────────────
        val bobRad = Math.toRadians(285.0)
        val bobX   = cx     + cos(bobRad).toFloat() * arm
        val bobY   = pivotY + sin(bobRad).toFloat() * arm

        drawLine(
            color       = Color(0xFFFFD700),
            start       = Offset(cx, pivotY),
            end         = Offset(bobX, bobY),
            strokeWidth = 1.8f.dp.toPx(),
            cap         = StrokeCap.Round,
        )

        // ── Bob: soft glow + solid gold + white glint ─────────────────────
        drawCircle(Color(0x40FFD700), radius = 3.5f.dp.toPx(), center = Offset(bobX, bobY))
        drawCircle(Color(0xFFFFD700), radius = 2.6f.dp.toPx(), center = Offset(bobX, bobY))
        drawCircle(
            color  = Color(0xCCFFFFCC),
            radius = 1.0f.dp.toPx(),
            center = Offset(bobX - 0.6f.dp.toPx(), bobY - 0.6f.dp.toPx()),
        )

        // ── Sparkle rays — three short lines fanning from the bob ─────────
        val inner = 2.9f.dp.toPx()
        val outer = 4.1f.dp.toPx()
        for (rayDeg in listOf(-25.0, -5.0, 25.0)) {
            val r = Math.toRadians(rayDeg)
            drawLine(
                color       = Color(0xAAFFEE88),
                start       = Offset(bobX + cos(r).toFloat() * inner, bobY + sin(r).toFloat() * inner),
                end         = Offset(bobX + cos(r).toFloat() * outer,  bobY + sin(r).toFloat() * outer),
                strokeWidth = 0.9f.dp.toPx(),
                cap         = StrokeCap.Round,
            )
        }

        // ── Pivot dot ─────────────────────────────────────────────────────
        drawCircle(Color(0xFFFFD700), radius = 1.8f.dp.toPx(), center = Offset(cx, pivotY))
    }
}
