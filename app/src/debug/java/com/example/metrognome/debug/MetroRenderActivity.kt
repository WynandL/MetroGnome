package com.example.metrognome.debug

import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalDensity
import com.example.metrognome.ui.components.GnomeCanvas
import com.example.metrognome.viewmodel.BeatEvent
import kotlinx.coroutines.flow.MutableSharedFlow
import java.io.File

/**
 * Dev-only: renders Metro at an exact pixel size (u = height / 17) with no items and no
 * idle breathing, and writes it to filesDir/metro_render.png so it can be pulled with
 * `adb exec-out run-as <appId> cat files/metro_render.png` and overlaid on reference art.
 *
 * Launch: adb shell am start -n <appId>/com.example.metrognome.debug.MetroRenderActivity
 * Optional extras: --ei u <px per unit> (default 80).
 */
class MetroRenderActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val uPx = intent.getIntExtra("u", 80)
        val heightPx = uPx * 17
        val widthPx = uPx * 10
        val beats = MutableSharedFlow<BeatEvent>()

        setContent {
            val density = LocalDensity.current
            val layer = rememberGraphicsLayer()
            Box(
                Modifier
                    .size(
                        with(density) { widthPx.toDp() },
                        with(density) { heightPx.toDp() }
                    )
                    .drawWithContent {
                        layer.record { this@drawWithContent.drawContent() }
                        drawLayer(layer)
                    }
            ) {
                GnomeCanvas(
                    bpm = 120,
                    isPlaying = true,      // no idle breath offset
                    beatEvents = beats,
                    flashOnBeat = false,
                )
            }
            LaunchedEffect(Unit) {
                repeat(3) { withFrameNanos { } }
                val bmp = layer.toImageBitmap().asAndroidBitmap()
                val out = File(filesDir, "metro_render.png")
                out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                File(filesDir, "metro_render.done").writeText("${bmp.width}x${bmp.height}")
                finish()
            }
        }
    }
}
