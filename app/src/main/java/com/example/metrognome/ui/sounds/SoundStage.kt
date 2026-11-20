package com.example.metrognome.ui.sounds

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.metrognome.ui.overlays.AppWindowDialog
import androidx.compose.ui.window.DialogProperties
import com.example.metrognome.ui.components.AppCard
import com.example.metrognome.ui.components.AppFilterChip
import com.example.metrognome.ui.components.AppGlyph
import com.example.metrognome.ui.components.AppIcons
import com.example.metrognome.ui.components.PinnedLines
import com.example.metrognome.ui.components.PinnedSlot
import com.example.metrognome.ui.components.PlayStopKey
import com.example.metrognome.ui.components.PrimaryButton
import com.example.metrognome.ui.components.drawGlyph
import com.example.metrognome.ui.components.drawStar
import com.example.metrognome.ui.components.instruments.Instrument
import com.example.metrognome.ui.components.instruments.InstrumentAffinityBadges
import com.example.metrognome.ui.dialogs.DialogCloseButton
import com.example.metrognome.ui.overlays.OverlayEyebrow
import com.example.metrognome.ui.scene.drawNightSky
import com.example.metrognome.ui.scene.drawPineForest
import com.example.metrognome.ui.scene.starField
import com.example.metrognome.ui.theme.AppColors
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlin.math.abs

/*
 * The sound stage: every sound of one kind on a moonlit stage, swiped through one at a time,
 * heard with one tap, chosen or bought from the same screen. Built for the metronome's click
 * voices (MetronomeSoundStage.kt is that binding) and written to know nothing about them, so
 * the drone's voices, or anything else that is a set of sounds some of which cost money, can
 * be shown the same way by building a list of [StageSound] and wiring the callbacks.
 *
 * Shared pieces it reuses rather than redraws: the Rhythm stage's night (ui/scene), the
 * play/stop key, the primary button, the card, the instrument badges, the gold star that
 * marks premium (and stays after purchase, as on the chips), and the pinned-text slots, so
 * nothing on the screen moves as the sounds change (the no-jump rule).
 */

/** One sound on the stage. [key] is stable (a slug); [owned] is true for every free sound. */
@Immutable
data class StageSound(
    val key: String,
    val name: String,
    val tagline: String,
    val glyph: AppGlyph,
    val instruments: Set<Instrument> = emptySet(),
    val premium: Boolean = false,
    val owned: Boolean = true,
    val price: String? = null,
    /** False when the store did not return the product, so it cannot be bought right now. */
    val available: Boolean = true,
)

@Immutable
data class SoundStageState(
    val sounds: List<StageSound>,
    /** The sound in use now. */
    val selectedKey: String,
    /** The sound being auditioned, if any. */
    val previewingKey: String? = null,
    /** Auditions are paused (a mic-scored session is running); the key dims and says why. */
    val previewBlocked: Boolean = false,
    val purchasing: Boolean = false,
    val storeConnecting: Boolean = false,
    /**
     * True when an audition stands in for a sound that is already playing (the metronome is
     * running), so the stage says it is heard "on its own", not layered over the live one.
     */
    val previewReplacesLive: Boolean = false,
    /** One or two lines at the foot of the stage, for a rule the player must know. */
    val footnote: String? = null,
    /**
     * Optional groups shown as chips under the header (the drone's TIMBRE / BLEND); empty
     * for a stage with one set of sounds. [sounds] and [selectedKey] are the group at
     * [tabIndex]; the caller swaps them when [onTab] fires.
     */
    val tabs: List<String> = emptyList(),
    val tabIndex: Int = 0,
)

/**
 * The stage as a full-screen overlay, opening on [initialKey] (the sound in use if null).
 * Back and the X call [onClose]; the caller stops any audition there. [beats] pulses the moon and the in-use sound's ring with a click that is
 * already sounding (pass null where nothing is).
 */
@Composable
fun SoundStageOverlay(
    eyebrow: String,
    hint: String,
    state: SoundStageState,
    onPreview: (String) -> Unit,
    onStopPreview: () -> Unit,
    onChoose: (String) -> Unit,
    onBuy: (String) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    beats: Flow<*>? = null,
    initialKey: String? = null,
    onTab: (Int) -> Unit = {},
) {
    AppWindowDialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        SoundStage(eyebrow, hint, state, onPreview, onStopPreview, onChoose, onBuy, onRestore, onClose, beats, initialKey, onTab)
    }
}

/** The stage's content, for hosts that place it themselves. See [SoundStageOverlay]. */
@Composable
fun SoundStage(
    eyebrow: String,
    hint: String,
    state: SoundStageState,
    onPreview: (String) -> Unit,
    onStopPreview: () -> Unit,
    onChoose: (String) -> Unit,
    onBuy: (String) -> Unit,
    onRestore: () -> Unit,
    onClose: () -> Unit,
    beats: Flow<*>? = null,
    /** The sound to open on; the one in use when null. */
    initialKey: String? = null,
    onTab: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (state.sounds.isEmpty()) return

    // The click's pulse: 1 on each beat, easing back to 0.
    val glow = remember { Animatable(0f) }
    LaunchedEffect(beats) {
        beats?.collect {
            launch {
                glow.snapTo(1f)
                glow.animateTo(0f, tween(520, easing = LinearOutSlowInEasing))
            }
        }
    }
    val ambience = rememberInfiniteTransition(label = "stageAmbience")
    val t by ambience.animateFloat(
        initialValue = 0f, targetValue = 600f,
        animationSpec = infiniteRepeatable(tween(600_000, easing = LinearEasing)),
        label = "stageTime",
    )
    val ripple by ambience.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing), RepeatMode.Restart),
        label = "stageRipple",
    )

    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, tween(280, easing = LinearOutSlowInEasing)) }

    val stars = remember { starField(seed = 11) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                alpha = appear.value
                val s = 0.97f + 0.03f * appear.value
                scaleX = s; scaleY = s
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val dp = 1.dp.toPx()
            // The moon sits lower than on the Rhythm lane, clear of the header's hint line.
            drawNightSky(stars, size.width, size.height, dp, t, glow.value, moonY = 0.165f)
            drawPineForest(size.width, size.height)
        }

        Column(
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    OverlayEyebrow(eyebrow, color = AppColors.goldSoft)
                    Text(hint, color = AppColors.textMuted, fontSize = 12.sp, lineHeight = 16.sp)
                }
                DialogCloseButton(onClick = onClose)
            }

            if (state.tabs.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    state.tabs.forEachIndexed { i, label ->
                        AppFilterChip(selected = i == state.tabIndex, onClick = { if (i != state.tabIndex) onTab(i) }, label = label)
                    }
                }
            }

            // A fresh carousel per group, opening on that group's sound in use (or on
            // [initialKey] the first time), so switching tabs never lands mid-list.
            key(state.tabIndex) {
                // [initialKey] when it is in this group (the sound the stage was opened for),
                // else the group's sound in use.
                val initial = listOf(initialKey, state.selectedKey).firstNotNullOfOrNull { k ->
                    state.sounds.indexOfFirst { it.key == k }.takeIf { it >= 0 }
                } ?: 0
                val pager = rememberPagerState(initialPage = initial) { state.sounds.size }
                val focused = state.sounds[pager.currentPage.coerceIn(state.sounds.indices)]

                // Swiping away from a sound that is being auditioned ends the audition: what
                // is heard must always be the sound in the spotlight.
                LaunchedEffect(pager.currentPage) {
                    val p = state.previewingKey
                    if (p != null && p != state.sounds[pager.currentPage.coerceIn(state.sounds.indices)].key) onStopPreview()
                }

                Spacer(Modifier.weight(1f))

                SoundCarousel(
                    state = state,
                    pager = pager,
                    glow = glow.value,
                    ripple = ripple,
                    onTapFocused = { key ->
                        if (state.previewingKey == key) onStopPreview()
                        else if (!state.previewBlocked) onPreview(key)
                    },
                )
                Spacer(Modifier.height(10.dp))
                PageDots(state.sounds, pager.currentPage)

                Spacer(Modifier.weight(1f))

                SoundDetails(
                    sound = focused,
                    state = state,
                    onPreview = onPreview,
                    onStopPreview = onStopPreview,
                    onChoose = onChoose,
                    onBuy = onBuy,
                    onRestore = onRestore,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── The carousel ──────────────────────────────────────────────────────────────

private val MEDALLION = 184.dp

@Composable
private fun SoundCarousel(
    state: SoundStageState,
    pager: PagerState,
    glow: Float,
    ripple: Float,
    onTapFocused: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = ((maxWidth - MEDALLION) / 2).coerceAtLeast(0.dp)
        // Centre stage: a beam from above and a lit floor, fixed at the centre, so whichever
        // sound is swiped into the middle is the one in the light. Drawn past the box's top
        // edge on purpose (the canvas is not clipped) so the beam falls from the sky.
        Canvas(Modifier.matchParentSize()) {
            val c = Offset(size.width / 2f, size.height / 2f)
            val r = MEDALLION.toPx() / 2f * 0.74f
            val top = -size.height * 1.1f
            val floorY = c.y + r * 1.05f
            val floor = androidx.compose.ui.geometry.Rect(
                c.x - r * 1.3f, floorY - r * 0.22f, c.x + r * 1.3f, floorY + r * 0.22f,
            )
            // The beam takes in the whole floor oval, so the pool of light is one shade; ending
            // it at the oval's middle lit only the back half.
            val beam = androidx.compose.ui.graphics.Path().apply {
                moveTo(c.x - r * 0.22f, top)
                lineTo(c.x + r * 0.22f, top)
                lineTo(c.x + r * 1.25f, floorY)
                lineTo(c.x - r * 1.25f, floorY)
                close()
            }.let { trapezoid ->
                androidx.compose.ui.graphics.Path().apply {
                    op(trapezoid, androidx.compose.ui.graphics.Path().apply { addOval(floor) }, androidx.compose.ui.graphics.PathOperation.Union)
                }
            }
            drawPath(
                beam,
                Brush.verticalGradient(
                    listOf(Color.Transparent, AppColors.gold.copy(alpha = 0.07f + 0.05f * glow)),
                    startY = top, endY = c.y + r,
                ),
            )
            drawOval(
                Brush.radialGradient(
                    listOf(AppColors.gold.copy(alpha = 0.22f + 0.10f * glow), Color.Transparent),
                    Offset(c.x, floorY), r * 1.3f,
                ),
                topLeft = Offset(c.x - r * 1.3f, floorY - r * 0.22f),
                size = androidx.compose.ui.geometry.Size(r * 2.6f, r * 0.44f),
            )
        }
        HorizontalPager(
            state = pager,
            pageSize = PageSize.Fixed(MEDALLION),
            contentPadding = PaddingValues(horizontal = side),
            pageSpacing = 4.dp,
            modifier = Modifier.fillMaxWidth().height(MEDALLION + 24.dp),
        ) { page ->
            val sound = state.sounds[page]
            // 0 in the spotlight, 1 one page away: neighbours shrink and dim, so the eye
            // reads one sound at a time and still sees there are more either side.
            val away = abs((pager.currentPage - page) + pager.currentPageOffsetFraction).coerceIn(0f, 1f)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val s = 1f - 0.30f * away
                        scaleX = s; scaleY = s
                        alpha = 1f - 0.55f * away
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) {
                        if (page == pager.currentPage) onTapFocused(sound.key)
                        else scope.launch { pager.animateScrollToPage(page) }
                    }
                    .semantics {
                        role = Role.Button
                        contentDescription = buildString {
                            append(sound.name)
                            if (sound.key == state.selectedKey) append(", in use")
                            if (page == pager.currentPage) append(", tap to hear it")
                        }
                    },
            ) {
                SoundMedallion(
                    glyph = sound.glyph,
                    premium = sound.premium,
                    owned = sound.owned,
                    inUse = sound.key == state.selectedKey,
                    sounding = sound.key == state.previewingKey,
                    glow = glow,
                    ripple = ripple,
                    size = MEDALLION,
                )
            }
        }
    }
}

/**
 * One sound as a medallion: a lifted disc with its mark, a gold star if it is premium, a
 * gold ring while it is the sound in use (pulsing with the click), and rings going out while
 * it is being heard. Public so another stage, or a list, can show a sound the same way.
 */
@Composable
fun SoundMedallion(
    glyph: AppGlyph,
    premium: Boolean,
    owned: Boolean,
    inUse: Boolean,
    sounding: Boolean,
    glow: Float,
    ripple: Float,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier.size(size)) {
        val dp = 1.dp.toPx()
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val r = this.size.minDimension / 2f * 0.74f

        // A spotlight from above, only on the sound in use, so it reads as "on stage".
        if (inUse) {
            drawCircle(
                Brush.radialGradient(
                    listOf(AppColors.gold.copy(alpha = 0.16f + 0.14f * glow), Color.Transparent),
                    c, r * 1.55f,
                ),
                r * 1.55f, c,
            )
        }
        if (sounding) {
            for (k in 0..1) {
                val p = (ripple + k * 0.5f) % 1f
                drawCircle(
                    AppColors.gold.copy(alpha = 0.45f * (1f - p)),
                    r * (1.04f + 0.30f * p), c,
                    style = Stroke(width = 1.5f * dp),
                )
            }
        }

        // The disc: lit from the upper left, like every raised face in the app.
        drawCircle(
            Brush.radialGradient(
                listOf(AppColors.surfaceActive, AppColors.card),
                Offset(c.x - r * 0.35f, c.y - r * 0.45f), r * 1.5f,
            ),
            r, c,
        )
        val (rim, rimWidth) = when {
            inUse -> AppColors.gold to 2f
            owned -> AppColors.goldBorderStrong to 1.5f
            else  -> AppColors.goldBorder to 1f
        }
        drawCircle(rim, r, c, style = Stroke(width = rimWidth * dp))
        if (inUse && glow > 0f) {
            drawCircle(
                AppColors.gold.copy(alpha = 0.55f * glow),
                r * (1.03f + 0.16f * (1f - glow)), c,
                style = Stroke(width = 1.5f * dp),
            )
        }

        val side = r * 1.02f
        drawGlyph(
            glyph = glyph,
            topLeft = Offset(c.x - side / 2f, c.y - side / 2f),
            side = side,
            tint = if (owned) AppColors.textPrimary else AppColors.goldSoft,
            // The family's heaviest line (RhythmPulseIcon's): at this size 1.5 dp is spindly.
            linePx = 2f * dp,
        )

        if (premium) {
            val sr = 10f * dp
            drawCircle(AppColors.card, sr * 1.25f, Offset(c.x + r * 0.72f, c.y - r * 0.72f))
            drawStar(Offset(c.x + r * 0.72f, c.y - r * 0.72f), sr, earned = true)
        }
    }
}

@Composable
private fun PageDots(sounds: List<StageSound>, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        sounds.forEachIndexed { i, s ->
            val on = i == current
            Canvas(Modifier.size(if (on) 8.dp else 6.dp)) {
                val base = if (s.premium) AppColors.gold else AppColors.textPrimary
                drawCircle(base.copy(alpha = if (on) 1f else 0.30f))
            }
        }
    }
}

// ── The focused sound's card ──────────────────────────────────────────────────

@Composable
private fun SoundDetails(
    sound: StageSound,
    state: SoundStageState,
    onPreview: (String) -> Unit,
    onStopPreview: () -> Unit,
    onChoose: (String) -> Unit,
    onBuy: (String) -> Unit,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val inUse = sound.key == state.selectedKey
    val sounding = sound.key == state.previewingKey
    AppCard(modifier = modifier.fillMaxWidth()) {
        PinnedSlot(lineHeight = 30.sp, alignment = Alignment.Center) {
            Text(
                sound.name, color = AppColors.textPrimary, fontSize = 24.sp, lineHeight = 30.sp,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = PinnedLines,
            )
        }
        PinnedSlot(lineHeight = 16.sp, alignment = Alignment.Center) {
            val (status, color) = when {
                inUse         -> "IN USE" to AppColors.gold
                !sound.premium -> "FREE" to AppColors.textDim
                sound.owned   -> "YOURS" to AppColors.goldSoft
                else          -> "PREMIUM" to AppColors.goldSoft
            }
            Text(
                status, color = color, fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp, style = PinnedLines,
            )
        }
        Spacer(Modifier.height(6.dp))
        PinnedSlot(lineHeight = 18.sp, lines = 2, alignment = Alignment.Center) {
            Text(
                sound.tagline, color = AppColors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center, minLines = 2, maxLines = 2, overflow = TextOverflow.Ellipsis,
                style = PinnedLines,
            )
        }
        Spacer(Modifier.height(6.dp))
        // Only for a group that has instrument fits at all (the clicks; not the drone), so a
        // group without them has no empty row, and a group with them never jumps.
        if (state.sounds.any { it.instruments.isNotEmpty() }) {
            Box(Modifier.fillMaxWidth().height(30.dp), contentAlignment = Alignment.Center) {
                InstrumentAffinityBadges(instruments = sound.instruments, iconSize = 24.dp)
            }
        }
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            PlayStopKey(
                playing = sounding,
                enabled = !state.previewBlocked || sounding,
                onClick = { if (sounding) onStopPreview() else onPreview(sound.key) },
                playDescription = "Hear ${sound.name}",
                stopDescription = "Stop ${sound.name}",
            )
            Spacer(Modifier.width(12.dp))
            val (label, enabled, action) = when {
                inUse -> Triple("In use", false, {})
                sound.owned -> Triple("Use ${sound.name}", true, { onChoose(sound.key) })
                state.purchasing -> Triple("Opening the store", false, {})
                state.storeConnecting -> Triple("Connecting to the store", false, {})
                !sound.available -> Triple("Not available right now", false, {})
                else -> Triple(
                    if (sound.price != null) "Unlock for ${sound.price}" else "Unlock ${sound.name}",
                    true, { onBuy(sound.key) },
                )
            }
            PrimaryButton(
                label = label,
                onClick = action,
                enabled = enabled,
                icon = if (inUse) AppIcons.Check else null,
                modifier = Modifier.weight(1f),
            )
        }

        // One fixed line under the keys: why the key is dim, or the restore link, or nothing.
        PinnedSlot(lineHeight = 30.sp, alignment = Alignment.Center) {
            when {
                state.previewBlocked && !sounding -> Text(
                    "Hear it after your session", color = AppColors.textMuted,
                    fontSize = 12.sp, lineHeight = 16.sp, style = PinnedLines,
                )
                sounding -> Text(
                    if (state.previewReplacesLive) "Sounding ${sound.name} on its own" else "Sounding ${sound.name}",
                    color = AppColors.gold,
                    fontSize = 12.sp, lineHeight = 16.sp, style = PinnedLines,
                )
                sound.premium && !sound.owned -> Text(
                    "Already bought it? Restore", color = AppColors.textMuted,
                    fontSize = 12.sp, lineHeight = 16.sp, style = PinnedLines,
                    modifier = Modifier
                        .clickable(enabled = !state.purchasing, onClick = onRestore)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                else -> Unit
            }
        }
        // Present or absent for the whole visit (it depends on the device, not the sound), so
        // leaving it out when there is nothing to say moves nothing.
        state.footnote?.let { note ->
            PinnedSlot(lineHeight = 15.sp, lines = 2, alignment = Alignment.Center) {
                Text(
                    note, color = AppColors.textDim, fontSize = 11.sp, lineHeight = 15.sp,
                    textAlign = TextAlign.Center, minLines = 2, maxLines = 2, style = PinnedLines,
                )
            }
        }
    }
}
