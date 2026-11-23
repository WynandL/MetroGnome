package com.example.metrognome.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.example.metrognome.ui.components.AppGlyph
import com.example.metrognome.ui.components.AppIcon
import com.example.metrognome.ui.components.AppIcons
import com.example.metrognome.ui.components.RaisedControl
import com.example.metrognome.ui.overlays.ModalPresence
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.AppRadius
import kotlin.math.roundToInt

/** One switch in [HomeMoreMenu]: a word and its state on the left, the key on the right. */
internal class HomeMoreItem(
    val label: String,
    val caption: String,
    /** Every caption this switch can show, so the panel is sized for the widest and never moves. */
    val allCaptions: List<String>,
    val icon: AppGlyph,
    val active: Boolean,
    val contentDescription: String,
    val onClick: () -> Unit,
)

private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private const val OPEN_MS = 300
private const val CLOSE_MS = 200

/** Room the panel keeps around its keys; the morph starts as the key plus this rim. */
private val PANEL_PAD = 6.dp

/**
 * The "more" key at the end of Home's second row, holding the switches that are set once and
 * left (Sound, Screen) so the row keeps four keys you reach for mid-practice (2026-11-23: six
 * keys plus subdivision had made the row too busy).
 *
 * Tapping it lifts a panel out of the key: the panel grows up and to the left from the key's
 * own corner, the switches rise into it nearest-first, and the dots turn to lie flat. The key
 * itself stays where it was, inside the panel's bottom-right corner, and closes it again.
 * With the room to the left, each switch carries its name and state in words.
 *
 * The key wears a small gold dot while [badge] is set (Home passes "muted"): a silent
 * metronome with its cause hidden would look broken. Screen-on is harmless and often left on,
 * so it gets no dot (dev's call, 2026-11-23).
 * The panel is a popup window counted by [ModalPresence], so nothing that waits for a calm
 * moment shows over it; a tap outside or Back closes it.
 */
@Composable
internal fun HomeMoreMenu(
    items: List<HomeMoreItem>,
    badge: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val progress = remember { Animatable(0f) }
    LaunchedEffect(expanded) {
        progress.animateTo(
            targetValue = if (expanded) 1f else 0f,
            animationSpec = tween(if (expanded) OPEN_MS else CLOSE_MS, easing = EmphasizedDecelerate),
        )
    }
    var keySize by remember { mutableStateOf(IntSize.Zero) }
    val description = buildString {
        append("More: ")
        append(items.joinToString { "${it.label} ${it.caption}" })
    }

    Box(modifier = modifier.onSizeChanged { keySize = it }) {
        MoreKey(
            turn = 0f,
            open = false,
            badge = badge,
            description = description,
            onClick = { expanded = true },
            modifier = Modifier.fillMaxSize(),
        )
        if ((expanded || progress.value > 0f) && keySize != IntSize.Zero) {
            val padPx = with(LocalDensity.current) { PANEL_PAD.roundToPx() }
            Popup(
                popupPositionProvider = remember(padPx) { CornerAnchored(padPx) },
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                ModalPresence.Register()
                MorphPanel(
                    t = progress.value,
                    keySize = keySize,
                    items = items,
                    badge = badge,
                    onClose = { expanded = false },
                )
            }
        }
    }
}

/** Puts the popup's bottom-right corner [pad] beyond the key's, so its key lands on the key. */
private class CornerAnchored(private val pad: Int) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ) = IntOffset(
        x = anchorBounds.right + pad - popupContentSize.width,
        y = anchorBounds.bottom + pad - popupContentSize.height,
    )
}

@Composable
private fun MorphPanel(
    t: Float,
    keySize: IntSize,
    items: List<HomeMoreItem>,
    badge: Boolean,
    onClose: () -> Unit,
) {
    val density = LocalDensity.current
    val keyW = with(density) { keySize.width.toDp() }
    val keyH = with(density) { keySize.height.toDp() }
    val rise = with(density) { 14.dp.toPx() }

    Layout(
        content = {
            // The panel: one surface that starts as the key's rim and grows to hold the rows.
            Box(
                Modifier
                    .background(AppColors.card, AppRadius.Panel)
                    .border(1.dp, AppColors.goldBorder, AppRadius.Panel)
            )
            Column(
                modifier = Modifier.padding(PANEL_PAD),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items.forEachIndexed { index, item ->
                    // Nearest the key first: the bottom row leads, each one above follows.
                    val fromKey = items.size - index
                    val local = ((t - 0.2f - 0.15f * (fromKey - 1)) / 0.55f).coerceIn(0f, 1f)
                    MoreRow(
                        item = item,
                        keyW = keyW,
                        keyH = keyH,
                        modifier = Modifier.graphicsLayer {
                            alpha = local
                            translationY = (1f - local) * rise
                        },
                    )
                }
                MoreKey(
                    turn = t,
                    open = true,
                    badge = badge,
                    description = "Close",
                    onClick = onClose,
                    modifier = Modifier.size(keyW, keyH),
                )
            }
        },
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val content = measurables[1].measure(loose)
        val w = content.width
        val h = content.height
        val padPx = PANEL_PAD.roundToPx()
        val startW = keySize.width + 2 * padPx
        val startH = keySize.height + 2 * padPx
        val pw = lerp(startW.toFloat(), w.toFloat(), t).roundToInt().coerceIn(0, w)
        val ph = lerp(startH.toFloat(), h.toFloat(), t).roundToInt().coerceIn(0, h)
        val panel = measurables[0].measure(Constraints.fixed(pw, ph))
        layout(w, h) {
            panel.place(w - pw, h - ph)
            content.place(0, 0)
        }
    }
}

@Composable
private fun MoreRow(item: HomeMoreItem, keyW: androidx.compose.ui.unit.Dp, keyH: androidx.compose.ui.unit.Dp, modifier: Modifier) {
    Row(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = item.onClick,
            )
            .semantics(mergeDescendants = true) {
                contentDescription = item.contentDescription
                stateDescription = item.caption
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = item.label,
                color = AppColors.textPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            // Sized by the widest caption (the others laid out unseen in the same box): a toggle
            // that changed the panel's width moved its left edge, since it is pinned at the key.
            Box(contentAlignment = Alignment.CenterEnd) {
                (item.allCaptions + item.caption).distinct().forEach { caption ->
                    Text(
                        text = caption,
                        color = if (item.active) AppColors.goldSoft else AppColors.textDim,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        maxLines = 1,
                        modifier = Modifier.graphicsLayer { alpha = if (caption == item.caption) 1f else 0f },
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        CompactIconChip(
            icon = item.icon,
            contentDescription = item.contentDescription,
            active = item.active,
            onClick = item.onClick,
            modifier = Modifier.size(keyW, keyH),
        )
    }
}

/**
 * The dots key. [turn] lays the dots flat as the panel opens (vertical to horizontal), and
 * [open] lights the key while its panel is up.
 */
@Composable
private fun MoreKey(
    turn: Float,
    open: Boolean,
    badge: Boolean,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    RaisedControl(
        onClick = onClick,
        shape = AppRadius.Inset,
        tint = if (open) AppColors.primaryPurple else AppColors.surfaceVariant,
        modifier = modifier.semantics { contentDescription = description },
    ) {
        AppIcon(
            glyph = AppIcons.MoreVert,
            contentDescription = null,
            tint = if (open) Color.White else Color.White.copy(alpha = 0.6f),
            modifier = Modifier
                .align(Alignment.Center)
                .size(20.dp)
                .graphicsLayer { rotationZ = 90f * turn },
        )
        if (badge) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-6).dp, y = 6.dp)
                    .size(6.dp)
                    .background(AppColors.gold, CircleShape)
            )
        }
    }
}
