package com.example.metrognome.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.theme.AppColors

/**
 * The app's toggle. Off, the track is a dark inset with a hairline and a dim thumb, like
 * every other quiet surface; on, the track becomes the same raised purple key as the play
 * button ([raisedFace]) and the thumb catches the light in a gold-tinted ivory. Replaces
 * Material's `Switch` everywhere so toggles look like part of the app.
 */
@Composable
fun AppSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val trackShape = RoundedCornerShape(50)
    val thumbX by animateDpAsState(if (checked) 22.dp else 0.dp, tween(180), label = "switchThumb")
    val thumbTop by animateColorAsState(
        if (checked) lerp(AppColors.gold, Color.White, 0.55f) else AppColors.textDim,
        tween(180), label = "thumbTop",
    )
    val thumbBottom by animateColorAsState(
        if (checked) AppColors.gold else AppColors.controlInactive,
        tween(180), label = "thumbBottom",
    )
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .toggleable(
                value = checked,
                role = Role.Switch,
                // No indication: the default ripple is a rectangle drawn outside the pill's clip.
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onValueChange = onCheckedChange,
            )
            .clip(trackShape)
            .then(
                if (checked) Modifier.raisedFace(trackShape, AppColors.primaryPurple)
                else Modifier
                    .background(AppColors.background, trackShape)
                    .border(BorderStroke(1.dp, AppColors.surfaceVariant), trackShape)
            ),
    ) {
        Box(
            modifier = Modifier
                .offset(x = 4.dp + thumbX, y = 4.dp)
                .size(22.dp)
                .background(Brush.verticalGradient(listOf(thumbTop, thumbBottom)), CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
        )
    }
}
