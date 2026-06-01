package com.example.metrognome.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.ui.theme.AppColors

/**
 * Spring-in dialog shell used by all app dialogs.
 *
 * Handles the boilerplate: [Animatable] scale from 0.2 → 1.0 with a medium-bouncy spring,
 * [DialogProperties] (no platform default width), [Surface] with [AppColors.surfaceDeep],
 * 24 dp rounded corners, and centred [Column] padding.
 *
 * [minWidth] / [maxWidth] default to standard dialog sizing but can be overridden.
 * [scrollable] wraps the content column in a vertical scroll and caps the dialog height at
 * 85 % of the screen — use for tall dialogs like Speed Trainer that exceed small screens.
 */
@Composable
fun AppDialog(
    onDismiss: () -> Unit,
    minWidth: Dp = 280.dp,
    maxWidth: Dp = 360.dp,
    scrollable: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
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

    val screenHeight = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.height.toDp() }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = AppColors.surfaceDeep,
            shadowElevation = 24.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .widthIn(min = minWidth, max = maxWidth)
                .then(if (scrollable) Modifier.heightIn(max = screenHeight * 0.85f) else Modifier)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                },
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 22.dp, vertical = 20.dp)
                    .then(if (scrollable) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = content,
            )
        }
    }
}
