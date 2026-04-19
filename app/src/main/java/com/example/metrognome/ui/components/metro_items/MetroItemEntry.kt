package com.example.metrognome.ui.components.metro_items

/** Pairs a drawable item with its unlock condition. Stored in the registry. */
data class MetroItemEntry(
    val item: MetroItem,
    val condition: UnlockCondition
)
