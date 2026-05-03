package com.example.metrognome.ui.theme

import androidx.compose.ui.graphics.Color

// Metro Gnome dark theme palette – Material3 required names
val Purple80 = Color(0xFFAB7DE0)

// Metro Gnome character colors — the brand palette
object GnomeColors {
    // Background
    val bgTop = Color(0xFF0A0818)
    val bgBottom = Color(0xFF16133A)

    // Skin (warm medium tone)
    val skin = Color(0xFFF0BC80)
    val skinHighlight = Color(0xFFFAD09A)
    val skinDark = Color(0xFFD8A060)

    // Santa beard & moustache — off-white/cream with gray shadow
    val beard = Color(0xFFF2EEEA)
    val beardShade = Color(0xFFB8B0A8)  // grey shadow + eyebrow color

    // Red garden gnome hat — classic and iconic
    val hatRed = Color(0xFFCC1818)
    val hatRedLight = Color(0xFFDD3535)
    val hatRedDark = Color(0xFF881010)

    // Near-black pinstripe suit — corporate & metropolitan
    val jacket = Color(0xFF111115)
    val jacketLight = Color(0xFF1C1C22)
    val jacketDark = Color(0xFF08080C)
    val pinstripe = Color(0x1ECCCCCC)  // subtle grey stripe

    // White dress shirt & accessories
    val shirt = Color(0xFFF8F4EE)
    val tie = Color(0xFFAA1E2E)  // deep red bow tie
    val belt = Color(0xFF0A0A0E)
    val beltBuckle = Color(0xFFFFD700)
    val buttonGold = Color(0xFFFFD700)

    // Slim dark suit trousers
    val pants = Color(0xFF0E0E14)
    val pantsHighlight = Color(0xFF1A1A24)

    // Red Oxford dress shoes
    val shoe = Color(0xFFBB1212)
    val shoeSole = Color(0xFFF0F0EE)
    val shoeGloss = Color(0x44FF8888)

    // Side-parted gray hair
    val hairGrey = Color(0xFFB5B0AB)
    val hairDark = Color(0xFF888280)

    // Subtle cheek blush & nose
    val cheek = Color(0x28EBA080)
    val nosePink = Color(0xFFCC8868)

    // Gold sunglasses — brand signature
    val glassFrame = Color(0xFFFFD700)
    val glassLens = Color(0xFF080818)
    val glassReflect = Color(0x664466AA)

    // Gold baton
    val batonGold = Color(0xFFFFD700)
    val batonDark = Color(0xFFBB9900)

    // FX
    val beatGlowAccent = Color(0x44FF8C00)
}

// ── App UI colors — used across screens and components ────────────────────────
object AppColors {
    // Backgrounds
    val background      = Color(0xFF0D0B1E)
    val surface         = Color(0xFF1E1B3A)
    val surfaceVariant  = Color(0xFF2A2550)
    val surfaceDeep     = Color(0xFF13102A)   // deep dialog / unlock card
    val surfaceDim      = Color(0xFF1A1838)   // game lanes, result card, mic bg
    val surfaceActive   = Color(0xFF1A1F3A)   // mic-mode / toggle-active surface

    // Text
    val textPrimary     = Color(0xFFEEEEFF)
    val textSecondary   = Color(0xFFCCCCEE)
    val textMuted       = Color(0xFF8080AA)
    val textSubtle      = Color(0xFF7070AA)
    val textDim         = Color(0xFF6060AA)
    val textAccent      = Color(0xFFAB7DE0)
    val textMutedBlue   = Color(0xFF8888BB)   // unlock overlay condition text

    // Brand
    val gold            = Color(0xFFFFD700)
    val primaryPurple   = Color(0xFF5B2D8A)
    val mediumPurple    = Color(0xFF7B4DB0)
    val deepPurple      = Color(0xFF3A2560)
    val darkPurple      = Color(0xFF2A1F55)
    val danger          = Color(0xFFCC2233)   // play-button stop, destructive

    // Controls
    val controlInactive = Color(0xFF666688)   // unchecked switch thumb
    val borderMuted     = Color(0xFF2A2860)   // inactive border (e.g. mic off)

    // Stop / quit (destructive but less alarming than danger)
    val stopRed         = Color(0xFFFF6666)
    val stopRedBorder   = Color(0xFF882222)

    // Unlock celebration item-preview canvas gradient
    val previewBgTop    = Color(0xFF080518)
    val previewBgBottom = Color(0xFF1A1040)

    // Confetti used in UnlockCelebrationOverlay
    val confetti = listOf(
        Color(0xFFFFD700), Color(0xFFAB7DE0), Color(0xFF4CAF50),
        Color(0xFFFF6B6B), Color(0xFF00BCD4), Color(0xFFFF9800),
        Color(0xFFF48FB1), Color(0xFFE8F5E9), Color(0xFF80DEEA),
        Color(0xFFCE93D8),
    )

    // Dev tools (shown only in debug builds)
    val devDarkBorder   = Color(0xFF333355)
    val devGrey         = Color(0xFF555577)
    val devBlue         = Color(0xFF66BBFF)
    val devBlueBorder   = Color(0xFF223355)
    val devRed          = Color(0xFFFF6B6B)
    val devRedBorder    = Color(0xFF552233)
}

// ── Rhythm game colors ────────────────────────────────────────────────────────
object GameColors {
    // Hit quality (gold = AppColors.gold for PERFECT)
    val good    = Color(0xFF7BE87B)
    val almost  = Color(0xFF7BB8FF)
    val miss    = Color(0xFFCC4444)

    // Note tints by travel position
    val noteAmber  = Color(0xFFCC8800)   // approaching
    val notePurple = Color(0xFF7B4DB8)   // far / just spawned

    // Beat position dots
    val beatDotAccent   = Color(0xFF9B5DE5)
    val beatDotDim      = Color(0xFF5B3D00)
    val beatDotInactive = Color(0xFF2A2845)

    // Mic equalizer bar (quiet end)
    val eqQuiet = Color(0xFF2D1F50)

    // Hit line idle colour / MIC label
    val hitLineIdle = Color(0xFF3A2A60)

    // Tolerance slider range labels + timing hint text
    val rangeBlue = Color(0xFF5566AA)
}

// ── Shared cosmetic-item palette ──────────────────────────────────────────────
object ItemPalette {
    // Gold accessories — GoldChain, GoldEarring, LuxuryWatch
    val goldLight = Color(0xFFFFE566)
    val goldMid   = Color(0xFFD4A800)
    val goldDark  = Color(0xFF8B6800)

    // Wood / trunk — ForestTree, TorchPost
    val woodBrown = Color(0xFF5C3317)
    val woodLight = Color(0xFF7A4A28)
}
