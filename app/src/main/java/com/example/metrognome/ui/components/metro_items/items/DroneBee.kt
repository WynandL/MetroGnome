package com.example.metrognome.ui.components.metro_items.items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotateRad
import com.example.metrognome.ui.components.metro_items.MetroItem
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * A bumblebee holding station near Metro, earned by using the tuning drone.
 *
 * The pun is the point: a drone is a held note and a drone is a bee, and the item explains
 * itself the moment someone sees what unlocked it. It also gives the word an anchor for
 * anyone who met "drone" for the first time on the Tuner screen.
 *
 * Two details do the work of making it read as a bee rather than a striped blob:
 *
 *  - **The path is a figure of eight**, not a random drift. Fireflies wander because that
 *    is what fireflies do; a bee holds a deliberate, repeating patrol, and the closed loop
 *    is what makes the motion look purposeful. It also matches the tone it was earned by:
 *    steady, unhurried, going nowhere.
 *  - **The body points where it is going.** The heading is taken from the derivative of the
 *    path, so the bee banks into its turns. Without it the sprite slides sideways through
 *    the corners and instantly reads as a decal being dragged around.
 *
 * Animation is driven from `System.currentTimeMillis()`, which is safe here because
 * GnomeCanvas recomposes continuously from its own infinite transition (same as Fireflies).
 */
object DroneBee : MetroItem {

    override val id            = "drone_bee"
    override val displayName   = "Drone Bee"
    override val description   = "A bumblebee that found Metro's held note and decided to stay. It hums along on a slow figure of eight."
    override val earnedMessage = "Half an hour of drone! Metro held one note long enough that a bee turned up to hum along with him. It patrols the air beside him now, keeping the tone company."
    override val isBodyAttached = false

    /** Drawn after Metro, so the bee patrols in front of him rather than behind his hat. */
    override val isForeground = true

    // Spread out over a wandering path, so no single tap target would be honest.
    override fun hitCenter(u: Float): Offset? = null

    override fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float) =
        Offset(canvasW * HOME_X, canvasH * HOME_Y)

    // Must contain the whole patrol, not just the bee: the preview crops around
    // [previewCenter], which is the middle of the loop, while the bee is somewhere on it.
    // SPAN_X + half a body + a little haze.
    override fun previewRadius(u: Float) = u * 1.45f

    // Centre of the patrol, as a fraction of the canvas: upper right, clear of the hat.
    private const val HOME_X = 0.74f
    private const val HOME_Y = 0.30f

    /** Seconds for one full circuit of the figure of eight. Slow: this is a drone, not a wasp. */
    private const val LOOP_SECONDS = 7.5f

    /**
     * Half-width and half-height of the patrol, in units rather than canvas fractions.
     *
     * This started out as fractions of the canvas (like Fireflies, which genuinely are
     * spread across the whole scene) and that was wrong for a single object. The crop in
     * the collection card is sized in units, so with a canvas-relative span the two scale
     * apart: on a wide, short card the patrol grew to nearly twice the crop and the bee
     * spent half its loop outside the frame. Tying the patrol to the same unit the bee and
     * the crop are drawn in makes containment arithmetic instead of luck.
     *
     * Deliberately small, a little over a body length each way. A bee holding station over
     * one spot is what the item is: it turned up for a held note and stayed.
     */
    private const val SPAN_X = 0.55f
    private const val SPAN_Y = 0.30f

    private val bodyAmber   = Color(0xFFF2B33C)
    private val bodyDeep    = Color(0xFFC98416)
    private val stripeDark  = Color(0xFF2A1E10)
    private val headDark    = Color(0xFF241A0F)
    private val wingPale    = Color(0xFFDCE8FF)
    private val hazeAmber   = Color(0xFFFFC65A)

    override fun DrawScope.draw(u: Float, cx: Float, baseY: Float) {
        val t = (System.currentTimeMillis() % 600_000L) / 1000f
        val phase = t / LOOP_SECONDS * 2f * PI.toFloat()

        // Lissajous 1:2 — a figure of eight lying on its side. The x half-width is larger
        // than the y so the loop reads as a patrol rather than vertical bobbing.
        val spanX = u * SPAN_X
        val spanY = u * SPAN_Y
        val bx = size.width * HOME_X + sin(phase) * spanX
        val by = size.height * HOME_Y + sin(phase * 2f) * spanY

        // Heading from the path's own derivative, so the bee banks into its turns.
        val heading = atan2(cos(phase * 2f) * 2f * spanY, cos(phase) * spanX)

        // One unit is size.height / 18, so a body under about 0.9u stops reading as a bee
        // and becomes a striped dash. Sized against GlissieFairy, the other companion that
        // has to be recognisable while drifting.
        val bodyLength = u * 0.95f
        val bodyHeight = u * 0.55f

        // Soft haze around the bee: the visual equivalent of a hum, and it keeps the sprite
        // from disappearing against the darkest part of the sky.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(hazeAmber.copy(alpha = 0.20f), Color.Transparent),
                center = Offset(bx, by),
                radius = u * 1.05f,
            ),
            radius = u * 1.05f,
            center = Offset(bx, by),
        )

        rotateRad(radians = heading, pivot = Offset(bx, by)) {
            // ── Wings ────────────────────────────────────────────────────────────
            // Two per side, beating far too fast to follow, so they are drawn as the blur
            // the eye actually sees: a pale translucent oval whose height flutters. Drawing
            // crisp wings at this size reads as a moth pinned to a board.
            val beat = 0.55f + 0.45f * sin(t * 26f)
            val wingHeight = bodyHeight * 0.95f * beat + u * 0.04f
            // Far wing: set back and dimmer, which is all the depth needed at this scale.
            drawOval(
                color = wingPale.copy(alpha = 0.15f),
                topLeft = Offset(bx - bodyLength * 0.36f, by - bodyHeight * (0.06f + 0.85f * beat)),
                size = Size(bodyLength * 0.70f, wingHeight),
            )
            drawOval(
                color = wingPale.copy(alpha = 0.32f),
                topLeft = Offset(bx - bodyLength * 0.26f, by - bodyHeight * (0.14f + 1.00f * beat)),
                size = Size(bodyLength * 0.78f, wingHeight),
            )

            // ── Abdomen ──────────────────────────────────────────────────────────
            drawOval(
                brush = Brush.verticalGradient(
                    colors = listOf(bodyAmber, bodyDeep),
                    startY = by - bodyHeight / 2f,
                    endY = by + bodyHeight / 2f,
                ),
                topLeft = Offset(bx - bodyLength / 2f, by - bodyHeight / 2f),
                size = Size(bodyLength, bodyHeight),
            )

            // Stripes, shortened towards the tail so they follow the taper of the body
            // instead of running off its edge as straight bars.
            listOf(0.06f, 0.26f, 0.44f).forEachIndexed { i, offset ->
                val sx = bx - bodyLength * offset
                val halfHeight = bodyHeight * (0.46f - i * 0.07f)
                drawLine(
                    color = stripeDark,
                    start = Offset(sx, by - halfHeight),
                    end = Offset(sx, by + halfHeight),
                    strokeWidth = u * 0.075f,
                    cap = StrokeCap.Round,
                )
            }

            // ── Head and antennae ────────────────────────────────────────────────
            val headX = bx + bodyLength * 0.46f
            drawCircle(color = headDark, radius = bodyHeight * 0.36f, center = Offset(headX, by))
            listOf(-1f, 1f).forEach { side ->
                drawLine(
                    color = headDark,
                    start = Offset(headX + bodyHeight * 0.10f, by + side * bodyHeight * 0.16f),
                    end = Offset(headX + bodyHeight * 0.62f, by + side * bodyHeight * 0.52f),
                    strokeWidth = u * 0.045f,
                    cap = StrokeCap.Round,
                )
            }

            // A single catchlight on the upper-left of the abdomen, matching the scene's one
            // key light. Everything else here is flat fill; this is what makes it a volume.
            drawArc(
                color = Color.White.copy(alpha = 0.22f),
                startAngle = 190f,
                sweepAngle = 70f,
                useCenter = false,
                topLeft = Offset(bx - bodyLength * 0.40f, by - bodyHeight * 0.38f),
                size = Size(bodyLength * 0.70f, bodyHeight * 0.72f),
                style = Stroke(width = u * 0.05f, cap = StrokeCap.Round),
            )
        }
    }
}
