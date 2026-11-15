"""Generate ui/components/AppIcons.kt from Lucide SVGs.

    npm pack lucide-static@0.441.0 && tar -xzf lucide-static-0.441.0.tgz
    python tools/icons/gen_icons.py package/icons app/src/main/java/com/example/metrognome/ui/components/AppIcons.kt

To add an icon: add a row to ICONS (app name, lucide file, filled?, what it replaced) and rerun.
"""
import re
import sys
import xml.etree.ElementTree as ET

SRC, OUT = sys.argv[1], sys.argv[2]

# App name (mirrors the Material name it replaces) -> (lucide file, filled?)
ICONS = [
    ('Award', 'award', False, 'WorkspacePremium'),
    ('Bell', 'bell', False, 'Notifications'),
    ('Bolt', 'zap', False, 'Bolt'),
    ('Check', 'check', False, 'Check'),
    ('ChevronDown', 'chevron-down', False, 'ExpandMore'),
    ('ChevronRight', 'chevron-right', False, 'KeyboardArrowRight'),
    ('Close', 'x', False, 'Close'),
    ('Ear', 'ear', False, 'Hearing'),
    ('Edit', 'pencil', False, 'Edit'),
    ('Equalizer', 'audio-lines', False, 'GraphicEq'),
    ('Flame', 'flame', False, 'LocalFireDepartment'),
    ('Heart', 'heart', False, 'Favorite'),
    ('Info', 'info', False, 'Info'),
    ('Lightbulb', 'lightbulb', False, 'Lightbulb'),
    ('Lock', 'lock', False, 'Lock'),
    ('Mic', 'mic', False, 'Mic'),
    ('MicOff', 'mic-off', False, 'MicOff'),
    ('Moon', 'moon', False, 'ModeNight'),
    ('MusicNote', 'music-2', False, 'MusicNote'),
    ('Pause', 'pause', True, 'Pause'),
    ('Piano', 'piano', False, 'Piano'),
    ('Play', 'play', True, 'PlayArrow'),
    ('PlayCircle', 'circle-play', False, 'PlayCircle'),
    ('Pulse', 'activity', False, 'Stars'),
    ('Search', 'search', False, 'Search'),
    ('Settings', 'settings', False, 'Settings'),
    ('SkipBack', 'skip-back', False, 'SkipPrevious'),
    ('SkipForward', 'skip-forward', False, 'SkipNext'),
    ('Speech', 'speech', False, 'RecordVoiceOver'),
    ('Star', 'star', False, 'Star'),
    ('Stop', 'square', True, 'Stop'),
    ('Sun', 'sun', False, 'LightMode'),
    ('Swap', 'arrow-left-right', False, 'SwapHoriz'),
    ('Target', 'target', False, 'TrackChanges'),
    ('ThumbDown', 'thumbs-down', False, 'ThumbDown'),
    ('ThumbUp', 'thumbs-up', False, 'ThumbUp'),
    ('Timer', 'timer', False, 'Timer'),
    ('Trash', 'trash-2', False, 'Delete'),
    ('Trophy', 'trophy', False, 'EmojiEvents'),
    ('VolumeOff', 'volume-x', False, 'VolumeOff'),
    ('VolumeUp', 'volume-2', False, 'VolumeUp'),
    ('Warning', 'triangle-alert', False, 'Warning'),
    ('Waves', 'waves', False, 'Waves'),
]


# The app's own marks: not from Lucide, drawn for Metro Gnome on the same 24 x 24 grid.
# (name, paths, indices of filled paths, indices of faint paths, KDoc)
CUSTOM = [
    ('Gnote',
     ['M13.4 1.5 9.3 11.2', 'M13.4 1.5l3.5 9.7', 'M7.6 11.2Q13.1 13 18.6 11.2', 'M13.1 12.6V18',
      'M6.28 20.14A3.7 2.9 -18 1 0 13.32 17.86A3.7 2.9 -18 1 0 6.28 20.14Z', 'M11.2 7.4H15.5'],
     [4], [5],
     'The Gnote: a music note wearing Metro\'s hat. The mark for the currency everywhere it is\n'
     '     * named (the Home pill, the earn banner, the two Gnotes dialogs). Not the bolt, which is\n'
     '     * the Speed Trainer\'s. Paths: hat left, hat right, brim, stem, head (filled), hat band (faint).'),
]


def f(x):
    return ('%.3f' % float(x)).rstrip('0').rstrip('.')


def to_paths(svg):
    out = []
    for el in ET.parse(svg).getroot():
        tag = el.tag.split('}')[-1]
        a = el.attrib
        if tag == 'path':
            out.append(a['d'])
        elif tag == 'line':
            out.append('M%s %sL%s %s' % (f(a['x1']), f(a['y1']), f(a['x2']), f(a['y2'])))
        elif tag in ('circle', 'ellipse'):
            cx, cy = float(a['cx']), float(a['cy'])
            rx = float(a.get('r', a.get('rx', 0)))
            ry = float(a.get('r', a.get('ry', 0)))
            out.append('M%s %sa%s %s 0 1 0 %s 0a%s %s 0 1 0 %s 0Z' % (f(cx - rx), f(cy), f(rx), f(ry), f(2 * rx), f(rx), f(ry), f(-2 * rx)))
        elif tag == 'rect':
            x, y, w, h = (float(a.get(k, 0)) for k in ('x', 'y', 'width', 'height'))
            r = float(a.get('rx', a.get('ry', 0)))
            if r:
                out.append('M%s %sh%sa%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 %s %sh%sa%s %s 0 0 1 %s %sv%sa%s %s 0 0 1 %s %sZ' % (
                    f(x + r), f(y), f(w - 2 * r), f(r), f(r), f(r), f(r), f(h - 2 * r), f(r), f(r), f(-r), f(r),
                    f(-(w - 2 * r)), f(r), f(r), f(-r), f(-r), f(-(h - 2 * r)), f(r), f(r), f(r), f(-r)))
            else:
                out.append('M%s %sh%sv%sh%sZ' % (f(x), f(y), f(w), f(h), f(-w)))
        elif tag in ('polyline', 'polygon'):
            pts = re.findall(r'-?[\d.]+', a['points'])
            d = 'M' + 'L'.join('%s %s' % (pts[i], pts[i + 1]) for i in range(0, len(pts), 2))
            out.append(d + ('Z' if tag == 'polygon' else ''))
        else:
            raise SystemExit('unhandled element %s in %s' % (tag, svg))
    return out


lines = []
for name, src, filled, was in ICONS:
    ds = to_paths('%s/%s.svg' % (SRC, src))
    lines.append('    /** lucide "%s" (was Material %s). */' % (src, was))
    body = ', '.join('"%s"' % d for d in ds)
    lines.append('    val %s = AppGlyph(listOf(%s)%s)' % (name, body, ', filled = true' if filled else ''))

for name, ds, fills, faint, doc in CUSTOM:
    lines.append('')
    lines.append('    /**\n     * %s\n     */' % doc)
    lines.append('    val %s = AppGlyph(\n        listOf(%s),\n        filledPaths = setOf(%s), faintPaths = setOf(%s),\n    )' % (
        name, ', '.join('"%s"' % d for d in ds), ', '.join(map(str, fills)), ', '.join(map(str, faint))))

open(OUT, 'w', encoding='utf-8', newline='\n').write('''package com.example.metrognome.ui.components

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

/**
 * One icon: path data on a 24 x 24 grid, parsed on first use. [filled] fills every path and is
 * for transport shapes only. The app's own marks can fill single paths ([filledPaths]) and draw
 * others as the family's faint secondary line ([faintPaths], 38% and dropped under 14 dp).
 */
@Immutable
class AppGlyph internal constructor(
    private val data: List<String>,
    internal val filled: Boolean = false,
    internal val filledPaths: Set<Int> = emptySet(),
    internal val faintPaths: Set<Int> = emptySet(),
) {
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
            val small = size.minDimension < 14.dp.toPx()
            glyph.paths.forEachIndexed { i, path ->
                val faint = i in glyph.faintPaths
                if (faint && small) return@forEachIndexed
                val colour = if (faint) tint.copy(alpha = tint.alpha * 0.38f) else tint
                if (glyph.filled || i in glyph.filledPaths) drawPath(path, colour)
                drawPath(path, colour, style = stroke)
            }
        }
    }
}

object AppIcons {
''' + '\n'.join(lines) + '\n}\n')
print(len(ICONS), 'icons')
