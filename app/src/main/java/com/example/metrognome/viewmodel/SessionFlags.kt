package com.example.metrognome.viewmodel

/**
 * App-scoped flags indicating when a structured session owns the metronome.
 * MetronomeViewModel checks these before crediting free-play time, so that
 * Speed Trainer seconds don't also inflate the Metronome Gnotes category.
 */
object SessionFlags {
    var speedTrainerActive: Boolean = false
}
