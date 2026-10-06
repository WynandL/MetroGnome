package com.example.metrognome.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * Small circular tap button — used for ± increment controls in the tuner and speed trainer.
 *
 * [label] is typically "−" or "+". Tap area is controlled by [size]. Pass [contentDescription]
 * to say what the step changes ("Raise target tempo"): a bare "+" reads aloud as "plus" with
 * nothing to say what it raises (UI audit U06).
 */
@Composable
fun CircleButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    fontSize: TextUnit = 16.sp,
    contentDescription: String? = null,
    enabled: Boolean = true,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(AppColors.surfaceVariant)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .then(
                if (contentDescription != null) Modifier.semantics { this.contentDescription = contentDescription }
                else Modifier
            ),
    ) {
        Text(
            label,
            color = AppColors.textSecondary,
            fontSize = fontSize,
            fontWeight = FontWeight.Light,
            modifier = if (contentDescription != null) Modifier.clearAndSetSemantics {} else Modifier,
        )
    }
}
