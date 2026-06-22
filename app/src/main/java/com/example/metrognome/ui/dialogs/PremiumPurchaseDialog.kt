package com.example.metrognome.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.ui.overlays.drawConfetti
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.ItemPalette

/**
 * Unified premium-purchase dialog used by every in-app purchase touchpoint
 * (BPM presets, practice mode, premium sounds, purchasable items).
 *
 * This is not a plain dialog — it is a celebration. A full-screen confetti
 * backdrop, a spring-bounced card, a live showcase of the feature being sold,
 * a benefit list, and a shimmering gold call-to-action. The goal: clicking a
 * locked feature should feel like an invitation, not a toll booth.
 *
 * Layout:
 *   ┌──────────────────────────────────┐
 *   │                             ×    │  close top-right
 *   │          ✦  PREMIUM  ✦           │  gold eyebrow
 *   │             Title                │  bold gold heading
 *   │      ┌──────────────────┐        │
 *   │      │  live showcase   │        │  previewContent (timer, chips, …)
 *   │      └──────────────────┘        │
 *   │        description text          │  centred body
 *   │   ✓ benefit   ✓ benefit          │  optional highlight list
 *   │     [secondary action]           │  optional (e.g. sound preview)
 *   │   ┌──────────────────────────┐   │
 *   │   │  ★  Unlock — $1.99        │   │  primary CTA, gold filled + shimmer
 *   │   └──────────────────────────┘   │
 *   │     Already purchased? Restore   │  small dim link
 *   └──────────────────────────────────┘
 */
@Composable
fun PremiumPurchaseDialog(
    title: String,
    description: String,
    actionLabel: String,
    priceText: String?,
    isPurchasing: Boolean,
    isBillingConnecting: Boolean,
    isAvailable: Boolean,
    alreadyUnlocked: Boolean = false,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onDismiss: () -> Unit,
    highlights: List<String> = emptyList(),
    previewContent: (@Composable () -> Unit)? = null,
    belowDescription: (@Composable () -> Unit)? = null,
    secondaryButton: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        // ── Entrance animation ──────────────────────────────────────────────
        val cardScale = remember { Animatable(0.2f) }
        LaunchedEffect(Unit) {
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }

        val confettiTime by rememberInfiniteTransition(label = "premiumConfetti")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
                label = "confettiTime",
            )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            // Full-screen confetti drifting behind the card.
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawConfetti(confettiTime)
            }

            Surface(
                shape           = RoundedCornerShape(28.dp),
                color           = AppColors.surfaceDeep,
                shadowElevation = 28.dp,
                modifier        = Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(min = 290.dp, max = 392.dp)
                    .graphicsLayer {
                        scaleX = cardScale.value
                        scaleY = cardScale.value
                        alpha = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    }
                    // Absorb taps so they don't fall through to the dismiss backdrop.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier            = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // ── Close ───────────────────────────────────────────────
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        DialogCloseButton(onClick = onDismiss)
                    }

                    // ── Eyebrow ─────────────────────────────────────────────
                    Text(
                        text          = "✦   PREMIUM   ✦",
                        color         = AppColors.gold,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                    )

                    Spacer(Modifier.height(8.dp))

                    // ── Title ───────────────────────────────────────────────
                    Text(
                        text          = title,
                        color         = AppColors.gold,
                        fontSize      = 25.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        textAlign     = TextAlign.Center,
                    )

                    Spacer(Modifier.height(18.dp))

                    // ── Live feature showcase ───────────────────────────────
                    previewContent?.let {
                        it()
                        Spacer(Modifier.height(18.dp))
                    }

                    // ── Description ─────────────────────────────────────────
                    Text(
                        text       = description,
                        color      = AppColors.textSecondary,
                        fontSize   = 13.sp,
                        lineHeight = 19.sp,
                        textAlign  = TextAlign.Center,
                    )

                    // ── Below-description slot (e.g. instrument-affinity badges) ──
                    belowDescription?.let {
                        Spacer(Modifier.height(14.dp))
                        it()
                    }

                    // ── Benefit highlights ──────────────────────────────────
                    if (highlights.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            highlights.forEach { HighlightRow(it) }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // ── Secondary button (e.g. sound preview) ───────────────
                    secondaryButton?.let {
                        it()
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Primary state-aware action ──────────────────────────
                    when {
                        alreadyUnlocked     -> StateMessage("✓  Already yours", AppColors.gold, bold = true)
                        isBillingConnecting -> StateMessage("Loading…", AppColors.textMuted, italic = true)
                        !isAvailable        -> StateMessage("Unavailable", AppColors.textMuted, italic = true)
                        else                -> {
                            PrimaryCta(
                                label        = actionLabel,
                                priceText    = priceText,
                                isPurchasing = isPurchasing,
                                onClick      = onPurchase,
                            )
                            Spacer(Modifier.height(10.dp))
                            RestoreLink(enabled = !isPurchasing, onClick = onRestore)
                        }
                    }
                }
            }
        }
    }
}

/**
 * First-use "enable" dialog for features that are now free.
 *
 * Same visual flair as [PremiumPurchaseDialog] — confetti backdrop, spring entrance,
 * gold CTA — but with no billing. Eyebrow reads "SIGNATURE" instead of "PREMIUM"
 * to convey craftsmanship without implying a price tag.
 */
@Composable
fun FeatureEnableDialog(
    title: String,
    description: String,
    onEnable: () -> Unit,
    onDismiss: () -> Unit,
    highlights: List<String> = emptyList(),
    previewContent: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val cardScale = remember { Animatable(0.2f) }
        LaunchedEffect(Unit) {
            cardScale.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        }
        val confettiTime by rememberInfiniteTransition(label = "enableConfetti")
            .animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing)),
                label = "confettiTime",
            )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) { drawConfetti(confettiTime) }

            Surface(
                shape           = RoundedCornerShape(28.dp),
                color           = AppColors.surfaceDeep,
                shadowElevation = 28.dp,
                modifier        = Modifier
                    .padding(horizontal = 20.dp)
                    .widthIn(min = 290.dp, max = 392.dp)
                    .graphicsLayer {
                        scaleX = cardScale.value
                        scaleY = cardScale.value
                        alpha  = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Column(
                    modifier            = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 22.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(1f))
                        DialogCloseButton(onClick = onDismiss)
                    }

                    Text(
                        text          = "✦   SIGNATURE   ✦",
                        color         = AppColors.gold,
                        fontSize      = 11.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = 3.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text          = title,
                        color         = AppColors.gold,
                        fontSize      = 25.sp,
                        fontWeight    = FontWeight.ExtraBold,
                        letterSpacing = (-0.5).sp,
                        textAlign     = TextAlign.Center,
                    )
                    Spacer(Modifier.height(18.dp))

                    previewContent?.let {
                        it()
                        Spacer(Modifier.height(18.dp))
                    }

                    Text(
                        text       = description,
                        color      = AppColors.textSecondary,
                        fontSize   = 13.sp,
                        lineHeight = 19.sp,
                        textAlign  = TextAlign.Center,
                    )

                    if (highlights.isNotEmpty()) {
                        Spacer(Modifier.height(16.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            highlights.forEach { HighlightRow(it) }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    PrimaryCta(
                        label        = "Enable",
                        priceText    = null,
                        isPurchasing = false,
                        onClick      = onEnable,
                    )
                }
            }
        }
    }
}

// ── Showcase frame ────────────────────────────────────────────────────────────

/**
 * A framed stage for a live feature preview inside [PremiumPurchaseDialog].
 *
 * Draws a dark gradient backdrop with a soft gold rim, an optional caption
 * underneath, and centres whatever feature composable the caller hands in —
 * the actual practice timer, real preset chips, etc. Reusing live components
 * means the user sees exactly what they are buying, not a mock-up.
 */
@Composable
fun ShowcaseFrame(
    modifier: Modifier = Modifier,
    caption: String? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.verticalGradient(listOf(AppColors.previewBgTop, AppColors.previewBgBottom))
            )
            .border(1.dp, AppColors.gold.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp),
            contentAlignment = Alignment.Center,
            content = content,
        )
        if (caption != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text          = caption,
                color         = AppColors.gold.copy(alpha = 0.7f),
                fontSize      = 9.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 1.5.sp,
            )
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun HighlightRow(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(AppColors.gold.copy(alpha = 0.16f)),
        ) {
            Icon(
                imageVector        = Icons.Filled.Check,
                contentDescription = null,
                tint               = AppColors.gold,
                modifier           = Modifier.size(12.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Text(
            text       = text,
            color      = AppColors.textSecondary,
            fontSize   = 13.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun StateMessage(text: String, color: Color, bold: Boolean = false, italic: Boolean = false) {
    Text(
        text       = text,
        color      = color,
        fontSize   = 14.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle  = if (italic) FontStyle.Italic else FontStyle.Normal,
        modifier   = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun PrimaryCta(
    label: String,
    priceText: String?,
    isPurchasing: Boolean,
    onClick: () -> Unit,
) {
    val text = when {
        isPurchasing      -> "Please wait…"
        priceText != null -> "$label   -   $priceText"
        else              -> label
    }

    // Slow shimmer band sweeping across the gold fill — a touch of life.
    val shimmer by rememberInfiniteTransition(label = "ctaShimmer")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
            label = "ctaShimmerX",
        )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isPurchasing) {
                    Brush.horizontalGradient(
                        listOf(AppColors.surfaceVariant, AppColors.surfaceVariant)
                    )
                } else {
                    Brush.horizontalGradient(
                        listOf(ItemPalette.goldMid, AppColors.gold, ItemPalette.goldLight)
                    )
                }
            )
            .clickable(enabled = !isPurchasing, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!isPurchasing) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val bandHalf = size.width * 0.16f
                val bandX = shimmer * (size.width + bandHalf * 2f) - bandHalf
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                        startX = bandX - bandHalf,
                        endX = bandX + bandHalf,
                    ),
                    topLeft = Offset(bandX - bandHalf, 0f),
                    size = Size(bandHalf * 2f, size.height),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!isPurchasing) {
                Icon(
                    imageVector        = Icons.Filled.Star,
                    contentDescription = null,
                    tint               = AppColors.background,
                    modifier           = Modifier.size(17.dp),
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text          = text,
                color         = if (isPurchasing) AppColors.textMuted else AppColors.background,
                fontSize      = 15.sp,
                fontWeight    = FontWeight.ExtraBold,
                letterSpacing = 0.3.sp,
            )
        }
    }
}

@Composable
private fun RestoreLink(enabled: Boolean, onClick: () -> Unit) {
    Text(
        text     = "Already purchased? Restore",
        color    = if (enabled) AppColors.textDim else Color(0x22FFFFFF),
        fontSize = 12.sp,
        modifier = Modifier
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 6.dp, horizontal = 12.dp),
    )
}

/** Outlined preview-action button used inside the secondaryButton slot for sounds. */
@Composable
fun PreviewActionButton(label: String, onClick: () -> Unit) {
    Surface(
        onClick  = onClick,
        shape    = RoundedCornerShape(14.dp),
        color    = Color.Transparent,
        border   = BorderStroke(1.dp, AppColors.textAccent.copy(alpha = 0.55f)),
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text       = label,
                color      = AppColors.textAccent,
                fontSize   = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
