package com.example.metrognome.ui.components.metro_items

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.example.metrognome.ui.components.metro_items.items.Fireflies
import com.example.metrognome.ui.components.metro_items.items.ForestFloorFlowers
import com.example.metrognome.ui.components.metro_items.items.ForestTree
import com.example.metrognome.ui.components.metro_items.items.GlissieFairy
import com.example.metrognome.ui.components.metro_items.items.GlowingMushroom
import com.example.metrognome.ui.components.metro_items.items.GoldChain
import com.example.metrognome.ui.components.metro_items.items.GoldEarring
import com.example.metrognome.ui.components.metro_items.items.LuxuryWatch
import com.example.metrognome.ui.components.metro_items.items.MoonAndStars
import com.example.metrognome.ui.components.metro_items.items.TorchPost
import com.example.metrognome.ui.overlays.ItemPreviewCanvas

@Composable
private fun ItemPreview(item: MetroItem) {
    ItemPreviewCanvas(MetroItemEntry(item, UnlockCondition.Always))
}

@Preview(name = "Gold Hoop Earring")
@Composable
private fun GoldEarringPreview() = ItemPreview(GoldEarring)

@Preview(name = "Luxury Watch")
@Composable
private fun LuxuryWatchPreview() = ItemPreview(LuxuryWatch)

@Preview(name = "Gold Chain")
@Composable
private fun GoldChainPreview() = ItemPreview(GoldChain)

@Preview(name = "Forest Tree")
@Composable
private fun ForestTreePreview() = ItemPreview(ForestTree)

@Preview(name = "Torch Post")
@Composable
private fun TorchPostPreview() = ItemPreview(TorchPost)

@Preview(name = "Glowing Mushroom")
@Composable
private fun GlowingMushroomPreview() = ItemPreview(GlowingMushroom)

@Preview(name = "Forest Floor Flowers")
@Composable
private fun ForestFloorFlowersPreview() = ItemPreview(ForestFloorFlowers)

@Preview(name = "Fireflies")
@Composable
private fun FirefliesPreview() = ItemPreview(Fireflies)

@Preview(name = "Moon and Stars")
@Composable
private fun MoonAndStarsPreview() = ItemPreview(MoonAndStars)

@Preview(name = "Glissie Fairy")
@Composable
private fun GlissieFairyPreview() = ItemPreview(GlissieFairy)
