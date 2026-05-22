package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * An ornate cast-iron street lantern — a more metropolitan companion for Metro
 * than a rough wooden torch. A curved bracket arm extends from the iron post;
 * a glazed lantern hangs from the tip and sways gently like a pendulum (a nod
 * to the metronome). The flame inside flickers independently of the swing.
 *
 * Positioned at size.width * 0.76f (same anchor as the old torch).
 * Bracket arm extends left, toward Metro. A small eighth-note is engraved
 * in the iron post as an easter egg.
 *
 * Animation:
 *   Lantern swing  — sin(t * 0.72) * 3.5°, period ≈ 8.7 s
 *   Flame flicker  — sin(t * 4.0) lateral sway, independent of swing
 */
object TorchPost : MetroItem {

    /**
     * SharedPreferences persistence key — MUST NOT be renamed or reassigned.
     *
     * History: before v4.3 this item was a plain wooden torch. It was completely
     * redesigned into a Street Lantern in v4.3 (new visual, new displayName, same unlock
     * condition). The visual change is cosmetic-only; the key is the stable identity.
     *
     * This value is burned into real users' "metro_cosmetics" SharedPreferences in two
     * separate StringSets managed by MetroItemTracker:
     *   "celebrated_item_ids"  — whether the unlock popup has already been shown
     *   "force_unlocked_ids"   — whether the item was unlocked via in-app purchase
     *
     * Renaming this constant without a migration silently erases both: the popup would
     * re-fire for every existing user, and purchased unlocks would disappear.
     *
     * If a rename is ever truly required, add a one-time migration in
     * MetroItemTracker.init() before reading any unlock state:
     *   migrateStringSetKey(prefs, "celebrated_item_ids", from = "torch_post", to = <newKey>)
     *   migrateStringSetKey(prefs, "force_unlocked_ids",  from = "torch_post", to = <newKey>)
     */
    private const val PREFS_KEY = "torch_post"

    override val id             = PREFS_KEY
    override val displayName    = "Street Lantern"
    override val description    = "An ornate iron lantern Metro installed so the forest path home is never dark after a late rehearsal."
    override val earnedMessage  = "15 rhythm games! Metro installed this iron lantern so the path home is never dark. Watch it sway."
    override val isBodyAttached  = false

    // Lantern body centre in body space. px = cx+5.2u (phones), bx = px-0.54u → 4.66u.
    // groundY = baseY-1.5u, postTopY = groundY-4.6u, lanternTop = postTopY+0.14u,
    // lantern mid = lanternTop+bodyH/2 → baseY-5.71u ≈ -5.7u.
    // Updated from old Offset(1.6u,-4.2u) which was correct for the pre-v4.3 wooden torch
    // at cx+3u. Moved with the lantern relocation to cx+5.2u in v4.3.
    override fun hitCenter(u: Float) = Offset(4.5f * u, -5.7f * u)
    override fun hitRadius(u: Float) = u * 0.9f

    // Preview centred on the lantern body, tight enough to show the flame + glass panels
    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(maxOf(canvasW * 0.82f, canvasW * 0.5f + 5.2f * u) - 0.54f * u, baseY - 4.65f * u)
    override fun previewRadius(u: Float) = u * 1.9f

    // ── Palette ───────────────────────────────────────────────────────────────
    private val ironDark   = Color(0xFF111120)
    private val ironMid    = Color(0xFF222236)
    private val ironLight  = Color(0xFF484870)
    private val glassAmber = Color(0x44FFA040)
    private val glassHot   = Color(0x33FFCC66)
    private val flameDeep  = Color(0xFFFF5500)
    private val flameMid   = Color(0xFFFFAA00)
    private val flameTip   = Color(0xFFFFEE44)
    private val glowWarm   = Color(0x28FF8800)
    private val glowFaint  = Color(0x0EFF5500)
    private val grassGreen = Color(0xFF4A7C3F)

    // Static per-frame allocation avoidance
    private val grassBlades = listOf(
        Triple(-0.30f, 0.50f, 78f),
        Triple(-0.12f, 0.58f, 68f),
        Triple( 0.06f, 0.52f, 100f),
        Triple( 0.20f, 0.46f, 112f),
    )
    private val bandFracs = listOf(0.30f, 0.63f)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t        = (System.nanoTime() / 1_000_000_000.0).toFloat()
        val px       = maxOf(size.width * 0.82f, cx + 5.2f * u)
        val groundY  = baseY - 1.5f * u
        val postH    = 4.6f * u
        val postW    = 0.13f * u
        val postTopY = groundY - postH

        // Bracket tip — where the lantern wire is attached
        val bx = px - 0.54f * u
        val by = postTopY - 0.08f * u

        // Lantern dimensions
        val bodyW      = 0.34f * u
        val bodyH      = 0.50f * u
        val capH       = 0.09f * u
        val wireH      = 0.22f * u
        val lanternTop = by + wireH

        // ── Ambient glow — drawn first, static (light on surroundings) ────────
        val glowCy = lanternTop + bodyH * 0.55f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(glowWarm, glowFaint, Color.Transparent),
                center = Offset(bx, glowCy),
                radius = bodyH * 3.2f,
            ),
            radius = bodyH * 3.2f,
            center = Offset(bx, glowCy),
        )

        // ── Ground shadow under post ───────────────────────────────────────────
        drawOval(
            color   = Color(0x1A000000),
            topLeft = Offset(px - 0.38f * u, groundY - 0.05f * u),
            size    = Size(0.76f * u, 0.10f * u),
        )

        // ── Grass tufts at post base ───────────────────────────────────────────
        for ((dx, len, ang) in grassBlades) {
            val rad = ang * PI.toFloat() / 180f
            drawLine(
                color       = grassGreen,
                start       = Offset(px + dx * u, groundY),
                end         = Offset(px + dx * u + cos(rad) * len * u * 0.42f,
                                     groundY - sin(rad) * len * u * 0.42f),
                strokeWidth = 0.07f * u,
                cap         = StrokeCap.Round,
            )
        }

        // ── Post base flange ───────────────────────────────────────────────────
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(ironDark, ironMid, ironDark),
                startX = px - postW * 1.15f,
                endX   = px + postW * 1.15f,
            ),
            topLeft      = Offset(px - postW * 1.15f, groundY - 0.13f * u),
            size         = Size(postW * 2.3f, 0.13f * u),
            cornerRadius = CornerRadius(0.04f * u),
        )

        // ── Post shaft ─────────────────────────────────────────────────────────
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colors = listOf(ironDark, ironMid, ironLight, ironMid, ironDark),
                startX = px - postW / 2f,
                endX   = px + postW / 2f,
            ),
            topLeft      = Offset(px - postW / 2f, postTopY),
            size         = Size(postW, groundY - postTopY - 0.13f * u),
            cornerRadius = CornerRadius(postW * 0.45f),
        )

        // Decorative ring bands
        for (frac in bandFracs) {
            val bandY = postTopY + (groundY - postTopY) * frac
            drawLine(
                color       = ironLight.copy(alpha = 0.55f),
                start       = Offset(px - postW * 0.78f, bandY),
                end         = Offset(px + postW * 0.78f, bandY),
                strokeWidth = 0.032f * u,
                cap         = StrokeCap.Round,
            )
        }

        // ── Eighth-note easter egg engraved in the post ───────────────────────
        val nr    = u * 0.060f
        val noteY = postTopY + (groundY - postTopY) * 0.46f
        drawOval(ironLight.copy(alpha = 0.45f),
            topLeft = Offset(px - nr * 1.1f, noteY - nr * 0.7f),
            size    = Size(nr * 2.2f, nr * 1.5f))
        drawLine(ironLight.copy(alpha = 0.45f),
            Offset(px + nr * 0.95f, noteY - nr * 0.2f),
            Offset(px + nr * 0.95f, noteY - nr * 3.1f),
            strokeWidth = nr * 0.38f, cap = StrokeCap.Round)
        drawLine(ironLight.copy(alpha = 0.45f),
            Offset(px + nr * 0.95f, noteY - nr * 3.1f),
            Offset(px + nr * 2.65f, noteY - nr * 2.4f),
            strokeWidth = nr * 0.32f, cap = StrokeCap.Round)

        // ── Bracket arm — S-curve from post top to lantern hook ───────────────
        val armPath = Path().apply {
            moveTo(px, postTopY + 0.09f * u)
            cubicTo(
                px - 0.10f * u, postTopY - 0.08f * u,
                px - 0.38f * u, postTopY - 0.16f * u,
                bx, by,
            )
        }
        drawPath(armPath, color = ironDark,              style = Stroke(0.115f * u, cap = StrokeCap.Round))
        drawPath(armPath, color = ironMid,               style = Stroke(0.090f * u, cap = StrokeCap.Round))
        drawPath(armPath, color = ironLight.copy(0.45f), style = Stroke(0.026f * u, cap = StrokeCap.Round))

        // Small hook circle at bracket tip
        drawCircle(ironMid,  radius = 0.055f * u, center = Offset(bx, by))
        drawCircle(ironLight.copy(alpha = 0.50f), radius = 0.025f * u, center = Offset(bx, by))

        // ── Swinging lantern ───────────────────────────────────────────────────
        val swingDeg = sin(t * 0.72f + 1.0f) * 3.5f

        withTransform({ rotate(swingDeg, pivot = Offset(bx, by)) }) {

            // Suspension wire
            drawLine(
                color       = ironMid,
                start       = Offset(bx, by + 0.03f * u),
                end         = Offset(bx, lanternTop),
                strokeWidth = 0.022f * u,
            )

            // Top crown cap
            drawRoundRect(
                color        = ironDark,
                topLeft      = Offset(bx - bodyW * 0.46f, lanternTop - capH),
                size         = Size(bodyW * 0.92f, capH),
                cornerRadius = CornerRadius(capH * 0.45f),
            )
            // Crown highlight
            drawRoundRect(
                color        = ironLight.copy(alpha = 0.30f),
                topLeft      = Offset(bx - bodyW * 0.44f, lanternTop - capH + 0.01f * u),
                size         = Size(bodyW * 0.88f, capH * 0.38f),
                cornerRadius = CornerRadius(capH * 0.40f),
            )

            // Glass body — amber fill
            drawRoundRect(
                color        = glassAmber,
                topLeft      = Offset(bx - bodyW / 2f, lanternTop),
                size         = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(bodyW * 0.13f),
            )
            // Inner hot-spot — warm glow pooling at the top from the flame
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(glassHot, Color(0x1EFF8800), Color.Transparent),
                    startY = lanternTop + bodyH * 0.08f,
                    endY   = lanternTop + bodyH * 0.72f,
                ),
                topLeft      = Offset(bx - bodyW * 0.33f, lanternTop + bodyH * 0.05f),
                size         = Size(bodyW * 0.66f, bodyH * 0.80f),
                cornerRadius = CornerRadius(bodyW * 0.10f),
            )

            // Iron frame — 2 vertical dividers creating 3 glass panels
            for (i in 1..2) {
                val fx = bx - bodyW / 2f + bodyW * i / 3f
                drawLine(
                    color       = ironDark.copy(alpha = 0.88f),
                    start       = Offset(fx, lanternTop + 0.022f * u),
                    end         = Offset(fx, lanternTop + bodyH - 0.022f * u),
                    strokeWidth = 0.028f * u,
                )
            }
            // Horizontal mid-bar
            drawLine(
                color       = ironDark.copy(alpha = 0.65f),
                start       = Offset(bx - bodyW * 0.44f, lanternTop + bodyH * 0.50f),
                end         = Offset(bx + bodyW * 0.44f, lanternTop + bodyH * 0.50f),
                strokeWidth = 0.020f * u,
            )
            // Outer frame
            drawRoundRect(
                color        = ironDark,
                topLeft      = Offset(bx - bodyW / 2f, lanternTop),
                size         = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(bodyW * 0.13f),
                style        = Stroke(width = 0.046f * u),
            )
            // Frame edge highlight — thin bright rim on the left (light from the scene)
            drawRoundRect(
                color        = ironLight.copy(alpha = 0.30f),
                topLeft      = Offset(bx - bodyW / 2f, lanternTop),
                size         = Size(bodyW, bodyH),
                cornerRadius = CornerRadius(bodyW * 0.13f),
                style        = Stroke(width = 0.012f * u),
            )

            // Inner flame — sways independently of the lantern swing
            val flameSway = sin(t * 4.0f + 0.6f) * 0.048f * u
            drawInnerFlame(bx + flameSway, lanternTop + bodyH * 0.87f, u * 0.25f, t)

            // Bottom finial — tapered iron drop
            drawPath(
                Path().apply {
                    moveTo(bx, lanternTop + bodyH + 0.10f * u)
                    lineTo(bx - bodyW * 0.19f, lanternTop + bodyH + 0.005f * u)
                    lineTo(bx + bodyW * 0.19f, lanternTop + bodyH + 0.005f * u)
                    close()
                },
                color = ironDark,
            )
        }
    }

    // Three concentric flames — deep orange base, amber mid, bright yellow core
    private fun DrawScope.drawInnerFlame(cx: Float, baseY: Float, u: Float, t: Float) {
        val fw = 0.28f * u
        val fh = 0.82f * u

        drawPath(Path().apply {
            moveTo(cx - fw, baseY)
            cubicTo(cx - fw * 0.80f, baseY - fh * 0.40f,
                    cx - fw * 0.22f, baseY - fh * 0.84f, cx, baseY - fh)
            cubicTo(cx + fw * 0.22f, baseY - fh * 0.84f,
                    cx + fw * 0.80f, baseY - fh * 0.40f, cx + fw, baseY)
            close()
        }, color = flameDeep)

        drawPath(Path().apply {
            moveTo(cx - fw * 0.60f, baseY)
            cubicTo(cx - fw * 0.46f, baseY - fh * 0.42f,
                    cx - fw * 0.14f, baseY - fh * 0.70f, cx, baseY - fh * 0.84f)
            cubicTo(cx + fw * 0.14f, baseY - fh * 0.70f,
                    cx + fw * 0.46f, baseY - fh * 0.42f, cx + fw * 0.60f, baseY)
            close()
        }, color = flameMid)

        drawPath(Path().apply {
            moveTo(cx - fw * 0.28f, baseY)
            cubicTo(cx - fw * 0.18f, baseY - fh * 0.36f,
                    cx - fw * 0.05f, baseY - fh * 0.52f, cx, baseY - fh * 0.62f)
            cubicTo(cx + fw * 0.05f, baseY - fh * 0.52f,
                    cx + fw * 0.18f, baseY - fh * 0.36f, cx + fw * 0.28f, baseY)
            close()
        }, color = flameTip)
    }
}
