package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.theme.AppColors

/**
 * Shown when the user re-enables Groove Check on a device that is already calibrated: a quick
 * choice between turning it back on as-is, or running the microphone check again. Built on the
 * shared [AppDialog] shell; its button row mirrors [CalibrationConfirmDialog] so it reads as part
 * of the same family. Dismiss (tap outside) cancels and leaves the toggle off.
 */
@Composable
fun GrooveCheckRecalibrateDialog(
    onReEnable: () -> Unit,
    onRecalibrate: () -> Unit,
    onDismiss: () -> Unit,
) {
    AppDialog(onDismiss = onDismiss) {
        Text(
            "Groove Check",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "This phone is already set up for Groove Check. Turn it back on, or run the quick " +
                "microphone check again?",
            color = AppColors.textSecondary,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Surface(
                onClick = onRecalibrate,
                shape = RoundedCornerShape(14.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, AppColors.textDim.copy(alpha = 0.5f)),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Re-check", color = AppColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.width(10.dp))
            Surface(
                onClick = onReEnable,
                shape = RoundedCornerShape(14.dp),
                color = AppColors.gold.copy(alpha = 0.10f),
                border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.75f)),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("Re-enable", color = AppColors.gold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
