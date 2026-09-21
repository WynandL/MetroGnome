package com.example.metrognome.audio.chords

/**
 * The two ways the Chord Finder can listen, named in code for the method each is built on
 * and on screen for what the player will notice.
 *
 * [MCLEOD] is the tuner's pipeline: McLeod's pitch method under the ambient gate, which
 * locks on a steady note and holds it through disturbance. Right for tuning; slow to let
 * go of a note when the next one arrives over it. To the player, **Steady**.
 *
 * [BROSSIER] is the onset-segmented note tracker ([NoteAnalyzer]): Brossier's labelling
 * model, an onset marks each note and its pitch is decided from the frames after it,
 * with the previous note's spectrum subtracted so a note is heard on its own while the
 * last one still rings. To the player, **Fast**.
 *
 * Both are offered on the Chords page's Listening card, because they are a real trade a
 * musician can feel: Fast hears each note as it is played, and so hears more of the
 * room; Steady counts only a note held for a moment, and so misses fast playing. The
 * captions say that and nothing about how; "onset", "pitch method" and the method names
 * stay in code and the Chord Loop, which is where [methodName] is used.
 */
enum class ChordEngine(
    /** The name on the chip. */
    val displayName: String,
    /** Under the chips: what the player gains and gives up. Two lines at most on a 280 dp column (about 80 characters). */
    val caption: String,
    /** The method's name, for the dev tools and logs. */
    val methodName: String,
) {
    MCLEOD(
        displayName = "Steady",
        caption = "Only counts a note you let ring a moment. Less sensitive to your surroundings.",
        methodName = "McLeod",
    ),
    BROSSIER(
        displayName = "Fast",
        caption = "Hears each note as you play it, even quick arpeggios. Best in a quiet room.",
        methodName = "Brossier",
    );

    companion object {
        /** What a fresh install starts on (Fast since 2026-09-21, after the phone battery). */
        val DEFAULT = BROSSIER
    }
}
