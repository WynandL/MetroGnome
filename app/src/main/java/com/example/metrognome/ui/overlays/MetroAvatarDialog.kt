package com.example.metrognome.ui.overlays

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.metrognome.ui.components.MetroAvatar
import com.example.metrognome.ui.theme.AppColors

/**
 * Dev-tool dialog: shows [MetroAvatar] front and center, styled to match
 * [UnlockCelebrationOverlay]'s card so it previews the same likeness the reusable
 * asset will have wherever it's used next.
 */
@Composable
fun MetroAvatarDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.padding(horizontal = 22.dp),
            shape = RoundedCornerShape(28.dp),
            color = AppColors.surfaceDeep,
            shadowElevation = 28.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "✦  METRO  ✦",
                    color = AppColors.gold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )

                Spacer(Modifier.height(20.dp))

                Column(
                    modifier = Modifier
                        .size(width = 220.dp, height = 260.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.verticalGradient(listOf(AppColors.previewBgTop, AppColors.previewBgBottom))
                        ),
                ) {
                    MetroAvatar(modifier = Modifier.fillMaxWidth().height(260.dp))
                }

                Spacer(Modifier.height(26.dp))

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.primaryPurple),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth(0.65f),
                ) {
                    Text(
                        text = "Close",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
        }
    }
}
