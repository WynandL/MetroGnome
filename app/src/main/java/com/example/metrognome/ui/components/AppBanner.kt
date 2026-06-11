package com.example.metrognome.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * The single transient top-of-screen banner asset, shared by every banner feed:
 * Gnotes earned, loyalty milestones, ad breaks, and any future one.
 *
 * The look (pill chrome, slide+fade animation, dot-separated layout) is defined
 * once here. Each feeder keeps its own queue and display timing, builds a
 * [BannerModel] describing what to show, and hands it to [BannerPill]; the
 * slide-in/out is handled by [TransientBannerHost]. To add a new banner: collect
 * your queue in a small composable, map the event to a [BannerModel], done.
 *
 * Drop the host feeders inside a Box at [Alignment.TopCenter] to float them above
 * screen content.
 */

/** One dot-separated trailing segment of muted context text. */
data class BannerSegment(
    val text: String,
    /** Render brighter/heavier — used to draw the eye (e.g. a "limit reached" line). */
    val strong: Boolean = false,
)

/**
 * Everything the shared pill needs to render one banner.
 *
 * Layout: `[icon] [lead][leadUnit] · seg · seg …`. The icon and lead share the
 * [accent] colour (gold reads as celebratory, purple as neutral). [lead] is the
 * emphasised headline token ("+12", "7"); omit it and the first segment becomes
 * the headline (e.g. an ad-break message or a "Daily limit reached" line).
 */
data class BannerModel(
    val accent: Color,
    val icon: ImageVector? = null,
    val lead: String? = null,
    val leadUnit: String? = null,
    val segments: List<BannerSegment> = emptyList(),
    val italic: Boolean = false,
)

/**
 * Shared slide-in/out wrapper. Identical motion for every banner so they feel like
 * one system. The caller owns [visible] (and therefore the auto-dismiss timing).
 */
@Composable
fun TransientBannerHost(
    visible: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    AnimatedVisibility(
        visible = visible,
        enter   = slideInVertically(tween(220)) { -it } + fadeIn(tween(220)),
        exit    = slideOutVertically(tween(280)) { -it } + fadeOut(tween(280)),
        modifier = modifier,
    ) {
        content()
    }
}

/** The pill itself — the reusable asset. */
@Composable
fun BannerPill(model: BannerModel) {
    val shape = RoundedCornerShape(50.dp)
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 10.dp)
            .background(AppColors.surfaceDeep, shape)
            .border(1.dp, model.accent.copy(alpha = 0.45f), shape)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment     = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        model.icon?.let { icon ->
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = model.accent,
                modifier           = Modifier.size(13.dp),
            )
        }

        if (model.lead != null) {
            Text(
                text       = model.lead,
                color      = model.accent,
                fontWeight = FontWeight.ExtraBold,
                fontSize   = 14.sp,
            )
            model.leadUnit?.let { unit ->
                Text(
                    text          = unit.uppercase(),
                    color         = model.accent.copy(alpha = 0.7f),
                    fontWeight    = FontWeight.Bold,
                    fontSize      = 10.sp,
                    letterSpacing = 1.5.sp,
                )
            }
        }

        model.segments.forEachIndexed { i, seg ->
            // A dot precedes a segment only when a headline (lead) or an earlier
            // segment sits before it — never when the segment is itself the headline.
            if (i > 0 || model.lead != null) {
                Text("·", color = AppColors.textMuted, fontSize = 13.sp)
            }
            Text(
                text       = seg.text,
                color      = if (seg.strong) AppColors.textSecondary else AppColors.textMuted,
                fontWeight = if (seg.strong) FontWeight.SemiBold else FontWeight.Normal,
                fontSize   = if (seg.strong) 13.sp else 12.sp,
                fontStyle  = if (model.italic) FontStyle.Italic else FontStyle.Normal,
            )
        }
    }
}
