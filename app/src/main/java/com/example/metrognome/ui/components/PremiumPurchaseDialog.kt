package com.example.metrognome.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.example.metrognome.ui.components.DialogCloseButton
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.ui.theme.AppColors

/**
 * Unified premium-purchase dialog used by every in-app purchase touchpoint
 * (BPM presets, practice mode, premium sounds, purchasable items).
 *
 * Layout:
 *   ┌──────────────────────────────────┐
 *   │  ★                          ×   │  badge top-left, close top-right
 *   │           Title                  │  bold gold heading
 *   │       [preview slot]             │  optional (item canvas, sound icon, …)
 *   │       description text           │  centred body
 *   │     [secondary action]           │  optional (e.g. sound preview button)
 *   │   ┌──────────────────────────┐   │
 *   │   │   Unlock — $1.99         │   │  primary CTA, gold filled
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
    previewContent: (@Composable () -> Unit)? = null,
    secondaryButton: (@Composable () -> Unit)? = null,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape           = RoundedCornerShape(24.dp),
            color           = AppColors.surfaceDeep,
            shadowElevation = 24.dp,
            modifier        = Modifier
                .padding(horizontal = 20.dp)
                .widthIn(min = 280.dp, max = 380.dp),
        ) {
            Column(
                modifier            = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // ── Top bar: premium badge + close ──────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    PremiumBadge()
                    Spacer(Modifier.weight(1f))
                    DialogCloseButton(onClick = onDismiss)
                }

                Spacer(Modifier.height(6.dp))

                // ── Title ───────────────────────────────────────────────────────
                Text(
                    text       = title,
                    color      = AppColors.gold,
                    fontSize   = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-0.5).sp,
                    textAlign  = TextAlign.Center,
                )

                Spacer(Modifier.height(18.dp))

                // ── Optional preview ────────────────────────────────────────────
                previewContent?.let {
                    it()
                    Spacer(Modifier.height(18.dp))
                }

                // ── Description ─────────────────────────────────────────────────
                Text(
                    text       = description,
                    color      = AppColors.textSecondary,
                    fontSize   = 13.sp,
                    lineHeight = 19.sp,
                    textAlign  = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                // ── Secondary button (e.g. sound preview) ───────────────────────
                secondaryButton?.let {
                    it()
                    Spacer(Modifier.height(12.dp))
                }

                // ── Primary state-aware action ──────────────────────────────────
                when {
                    alreadyUnlocked      -> StateMessage("✓  Already yours", AppColors.gold, bold = true)
                    isBillingConnecting  -> StateMessage("Loading…", AppColors.textMuted, italic = true)
                    !isAvailable         -> StateMessage("Unavailable", AppColors.textMuted, italic = true)
                    else                 -> {
                        PrimaryCta(
                            label       = actionLabel,
                            priceText   = priceText,
                            isPurchasing = isPurchasing,
                            onClick     = onPurchase,
                        )
                        Spacer(Modifier.height(10.dp))
                        RestoreLink(enabled = !isPurchasing, onClick = onRestore)
                    }
                }
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun PremiumBadge() {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(AppColors.gold.copy(alpha = 0.14f))
            .border(1.dp, AppColors.gold.copy(alpha = 0.45f), CircleShape),
    ) {
        Text(
            text       = "★",
            color      = AppColors.gold,
            fontSize   = 16.sp,
            fontWeight = FontWeight.Bold,
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
        isPurchasing       -> "Please wait…"
        priceText != null  -> "$label  —  $priceText"
        else               -> label
    }
    Surface(
        onClick     = onClick,
        enabled     = !isPurchasing,
        shape       = RoundedCornerShape(16.dp),
        color       = Color.Transparent,
        border      = BorderStroke(
            1.dp,
            if (!isPurchasing) AppColors.gold else AppColors.surfaceVariant,
        ),
        modifier    = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp),
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(vertical = 14.dp)) {
            Text(
                text          = text,
                color         = if (!isPurchasing) AppColors.gold else AppColors.textMuted,
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
