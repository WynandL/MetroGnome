# adb helpers for the audio probe. Source it from Git Bash:  . tools/audio-probe/p.sh
# Needs a debug build installed (AudioProbeReceiver is debug-only) and the phone on USB.
# Generated WAVs and results go to tools/audio-probe/work/ (gitignored).
export MSYS_NO_PATHCONV=1
ADB="$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe"
P=com.wynandl.metrognome
PROBE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
S="$PROBE/work"
mkdir -p "$S/wav" "$S/res"

adb() { "$ADB" "$@"; }
probe() { adb shell am broadcast -n $P/com.example.metrognome.debug.probe.AudioProbeReceiver --es cmd "$1" ${2:+--es file "$2"} >/dev/null; }

# UI dump as "text|content-desc|bounds" lines.
ui() { adb shell uiautomator dump /sdcard/ui.xml >/dev/null; adb shell cat /sdcard/ui.xml | grep -o '<node[^>]*>' | sed -n 's/.*text="\([^"]*\)".*content-desc="\([^"]*\)".*bounds="\([^"]*\)".*/\1|\2|\3/p' | grep -v '^||'; }
tap() { adb shell input tap "$1" "$2"; }
# Tap the centre of the first node whose "text|desc|bounds" line matches the regex $1.
tapText() {
  local b; b=$(ui | grep -m1 -E "$1" | sed 's/.*|\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]$/\1 \2 \3 \4/')
  [ -z "$b" ] && { echo "tapText: '$1' not on screen" >&2; return 1; }
  set -- $b; tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ))
}
# Bottom-bar tab by exact label: Gnome | Tuner | Chords | Rhythm | Settings
tab() { tapText "^$1\|" && sleep 2; }

# Push generated WAVs into the app's files dir.
pushwav() { for f in "$@"; do adb push "$S/wav/$f.wav" /data/local/tmp/ >/dev/null && adb shell run-as $P cp /data/local/tmp/$f.wav files/; done; }

# Play one WAV with the Tuner tab open and keep the per-hop trace: run <name> <seconds> <tag>
run() {
  probe trace_start; probe play $1.wav; sleep $2; probe trace_dump trace.csv; sleep 1
  adb shell run-as $P cat files/trace.csv > "$S/res/$3_$1.csv"; wc -l < "$S/res/$3_$1.csv"; }

# Groove Check from a cleared calibration, N times: groovecheck <n>. Saves each persisted
# record to work/res/gc_<i>.xml and the mic start/stop log to work/res/gc_rec.txt.
groovecheck() {
  for i in $(seq 1 "$1"); do
    adb shell am force-stop $P; adb shell run-as $P rm -f shared_prefs/mic_selftest_calibration.xml
    adb shell monkey -p $P -c android.intent.category.LAUNCHER 1 >/dev/null 2>&1; sleep 4
    tab Settings
    ui >/dev/null   # fresh dump of the Settings screen
    local sw; sw=$(adb shell cat /sdcard/ui.xml | grep -o '<node[^>]*checkable="true"[^>]*>' | head -1 | sed 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/')
    set -- $sw; tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2
    tapText "Start" || return 1
    local r=""; for t in $(seq 1 18); do sleep 5; r=$(ui | grep -E "all set|Not available|Try again|switch to the speaker" | head -1); [ -n "$r" ] && break; done
    echo "run $i: ${r%%|*}"
    adb shell run-as $P cat shared_prefs/mic_selftest_calibration.xml > "$S/res/gc_$i.xml"
    tapText "Done" >/dev/null 2>&1; tapText "Sweet" >/dev/null 2>&1
  done
  adb shell dumpsys audio | grep "rec start\|rec stop" | grep metrognome > "$S/res/gc_rec.txt"
  grep -ho 'latency_ms" value="[^"]*' "$S"/res/gc_*.xml | sed 's/.*"//'
}

# The mic diagnostics log of the last Practice / Speed Trainer session: micdump <tag>
micdump() { probe mic_dump mic.csv; sleep 1; adb shell run-as $P cat files/mic.csv > "$S/res/mic_$1.csv"; wc -l < "$S/res/mic_$1.csv"; }
