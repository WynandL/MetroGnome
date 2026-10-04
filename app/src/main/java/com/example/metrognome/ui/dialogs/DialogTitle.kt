package com.example.metrognome.ui.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp

/**
 * The title of every dialog, popup and overlay: white, 18 sp bold. Centred by default; pass
 * [textAlign] = Start for a left-aligned header row. [fillWidth] = false for a title that sits
 * beside an icon. There is no other title style, so do not set a size or colour at a call site.
 */
@Composable
fun DialogTitle(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign = TextAlign.Center,
    fillWidth: Boolean = true,
) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        textAlign = textAlign,
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier).then(modifier),
    )
}
