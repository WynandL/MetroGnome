package com.example.metrognome.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

/**
 * The app's corner radii: one scale, named by what the corner belongs to. A radius is chosen by
 * role, never by eye, so a new chip matches every other chip and nothing is "about 10".
 *
 * The scale steps down as things nest: a dialog (24) holds a card or an art panel (20 / 18),
 * which holds a button or strip (14), an inset or field (12), a chip (10), a small swatch (8).
 * Before this the UI carried 8, 10, 12, 14, 18 and 20 plus one-offs (7, 16, 23) with no rule
 * saying which was which.
 *
 * Do not write `RoundedCornerShape(n.dp)` in a screen or component; add a role here if one is
 * truly missing. The only literals left are asymmetric shapes (the feedback sheet's top corners)
 * and the cosmetic items' own art.
 */
object AppRadius {
    /** Dialogs, popups and overlay cards. */
    val Dialog = RoundedCornerShape(24.dp)
    /** The app card. */
    val Card = RoundedCornerShape(20.dp)
    /** An art or preview panel inside a dialog. */
    val Panel = RoundedCornerShape(18.dp)
    /** Action buttons, and strips the height of one (the mic nudge, a choice row). */
    val Button = RoundedCornerShape(14.dp)
    /** An inset inside a card: a meter, a readout, a text field, a square key. */
    val Inset = RoundedCornerShape(12.dp)
    /** Chips and other 34 dp controls. */
    val Chip = RoundedCornerShape(10.dp)
    /** Small swatches, cells and badges. */
    val Small = RoundedCornerShape(8.dp)
    /** Thin meter bars. */
    val Bar = RoundedCornerShape(2.dp)
    /** Fully rounded ends, whatever the height. */
    val Pill = RoundedCornerShape(50)
}
