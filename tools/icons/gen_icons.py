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
    # The nine metronome click voices, one mark each, for the sound stage (and any list of
    # sounds). Keyed by ClickSound.slug in ui/sounds/SoundGlyphs.kt. Kept distinct from
    # Bell (notifications) and from the instrument-affinity glyphs, which mean "suits".
    ('SoundClassic',
     ['M8 20.5 10.6 3.5h2.8L16 20.5Z', 'M5.5 20.5h13', 'M12 17 13.1 7.5', 'M12.05 10.3h1.4l-.2 1.5h-1.4Z'],
     [3], [],
     'Classic: a pyramid metronome, its pendulum swung a little inside the case with the weight\n'
     '     * on the arm (the first cut swung the arm out through the side). Paths: body, base, arm,\n'
     '     * weight (filled).'),
    ('SoundHiHat',
     ['M3.5 8.4Q12 4.6 20.5 8.4Q12 9.6 3.5 8.4Z', 'M3.5 10.4Q12 9.2 20.5 10.4Q12 14.2 3.5 10.4Z', 'M12 6.3V20.5', 'M8.5 20.5 12 17.5l3.5 3'],
     [], [],
     'Hi-Hat: a pair of domed cymbals clamped face to face on the stand. Thin arcs read as an\n'
     '     * umbrella and then a coat hanger. Paths: top cymbal, bottom cymbal, rod, feet.'),
    ('SoundWood',
     ['M3.5 13Q3.5 11 5.5 11H16.5Q18.5 11 18.5 13V18.5Q18.5 20.5 16.5 20.5H5.5Q3.5 20.5 3.5 18.5Z', 'M6.5 15.5h9',
      'M21 3 12.6 9.6', 'M11.1 10.2a1.1 1.1 0 1 0 2.2 0a1.1 1.1 0 1 0 -2.2 0Z'],
     [3], [1],
     'Wood: a woodblock with its slot, a stick coming down to strike its top. A short beater in\n'
     '     * the corner read as a radio aerial, knock lines as a notification. Paths: block, slot\n'
     '     * (faint), stick, tip (filled).'),
    ('SoundWarm',
     ['M2 12c2-5 4-5 6 0s4 5 6 0 4-5 6 0', 'M5 17.5c1.4-2 2.6-2 4 0s2.6 2 4 0 2.6-2 4 0'],
     [], [1],
     'Warm: one rounded wave with its echo. Paths: wave, echo (faint).'),
    ('SoundBell',
     ['M12 2.5v3', 'M6.5 16.5Q6.5 6.5 12 5.5Q17.5 6.5 17.5 16.5Z', 'M4.5 16.5h15',
      'M10.8 19.3a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z'],
     [3], [],
     'Bell: a hand bell, handle up, clapper below. Not AppIcons.Bell, which means notifications.\n'
     '     * Paths: handle, body, lip, clapper (filled).'),
    ('SoundCrystalBowl',
     ['M3.5 11h17Q20 19 12 19Q4 19 3.5 11Z', 'M9 21h6', 'M7.5 7.5Q12 5 16.5 7.5', 'M10 4.3Q12 3.3 14 4.3'],
     [], [2, 3],
     'Crystal Bowl: a singing bowl on its foot, two rings of sound over it. Paths: bowl, foot,\n'
     '     * inner ring (faint), outer ring (faint).'),
    ('SoundKalimba',
     ['M5 20.5 3.5 9Q12 5.5 20.5 9L19 20.5Z', 'M8.5 7.6V13', 'M10.5 7.1V14.5', 'M12 6.9V15',
      'M13.5 7.1V14.5', 'M15.5 7.6V13', 'M10.8 17.7a1.2 1.2 0 1 0 2.4 0a1.2 1.2 0 1 0 -2.4 0Z'],
     [], [6],
     'Kalimba: the board with five tines, the middle one longest, and its sound hole. Paths:\n'
     '     * board, five tines, hole (faint).'),
    ('SoundCowbell',
     ['M8 6h8l3 13H5Z', 'M10 6V3.5h4V6', 'M5 19Q12 21.5 19 19', 'M9.5 10.5 8.5 15'],
     [], [3],
     'Cowbell: the bell, its handle, the open mouth and a glint. Paths: body, handle, mouth,\n'
     '     * glint (faint).'),
    ('SoundMetalKick',
     ['M4 12a8 8 0 1 0 16 0a8 8 0 1 0 -16 0Z', 'M8 12a4 4 0 1 0 8 0a4 4 0 1 0 -8 0Z',
      'M6.3 17.7 4 21', 'M17.7 17.7 20 21'],
     [], [1],
     'Metal Kick: a kick drum face-on on its spurs. Paths: head, port ring (faint), two spurs.'),
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
