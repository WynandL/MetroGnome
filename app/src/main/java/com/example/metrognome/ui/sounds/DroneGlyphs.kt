package com.example.metrognome.ui.sounds

import com.example.metrognome.audio.drone.DroneBlend
import com.example.metrognome.audio.drone.DroneTimbre
import com.example.metrognome.ui.components.AppGlyph
import com.example.metrognome.ui.components.AppIcons

/**
 * The mark for each drone voice. Exhaustive `when`s, so a new timbre or blend does not build
 * until it has one. Kept out of audio/drone/ so the synthesis stays free of UI, as it is of
 * billing.
 */
fun droneGlyphFor(timbre: DroneTimbre): AppGlyph = when (timbre) {
    DroneTimbre.PURE   -> AppIcons.DronePure
    DroneTimbre.WARM   -> AppIcons.DroneWarm
    DroneTimbre.REED   -> AppIcons.DroneReed
    DroneTimbre.STRING -> AppIcons.DroneString
}

fun droneGlyphFor(blend: DroneBlend): AppGlyph = when (blend) {
    DroneBlend.ROOT   -> AppIcons.BlendRoot
    DroneBlend.OCTAVE -> AppIcons.BlendOctave
    DroneBlend.FIFTH  -> AppIcons.BlendFifth
}
