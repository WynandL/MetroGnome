package com.example.metrognome.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/*
 * The app's icons. One family, one line: every small icon in the app is a single-colour glyph
 * drawn with a 1.5 dp line, whatever size it is shown at (see CLAUDE.md, "Icons"). Material's
 * filled icons used to sit beside the hand-drawn glyphs (TunerNeedleIcon, ChordGridIcon) and
 * read as a second, heavier family.
 *
 * Path data is Lucide's (ISC licensed, see THIRD_PARTY_LICENSES.md), the set the instrument
 * glyphs already came from, on its 24 x 24 grid. This file is generated: add an icon by adding
 * a row to tools/icons/gen_icons.py and running it, not by pasting path data here.
 *
 * Use [AppIcon] exactly as Material's Icon was used; pass an [AppGlyph] wherever an icon
 * travels as a value (a button's leading icon, a banner model).
 */

/** One icon: Lucide path data, parsed on first use. [filled] is for transport shapes only. */
@Immutable
class AppGlyph internal constructor(private val data: List<String>, internal val filled: Boolean = false) {
    internal val paths: List<Path> by lazy(LazyThreadSafetyMode.NONE) {
        data.map { PathParser().parsePathString(it).toPath() }
    }
}

private const val GRID = 24f

/**
 * Draws [glyph] in [tint]. 24 dp unless [modifier] sets a size. The line is 1.5 dp at every
 * size (1.25 dp under 14 dp, where 1.5 closes the counters up): it is never scaled with the
 * icon, which is what keeps a 13 dp icon and a 26 dp icon looking like the same pen.
 */
@Composable
fun AppIcon(
    glyph: AppGlyph,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current,
) {
    val semantics = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription; role = Role.Image }
    } else Modifier
    Canvas(modifier = modifier.size(24.dp).then(semantics)) {
        val s = size.minDimension / GRID
        val line = (if (size.minDimension < 14.dp.toPx()) 1.25f else 1.5f).dp.toPx() / s
        val dx = (size.width - GRID * s) / 2f
        val dy = (size.height - GRID * s) / 2f
        scale(s, s, pivot = Offset.Zero) {
            drawContext.transform.translate(dx / s, dy / s)
            val stroke = Stroke(width = line, cap = StrokeCap.Round, join = StrokeJoin.Round)
            glyph.paths.forEach { path ->
                if (glyph.filled) drawPath(path, tint)
                drawPath(path, tint, style = stroke)
            }
        }
    }
}

object AppIcons {
    /** lucide "award" (was Material WorkspacePremium). */
    val Award = AppGlyph(listOf("m15.477 12.89 1.515 8.526a.5.5 0 0 1-.81.47l-3.58-2.687a1 1 0 0 0-1.197 0l-3.586 2.686a.5.5 0 0 1-.81-.469l1.514-8.526", "M6 8a6 6 0 1 0 12 0a6 6 0 1 0 -12 0Z"))
    /** lucide "bell" (was Material Notifications). */
    val Bell = AppGlyph(listOf("M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9", "M10.3 21a1.94 1.94 0 0 0 3.4 0"))
    /** lucide "zap" (was Material Bolt). */
    val Bolt = AppGlyph(listOf("M4 14a1 1 0 0 1-.78-1.63l9.9-10.2a.5.5 0 0 1 .86.46l-1.92 6.02A1 1 0 0 0 13 10h7a1 1 0 0 1 .78 1.63l-9.9 10.2a.5.5 0 0 1-.86-.46l1.92-6.02A1 1 0 0 0 11 14z"))
    /** lucide "check" (was Material Check). */
    val Check = AppGlyph(listOf("M20 6 9 17l-5-5"))
    /** lucide "chevron-down" (was Material ExpandMore). */
    val ChevronDown = AppGlyph(listOf("m6 9 6 6 6-6"))
    /** lucide "chevron-right" (was Material KeyboardArrowRight). */
    val ChevronRight = AppGlyph(listOf("m9 18 6-6-6-6"))
    /** lucide "x" (was Material Close). */
    val Close = AppGlyph(listOf("M18 6 6 18", "m6 6 12 12"))
    /** lucide "ear" (was Material Hearing). */
    val Ear = AppGlyph(listOf("M6 8.5a6.5 6.5 0 1 1 13 0c0 6-6 6-6 10a3.5 3.5 0 1 1-7 0", "M15 8.5a2.5 2.5 0 0 0-5 0v1a2 2 0 1 1 0 4"))
    /** lucide "pencil" (was Material Edit). */
    val Edit = AppGlyph(listOf("M21.174 6.812a1 1 0 0 0-3.986-3.987L3.842 16.174a2 2 0 0 0-.5.83l-1.321 4.352a.5.5 0 0 0 .623.622l4.353-1.32a2 2 0 0 0 .83-.497z", "m15 5 4 4"))
    /** lucide "audio-lines" (was Material GraphicEq). */
    val Equalizer = AppGlyph(listOf("M2 10v3", "M6 6v11", "M10 3v18", "M14 8v7", "M18 5v13", "M22 10v3"))
    /** lucide "flame" (was Material LocalFireDepartment). */
    val Flame = AppGlyph(listOf("M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.072-2.143-.224-4.054 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.153.433-2.294 1-3a2.5 2.5 0 0 0 2.5 2.5z"))
    /** lucide "heart" (was Material Favorite). */
    val Heart = AppGlyph(listOf("M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"))
    /** lucide "info" (was Material Info). */
    val Info = AppGlyph(listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M12 16v-4", "M12 8h.01"))
    /** lucide "lightbulb" (was Material Lightbulb). */
    val Lightbulb = AppGlyph(listOf("M15 14c.2-1 .7-1.7 1.5-2.5 1-.9 1.5-2.2 1.5-3.5A6 6 0 0 0 6 8c0 1 .2 2.2 1.5 3.5.7.7 1.3 1.5 1.5 2.5", "M9 18h6", "M10 22h4"))
    /** lucide "lock" (was Material Lock). */
    val Lock = AppGlyph(listOf("M5 11h14a2 2 0 0 1 2 2v7a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-7a2 2 0 0 1 2 -2Z", "M7 11V7a5 5 0 0 1 10 0v4"))
    /** lucide "mic" (was Material Mic). */
    val Mic = AppGlyph(listOf("M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z", "M19 10v2a7 7 0 0 1-14 0v-2", "M12 19L12 22"))
    /** lucide "mic-off" (was Material MicOff). */
    val MicOff = AppGlyph(listOf("M2 2L22 22", "M18.89 13.23A7.12 7.12 0 0 0 19 12v-2", "M5 10v2a7 7 0 0 0 12 5", "M15 9.34V5a3 3 0 0 0-5.68-1.33", "M9 9v3a3 3 0 0 0 5.12 2.12", "M12 19L12 22"))
    /** lucide "moon" (was Material ModeNight). */
    val Moon = AppGlyph(listOf("M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"))
    /** lucide "music-2" (was Material MusicNote). */
    val MusicNote = AppGlyph(listOf("M4 18a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", "M12 18V2l7 4"))
    /** lucide "pause" (was Material Pause). */
    val Pause = AppGlyph(listOf("M15 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-14a1 1 0 0 1 1 -1Z", "M7 4h2a1 1 0 0 1 1 1v14a1 1 0 0 1 -1 1h-2a1 1 0 0 1 -1 -1v-14a1 1 0 0 1 1 -1Z"), filled = true)
    /** lucide "piano" (was Material Piano). */
    val Piano = AppGlyph(listOf("M18.5 8c-1.4 0-2.6-.8-3.2-2A6.87 6.87 0 0 0 2 9v11a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-8.5C22 9.6 20.4 8 18.5 8", "M2 14h20", "M6 14v4", "M10 14v4", "M14 14v4", "M18 14v4"))
    /** lucide "play" (was Material PlayArrow). */
    val Play = AppGlyph(listOf("M6 3L20 12L6 21L6 3Z"), filled = true)
    /** lucide "circle-play" (was Material PlayCircle). */
    val PlayCircle = AppGlyph(listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M10 8L16 12L10 16L10 8Z"))
    /** lucide "activity" (was Material Stars). */
    val Pulse = AppGlyph(listOf("M22 12h-2.48a2 2 0 0 0-1.93 1.46l-2.35 8.36a.25.25 0 0 1-.48 0L9.24 2.18a.25.25 0 0 0-.48 0l-2.35 8.36A2 2 0 0 1 4.49 12H2"))
    /** lucide "search" (was Material Search). */
    val Search = AppGlyph(listOf("M3 11a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z", "m21 21-4.3-4.3"))
    /** lucide "settings" (was Material Settings). */
    val Settings = AppGlyph(listOf("M12.22 2h-.44a2 2 0 0 0-2 2v.18a2 2 0 0 1-1 1.73l-.43.25a2 2 0 0 1-2 0l-.15-.08a2 2 0 0 0-2.73.73l-.22.38a2 2 0 0 0 .73 2.73l.15.1a2 2 0 0 1 1 1.72v.51a2 2 0 0 1-1 1.74l-.15.09a2 2 0 0 0-.73 2.73l.22.38a2 2 0 0 0 2.73.73l.15-.08a2 2 0 0 1 2 0l.43.25a2 2 0 0 1 1 1.73V20a2 2 0 0 0 2 2h.44a2 2 0 0 0 2-2v-.18a2 2 0 0 1 1-1.73l.43-.25a2 2 0 0 1 2 0l.15.08a2 2 0 0 0 2.73-.73l.22-.39a2 2 0 0 0-.73-2.73l-.15-.08a2 2 0 0 1-1-1.74v-.5a2 2 0 0 1 1-1.74l.15-.09a2 2 0 0 0 .73-2.73l-.22-.38a2 2 0 0 0-2.73-.73l-.15.08a2 2 0 0 1-2 0l-.43-.25a2 2 0 0 1-1-1.73V4a2 2 0 0 0-2-2z", "M9 12a3 3 0 1 0 6 0a3 3 0 1 0 -6 0Z"))
    /** lucide "skip-back" (was Material SkipPrevious). */
    val SkipBack = AppGlyph(listOf("M19 20L9 12L19 4L19 20Z", "M5 19L5 5"))
    /** lucide "skip-forward" (was Material SkipNext). */
    val SkipForward = AppGlyph(listOf("M5 4L15 12L5 20L5 4Z", "M19 5L19 19"))
    /** lucide "speech" (was Material RecordVoiceOver). */
    val Speech = AppGlyph(listOf("M8.8 20v-4.1l1.9.2a2.3 2.3 0 0 0 2.164-2.1V8.3A5.37 5.37 0 0 0 2 8.25c0 2.8.656 3.054 1 4.55a5.77 5.77 0 0 1 .029 2.758L2 20", "M19.8 17.8a7.5 7.5 0 0 0 .003-10.603", "M17 15a3.5 3.5 0 0 0-.025-4.975"))
    /** lucide "star" (was Material Star). */
    val Star = AppGlyph(listOf("M12 2L15.09 8.26L22 9.27L17 14.14L18.18 21.02L12 17.77L5.82 21.02L7 14.14L2 9.27L8.91 8.26L12 2Z"))
    /** lucide "square" (was Material Stop). */
    val Stop = AppGlyph(listOf("M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1 -2 2h-14a2 2 0 0 1 -2 -2v-14a2 2 0 0 1 2 -2Z"), filled = true)
    /** lucide "sun" (was Material LightMode). */
    val Sun = AppGlyph(listOf("M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z", "M12 2v2", "M12 20v2", "m4.93 4.93 1.41 1.41", "m17.66 17.66 1.41 1.41", "M2 12h2", "M20 12h2", "m6.34 17.66-1.41 1.41", "m19.07 4.93-1.41 1.41"))
    /** lucide "arrow-left-right" (was Material SwapHoriz). */
    val Swap = AppGlyph(listOf("M8 3 4 7l4 4", "M4 7h16", "m16 21 4-4-4-4", "M20 17H4"))
    /** lucide "target" (was Material TrackChanges). */
    val Target = AppGlyph(listOf("M2 12a10 10 0 1 0 20 0a10 10 0 1 0 -20 0Z", "M6 12a6 6 0 1 0 12 0a6 6 0 1 0 -12 0Z", "M10 12a2 2 0 1 0 4 0a2 2 0 1 0 -4 0Z"))
    /** lucide "thumbs-down" (was Material ThumbDown). */
    val ThumbDown = AppGlyph(listOf("M17 14V2", "M9 18.12 10 14H4.17a2 2 0 0 1-1.92-2.56l2.33-8A2 2 0 0 1 6.5 2H20a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2h-2.76a2 2 0 0 0-1.79 1.11L12 22a3.13 3.13 0 0 1-3-3.88Z"))
    /** lucide "thumbs-up" (was Material ThumbUp). */
    val ThumbUp = AppGlyph(listOf("M7 10v12", "M15 5.88 14 10h5.83a2 2 0 0 1 1.92 2.56l-2.33 8A2 2 0 0 1 17.5 22H4a2 2 0 0 1-2-2v-8a2 2 0 0 1 2-2h2.76a2 2 0 0 0 1.79-1.11L12 2a3.13 3.13 0 0 1 3 3.88Z"))
    /** lucide "timer" (was Material Timer). */
    val Timer = AppGlyph(listOf("M10 2L14 2", "M12 14L15 11", "M4 14a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z"))
    /** lucide "trash-2" (was Material Delete). */
    val Trash = AppGlyph(listOf("M3 6h18", "M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6", "M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2", "M10 11L10 17", "M14 11L14 17"))
    /** lucide "trophy" (was Material EmojiEvents). */
    val Trophy = AppGlyph(listOf("M6 9H4.5a2.5 2.5 0 0 1 0-5H6", "M18 9h1.5a2.5 2.5 0 0 0 0-5H18", "M4 22h16", "M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22", "M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22", "M18 2H6v7a6 6 0 0 0 12 0V2Z"))
    /** lucide "volume-x" (was Material VolumeOff). */
    val VolumeOff = AppGlyph(listOf("M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z", "M22 9L16 15", "M16 9L22 15"))
    /** lucide "volume-2" (was Material VolumeUp). */
    val VolumeUp = AppGlyph(listOf("M11 4.702a.705.705 0 0 0-1.203-.498L6.413 7.587A1.4 1.4 0 0 1 5.416 8H3a1 1 0 0 0-1 1v6a1 1 0 0 0 1 1h2.416a1.4 1.4 0 0 1 .997.413l3.383 3.384A.705.705 0 0 0 11 19.298z", "M16 9a5 5 0 0 1 0 6", "M19.364 18.364a9 9 0 0 0 0-12.728"))
    /** lucide "triangle-alert" (was Material Warning). */
    val Warning = AppGlyph(listOf("m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3", "M12 9v4", "M12 17h.01"))
    /** lucide "waves" (was Material Waves). */
    val Waves = AppGlyph(listOf("M2 6c.6.5 1.2 1 2.5 1C7 7 7 5 9.5 5c2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1", "M2 12c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1", "M2 18c.6.5 1.2 1 2.5 1 2.5 0 2.5-2 5-2 2.6 0 2.4 2 5 2 2.5 0 2.5-2 5-2 1.3 0 1.9.5 2.5 1"))
}
