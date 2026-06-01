package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.points.pointsDisplayText
import com.example.metrognome.points.pointsEquivalent
import com.example.metrognome.ui.components.metro_items.METRO_ITEM_REGISTRY
import com.example.metrognome.ui.components.metro_items.MetroItemEntry
import com.example.metrognome.ui.overlays.ItemPreviewCanvas
import com.example.metrognome.ui.theme.AppColors

/**
 * Browsable catalog of every Metro item: what it looks like, whether it's
 * unlocked, and exactly what the user needs to do to unlock it.
 *
 * Reads directly from [METRO_ITEM_REGISTRY] so new items added to the registry
 * automatically appear here without any further changes.
 *
 * Sorted: already-unlocked items first (with a gold checkmark), then locked
 * items in ascending points-threshold order so users can see what's next.
 */
@Composable
fun ItemCatalogDialog(
    activeItemIds: Set<String>,
    onDismiss: () -> Unit,
) {
    val sorted = remember(activeItemIds) {
        METRO_ITEM_REGISTRY.sortedWith(
            compareBy(
                { if (it.item.id in activeItemIds) 0 else 1 },
                { it.condition.pointsEquivalent() },
            )
        )
    }
    val unlockedCount = sorted.count { it.item.id in activeItemIds }
    val totalCount    = sorted.size

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.88f),
            shape  = RoundedCornerShape(20.dp),
            color  = AppColors.surfaceDeep,
            border = BorderStroke(1.dp, AppColors.surfaceVariant),
        ) {
            Column {

                // ── Header ────────────────────────────────────────────────────
                Row(
                    modifier = Modifier.padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .background(AppColors.gold.copy(alpha = 0.14f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Stars,
                            contentDescription = null,
                            tint               = AppColors.gold,
                            modifier           = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text       = "Metro's Collection",
                            color      = AppColors.textPrimary,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize   = 16.sp,
                        )
                        Text(
                            text       = "Items Metro wears and the world he lives in",
                            color      = AppColors.textMuted,
                            fontSize   = 10.sp,
                            lineHeight = 13.sp,
                            modifier   = Modifier.padding(top = 1.dp),
                        )
                    }
                    // Unlocked count badge
                    Surface(
                        color  = AppColors.gold.copy(alpha = 0.15f),
                        shape  = RoundedCornerShape(50.dp),
                        border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.35f)),
                    ) {
                        Text(
                            text       = "$unlockedCount / $totalCount",
                            color      = AppColors.gold,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 12.sp,
                            modifier   = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                HorizontalDivider(color = AppColors.surfaceVariant)

                // ── Grid ──────────────────────────────────────────────────────
                LazyVerticalGrid(
                    columns             = GridCells.Fixed(2),
                    modifier            = Modifier.weight(1f),
                    contentPadding      = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        items = sorted,
                        key   = { it.item.id },
                    ) { entry ->
                        ItemCard(
                            entry      = entry,
                            isUnlocked = entry.item.id in activeItemIds,
                        )
                    }
                }

                // ── Close ─────────────────────────────────────────────────────
                HorizontalDivider(color = AppColors.surfaceVariant)
                Box(
                    modifier         = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    TextButton(onClick = onDismiss) {
                        Text(
                            text       = "Close",
                            color      = AppColors.gold,
                            fontWeight = FontWeight.Bold,
                            fontSize   = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemCard(entry: MetroItemEntry, isUnlocked: Boolean) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        color    = AppColors.surface,
        shape    = shape,
        border   = BorderStroke(
            1.dp,
            if (isUnlocked) AppColors.gold.copy(alpha = 0.40f) else AppColors.surfaceVariant,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            // ── Preview area ──────────────────────────────────────────────────
            Box {
                ItemPreviewCanvas(
                    entry    = entry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(220f / 170f),
                )
                if (!isUnlocked) {
                    // Frosted lock overlay
                    Box(
                        modifier         = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.50f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.Lock,
                            contentDescription = "Locked",
                            tint               = Color.White.copy(alpha = 0.75f),
                            modifier           = Modifier.size(22.dp),
                        )
                    }
                } else {
                    // Gold checkmark in top-right corner
                    Box(
                        modifier         = Modifier.matchParentSize().padding(6.dp),
                        contentAlignment = Alignment.TopEnd,
                    ) {
                        Icon(
                            imageVector        = Icons.Filled.CheckCircle,
                            contentDescription = "Unlocked",
                            tint               = AppColors.gold,
                            modifier           = Modifier.size(16.dp),
                        )
                    }
                }
            }

            // ── Info ──────────────────────────────────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
                Text(
                    text       = entry.item.displayName,
                    color      = AppColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize   = 12.sp,
                    maxLines   = 1,
                    overflow   = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.size(3.dp))
                if (isUnlocked) {
                    Text(
                        text       = "Unlocked",
                        color      = AppColors.gold,
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 10.sp,
                    )
                } else {
                    Text(
                        text       = entry.condition.pointsDisplayText(),
                        color      = AppColors.textMuted,
                        fontSize   = 10.sp,
                        lineHeight = 13.sp,
                        maxLines   = 2,
                        overflow   = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
