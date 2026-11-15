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
    ('Play', 'play', True, 'PlayArrow'),
    ('PlayCircle', 'circle-play', False, 'PlayCircle'),
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
]


# The app's own marks: not from Lucide, drawn for Metro Gnome on the same 24 x 24 grid.
# (name, paths, indices of filled paths, indices of faint paths, KDoc)
CUSTOM = [
    ('Gnote',
     ['M13.4 1.5 9.3 11.2', 'M13.4 1.5l3.5 9.7', 'M7.6 11.2Q13.1 13 18.6 11.2', 'M13.1 12.6V18',
      'M6.28 20.14A3.7 2.9 -18 1 0 13.32 17.86A3.7 2.9 -18 1 0 6.28 20.14Z'],
     [4], [],
     'The Gnote: a music note wearing Metro\'s hat. The mark for the currency everywhere it is\n'
     '     * named (the Home pill, the earn banner, the two Gnotes dialogs). Not the bolt, which is\n'
     '     * the Speed Trainer\'s. Paths: hat left, hat right, brim, stem, head (filled).'),
    ('TuningFork',
     ['M9 2V11A3 3 0 0 0 15 11V2', 'M12 14V19', 'M10.6 20.6a1.4 1.4 0 1 0 2.8 0a1.4 1.4 0 1 0 -2.8 0Z'],
     [2], [],
     'A tuning fork: two tines open at the top, joined by a U, a stem and the ball it is held by.\n'
     '     * Drawn for the Tuner\'s fork calibration; the first one closed the tines over the top and\n'
     '     * read as an ice cream. Paths: tines and U, stem, ball (filled).'),
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

/*
 * The app's icons: path data only. Lucide's (ISC licensed, see THIRD_PARTY_LICENSES.md) on its
 * 24 x 24 grid, plus the app's own marks. This file is generated: add an icon by adding a row
 * to tools/icons/gen_icons.py and running it, not by pasting path data here. How a glyph is
 * drawn ([AppGlyph], [AppIcon], [drawGlyph]) lives in AppIcon.kt, which is hand-written.
 */

object AppIcons {
''' + '\n'.join(lines) + '\n}\n')
print(len(ICONS), 'icons')
