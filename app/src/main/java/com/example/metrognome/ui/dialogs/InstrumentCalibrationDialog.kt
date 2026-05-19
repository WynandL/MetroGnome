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
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.audio.tuner.Tuner
import com.example.metrognome.ui.theme.AppColors
import com.example.metrognome.ui.theme.GameColors
import java.util.Locale
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Asks the musician to confirm that the currently-detected note represents their
 * instrument playing perfectly in tune. On confirm, the caller applies a calibration
 * factor derived from the reading so that the tuner agrees with the instrument.
 */
@Composable
fun InstrumentCalibrationDialog(
    reading: Tuner.Reading,
    onConfirm: () -> Unit,
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

    val noteName  = "${reading.noteName}${reading.octave}"
    val centsAbs  = reading.cents.roundToInt().let { if (it < 0) -it else it }
    val direction = if (reading.cents < 0f) "flat" else "sharp"
    val adjective = if (reading.cents < -0.5f || reading.cents > 0.5f)
        "$centsAbs¢ $direction" else "in tune"

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
                .widthIn(min = 280.dp, max = 360.dp)
                .graphicsLayer {
                    scaleX = cardScale.value
                    scaleY = cardScale.value
                    alpha = ((cardScale.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(52.dp)
                        .background(AppColors.gold.copy(alpha = 0.12f), CircleShape),
                ) {
                    Icon(
                        imageVector = Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = AppColors.gold,
                        modifier = Modifier.size(26.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "Calibrate to your $noteName?",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    buildAnnotatedString {
                        append("We're reading it as ")
                        withStyle(SpanStyle(
                            color = if (reading.inTune) GameColors.good else AppColors.gold,
                            fontWeight = FontWeight.Bold,
                        )) { append(adjective) }
                        append(". If your $noteName is perfectly in tune, we'll shift the tuner to match your instrument.")
                    },
                    color = AppColors.textSecondary,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                )

                if (reading.frequency > 0f) {
                    Spacer(Modifier.height(10.dp))
                    val target = String.format(
                        Locale.US, "%.1f Hz",
                        reading.frequency / 2.0.pow(reading.cents / 1200.0),
                    )
                    Text(
                        "Detected: ${String.format(Locale.US, "%.1f Hz", reading.frequency)}  →  Target: $target",
                        color = AppColors.textMuted,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }

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
                                "Cancel",
                                color = AppColors.textSecondary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                            )
                        }
                    }

                    Spacer(Modifier.width(10.dp))

                    Surface(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(14.dp),
                        color = AppColors.gold.copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, AppColors.gold.copy(alpha = 0.8f)),
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                "Calibrate",
                                color = AppColors.gold,
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
