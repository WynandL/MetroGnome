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

    fun DrawScope.draw(u: Float, cx: Float, baseY: Float)
}
