package com.example.metrognome.audio.metronome

import com.example.metrognome.audio.dsp.BiquadFilter
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.random.Random

/**
 * The Studio Hi-Hat (soundType 9, premium), synthesised as a real hat: a short **closed** hat
 * on every beat and a longer **open** hat on the accent, which is how a drummer accents on
 * the hats.
 *
 * Built (2026-11-21) as a replacement for the free Hi-Hat, a 9 kHz sine whose accent was
 * only ~4 dB louder and 10 ms longer, which the dev heard as accents that "all sound very
 * similar". The dev liked it enough to make it a paid voice instead; the free Hi-Hat stays
 * and got a lower accent note. Here the accent differs by *length*: at the same peak the
 * open hat still rings for many times longer, which no speaker limiting can take away
 * (`HiHatVoiceTest` asserts exactly that, at equal peaks).
 *
 * Synthesis, the TR-808's recipe: six square waves at inharmonic "metal" frequencies plus
 * white noise, high-passed so only their upper partials remain (the metallic shimmer),
 * lifted in a band around 7.5 kHz and gently low-passed so the energy sits where a phone
 * speaker plays (a small speaker sags above ~10 kHz, part of why the 9 kHz sine was thin).
 * The source runs through the filters for a pre-roll before the hit so they are settled,
 * then an envelope shapes it: 0.5 ms attack, exponential decay, and a raised-cosine fade
 * to true silence (an exponential never reaches zero; see the Metal Kick's lesson).
 *
 * Pure Kotlin, no Android, deterministic (fixed seed): the same buffer on every launch and
 * in every test. Broadband noise is what `ClapDetector` accepts as a clap, so this voice
 * must never sound during mic scoring; that holds because mic sessions force Classic
 * (`MetronomeViewModel.effectiveSoundType`).
 */
object HiHatVoice {

    const val CLOSED_MS = 70
    const val OPEN_MS = 300
    const val CLOSED_VOLUME = 0.72f   // Classic's levels: 0.72 on the beat, 0.92 on the accent
    const val OPEN_VOLUME = 0.92f

    private const val CLOSED_DECAY_MS = 9.0
    private const val OPEN_DECAY_MS = 65.0
    private const val CLOSED_FADE_MS = 20.0
    private const val OPEN_FADE_MS = 70.0
    private const val ATTACK_MS = 0.5
    private const val PREROLL_MS = 20

    /** TR-808 hat oscillators, Hz. Inharmonic, so their upper partials clash like metal. */
    private val METAL_HZ = doubleArrayOf(205.3, 304.4, 369.6, 522.7, 540.0, 800.0)
    private const val METAL_MIX = 0.55f
    private const val NOISE_MIX = 0.45f
    private const val HIGH_PASS_HZ = 4_000f
    private const val BAND_HZ = 7_000f
    private const val BAND_Q = 0.9f
    private const val BAND_LIFT = 1.5f
    private const val LOW_PASS_HZ = 9_000.0

    private const val SEED = 0x4848L   // "HH"

    /** The beat: a tight closed hat. */
    fun closed(sampleRate: Int = 44_100, volume: Float = CLOSED_VOLUME): ShortArray =
        render(sampleRate, CLOSED_MS, CLOSED_DECAY_MS, CLOSED_FADE_MS, volume)

    /** The accent: the hat opened, the same metal ringing on. */
    fun open(sampleRate: Int = 44_100, volume: Float = OPEN_VOLUME): ShortArray =
        render(sampleRate, OPEN_MS, OPEN_DECAY_MS, OPEN_FADE_MS, volume)

    private fun render(sr: Int, ms: Int, decayMs: Double, fadeMs: Double, volume: Float): ShortArray {
        val n = sr * ms / 1000
        val preroll = sr * PREROLL_MS / 1000
        val rng = Random(SEED)
        val hp1 = BiquadFilter.highPass(sr, HIGH_PASS_HZ)
        val hp2 = BiquadFilter.highPass(sr, HIGH_PASS_HZ)
        val band = BiquadFilter.bandPass(sr, BAND_HZ, BAND_Q)
        // One-pole low-pass: a gentle top roll-off, not a wall.
        val lpA = exp(-2.0 * PI * LOW_PASS_HZ / sr).toFloat()
        var lp = 0f
        val phase = DoubleArray(METAL_HZ.size)

        val tone = FloatArray(n)
        for (j in 0 until n + preroll) {
            var metal = 0f
            for (k in METAL_HZ.indices) {
                phase[k] += METAL_HZ[k] / sr
                if (phase[k] >= 1.0) phase[k] -= 1.0
                metal += if (phase[k] < 0.5) 1f else -1f
            }
            metal /= METAL_HZ.size
            val noise = rng.nextFloat() * 2f - 1f
            val bright = hp2.process(hp1.process(METAL_MIX * metal + NOISE_MIX * noise))
            val shaped = bright + BAND_LIFT * band.process(bright)
            lp = (1f - lpA) * shaped + lpA * lp
            if (j >= preroll) tone[j - preroll] = lp
        }

        val attack = (ATTACK_MS * sr / 1000).toInt().coerceAtLeast(1)
        val tau = decayMs * sr / 1000
        val fade = (fadeMs * sr / 1000).toInt().coerceAtMost(n)
        for (i in 0 until n) {
            val rise = if (i < attack) i.toFloat() / attack else 1f
            val left = n - i
            val tail = if (left < fade) 0.5f * (1f - cos(PI * left / fade).toFloat()) else 1f
            tone[i] *= rise * exp(-i / tau).toFloat() * tail
        }

        val peak = tone.maxOf { abs(it) }
        val scale = if (peak > 0f) volume / peak else 0f
        return ShortArray(n) { i ->
            (tone[i] * scale * Short.MAX_VALUE).toInt()
                .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
        }
    }
}
