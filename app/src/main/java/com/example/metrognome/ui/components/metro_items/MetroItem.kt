package com.example.metrognome.ui.components.metro_items

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope

interface MetroItem {
    val id: String
    val displayName: String
    val description: String
    val unlockCondition: String
    val earnedMessage: String       // shown when the user taps the item on Metro
    val isBodyAttached: Boolean     // true = drawn inside the withTransform block; false = background layer
    val isHeadAttached: Boolean get() = false  // true = moves with the head-bob transform

    /**
     * Centre of the tap target in body coordinates (after translate(cx, baseY)).
     * Return null for background/non-tappable items.
     */
    fun hitCenter(u: Float): Offset? = null
    fun hitRadius(u: Float): Float = u * 0.5f

    /**
     * Visual centre of this item in absolute canvas coordinates for the unlock preview box.
     * Used by ItemPreviewCanvas to centre + zoom the item.
     * Background items must override with their actual draw position.
     * Body-attached items are handled separately via hitCenter — no override needed.
     */
    fun previewCenter(canvasW: Float, canvasH: Float, u: Float, baseY: Float): Offset =
        Offset(canvasW * 0.5f, canvasH * 0.5f)

    /** Approximate half-extent (radius in px) of the item for preview zoom calculation. */
    fun previewRadius(u: Float): Float = u * 4f

    fun DrawScope.draw(u: Float, cx: Float, baseY: Float)
}
