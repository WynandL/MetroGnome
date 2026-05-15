package com.example.metrognome.ui.dialogs

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.presets.BpmPreset
import com.example.metrognome.ui.theme.AppColors

/**
 * Long-press confirmation: "Delete this preset?" with destructive Delete and neutral Cancel.
 * Mirrors the iOS-style "delete app" flow — short, focused, irreversible.
 *
 * Springs in with a bounce and leads with a danger-tinted trash icon so the
 * destructive intent reads instantly.
 */
@Composable
fun PresetDeleteDialog(
    preset: BpmPreset,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit,
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
                .widthIn(min = 260.dp, max = 340.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(48.dp)
                        .background(AppColors.danger.copy(alpha = 0.15f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = null,
                        tint = AppColors.danger,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = "Delete preset?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    text = "\"${preset.name}\" — ${preset.bpm} BPM",
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(22.dp))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Surface(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Cancel",
                                color = AppColors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Surface(
                        onClick = onConfirmDelete,
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.danger.copy(alpha = 0.18f),
                        border = BorderStroke(1.dp, AppColors.danger.copy(alpha = 0.7f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "Delete",
                                color = AppColors.danger,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
