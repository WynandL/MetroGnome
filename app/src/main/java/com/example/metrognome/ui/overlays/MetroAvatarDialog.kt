package com.example.metrognome.ui.overlays

import androidx.compose.foundation.background
import com.example.metrognome.ui.theme.AppRadius
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp
import com.example.metrognome.ui.components.MetroAvatar
import com.example.metrognome.ui.components.AppCardDefaults
import com.example.metrognome.ui.components.PrimaryButton
import com.example.metrognome.ui.theme.AppColors

/**
 * Dev-tool dialog: shows [MetroAvatar] front and center, styled to match
 * [UnlockCelebrationOverlay]'s card so it previews the same likeness the reusable
 * asset will have wherever it's used next.
 */
@Composable
fun MetroAvatarDialog(onDismiss: () -> Unit) {
    AppWindowDialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(horizontal = 22.dp),
            shape = AppCardDefaults.DialogShape,
            color = AppColors.surfaceDeep,
            border = AppCardDefaults.Border,
            shadowElevation = 28.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OverlayEyebrow("✦  METRO  ✦", color = AppColors.gold)

                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .size(width = 220.dp, height = 260.dp)
                        .clip(AppRadius.Panel)
                        .background(
                            Brush.verticalGradient(listOf(AppColors.previewBgTop, AppColors.previewBgBottom))
                        ),
                ) {
                    MetroAvatar(modifier = Modifier.fillMaxWidth().height(260.dp))
                }

                Spacer(Modifier.height(26.dp))

                PrimaryButton("Close", onDismiss, Modifier.fillMaxWidth(0.65f))
            }
        }
    }
}
