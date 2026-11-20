package com.example.metrognome.presets

/**
 * A saved tempo. A blank [name] means the user gave none: the pill then draws the Gnome tab's
 * note mark (AppIcons.MusicNote) beside the BPM. Never store a note character in the name;
 * the text font draws one unlike the app's note (and in a text field, as a dot).
 */
data class BpmPreset(val name: String, val bpm: Int)
