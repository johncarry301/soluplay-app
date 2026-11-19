package com.soluplay.phone

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.app.player.PlayerSurface
import com.soluplay.core.browse.CATEGORY_FILTERS
import com.soluplay.core.browse.CategoryFilter
import com.soluplay.core.browse.channelsInFilter
import com.soluplay.core.browse.sidebarChannelsInOrder
import com.soluplay.core.gestures.GestureIntent
import com.soluplay.core.model.Channel
import com.soluplay.core.player.PlaybackPhase
import com.soluplay.core.player.PlaybackState
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.CategoryPillRow
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.EmeraldText
import com.soluplay.core.ui.HeaderBackground
import com.soluplay.core.ui.KeepScreenOn
import com.soluplay.core.ui.LiveDot
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.PanelDeep
import com.soluplay.core.ui.PanelMid
import com.soluplay.core.ui.PlayerLoadingFrame
import com.soluplay.core.ui.Slate200
import com.soluplay.core.ui.Slate400
import com.soluplay.core.ui.Slate700
import com.soluplay.core.ui.Slate800
import com.soluplay.core.ui.Slate950
import com.soluplay.core.ui.StarColour
import com.soluplay.core.ui.playbackDetail
import com.soluplay.core.ui.playbackFailure
import com.soluplay.core.ui.watchGestures
import kotlinx.coroutines.delay

/**
 * The watch screen, as the site's own watch page on a phone.
 *
 * The way back, the picture, and the channel list under it — one column, the
 * picture in its own frame, and every control the player has sits on the
 * picture itself: the status pill at the top, the play, sound, quality and
 * full-screen controls at the bottom, gone a moment after the last touch.
 *
 * The finger still does what it always did: a tap brings the controls in and
 * out and never pauses the feed, two taps give the picture the whole screen,
 * two fingers hold it, a vertical swipe changes the channel and a sideways
 * swipe asks for another mirror.
 */
@Composable
fun PhoneWatchScreen(
    channel: Channel,
    channels: List<Channel>,
    host: Media3PlaybackHost,
    playback: PlaybackViewModel,
    onOpenChannel: (Channel) -> Unit,
    onClose: () -> Unit,
    marked: Boolean,
    onToggleFavourite: () -> Unit,
    directUrl: String? = null,
    initialFilter: CategoryFilter? = null,
    loading: Boolean = false,
) {
    val state by playback.state.collectAsStateWithLifecycle()
    val audio by host.audio.collectAsStateWithLifecycle()
    val quality by host.quality.collectAsStateWithLifecycle()

    var controls by rememberSaveable { mutableStateOf(true) }
    var menuOpen by remember { mutableStateOf(false) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }

    // The channel a swipe just arrived on, shown for a moment.
    var switchPreview by remember { mutableStateOf<Channel?>(null) }

    // The list under the picture steps back while the picture is watched:
    // after a while without a touch it fades to half, and any touch brings
    // it fully back and starts the wait again.
    var listDimmed by rememberSaveable { mutableStateOf(false) }
    var listTouch by remember { mutableStateOf(0) }

    // The pill the viewer came in on stays on while they move between
    // channels, the way the site keeps its category across switches.
    var filterSlug by rememberSaveable(initialFilter?.slug) {
        mutableStateOf(initialFilter?.slug)
    }
    val filter = CATEGORY_FILTERS.firstOrNull { it.slug == filterSlug }

    val held = state.paused
    val dead = state.phase == PlaybackPhase.EXHAUSTED || state.phase == PlaybackPhase.FAILED
    val tuning = !state.onAir && !dead && !held

    PhoneFullscreen(enabled = fullscreen)

    val running = state.phase != PlaybackPhase.IDLE && !dead && !held
    KeepScreenOn(enabled = running)

    LaunchedEffect(channel.id, directUrl) {
        controls = true
        menuOpen = false
        if (directUrl != null) {
            playback.openDirect(channel.id, directUrl, channel.name, channel.logo)
        } else {
            playback.open(channel.id, channel.name, channel.logo)
        }
    }

    DisposableEffect(channel.id, directUrl) {
        onDispose { playback.close() }
    }

    LaunchedEffect(switchPreview) {
        if (switchPreview != null) {
            delay(SWITCH_PREVIEW_MS)
            switchPreview = null
        }
    }

    // The moment the picture is on air the controls put themselves away at
    // once; a tap brings them back and the usual wait applies again.
    LaunchedEffect(state.onAir) {
        if (state.onAir) controls = false
    }

    LaunchedEffect(state.onAir, channel.id, listTouch) {
        if (state.onAir) {
            delay(LIST_DIM_MS)
            listDimmed = true
        } else {
            listDimmed = false
        }
    }

    // The controls put themselves away a moment after the last touch, the
    // way the web player's own bar does — but a menu that is open stays.
    LaunchedEffect(controls, menuOpen) {
        if (controls && !menuOpen) {
            delay(CONTROLS_HIDE_MS)
            controls = false
        }
    }

    BackHandler {
        when {
            menuOpen -> menuOpen = false
            fullscreen -> fullscreen = false
            else -> onClose()
        }
    }

    val index = channels.indexOfFirst { it.id == channel.id }
    val next = channels.getOrNull(index + 1)
    val previous = channels.getOrNull(index - 1)

    fun onIntent(intent: GestureIntent) {
        when (intent) {
            GestureIntent.REVEAL_CONTROLS ->
                if (menuOpen) menuOpen = false else controls = !controls

            GestureIntent.FULLSCREEN -> {
                fullscreen = !fullscreen
                controls = true
            }

            GestureIntent.PAUSE -> playback.setPaused(true)
            GestureIntent.NEXT_MIRROR -> playback.skipMirror()
            GestureIntent.NEXT_CHANNEL -> next?.let {
                switchPreview = it
                onOpenChannel(it)
            }
            GestureIntent.PREVIOUS_CHANNEL -> previous?.let {
                switchPreview = it
                onOpenChannel(it)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else Background)
            .then(
                if (fullscreen) Modifier
                else Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ),
        verticalArrangement = Arrangement.spacedBy(if (fullscreen) 0.dp else 12.dp),
    ) {
        if (!fullscreen) {
            val mirrorLabel = if (state.mirrorCount > 0 && state.phase != PlaybackPhase.IDLE) {
                Copy.mirrorInfo(state.mirror, state.mirrorCount)
            } else {
                null
            }
            WatchBackRow(onClose = onClose, mirrorLabel = mirrorLabel)
        }

        // The picture, with everything the player says and everything the
        // viewer can ask for sitting on it.
        Box(
            modifier = (
                if (fullscreen) {
                    Modifier.fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                }
                ).background(Color.Black),
        ) {
            PlayerSurface(host = host, modifier = Modifier.fillMaxSize())

            // The gesture layer sits over the picture so a finger reaches the
            // app and not the decoder; the controls sit over the layer, so a
            // tap on a control is the control's and not a gesture.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .watchGestures { intent -> onIntent(intent) },
            )

            if (dead) {
                WeakOverlay(
                    state = state,
                    onSwitchMirror = {
                        if (state.phase == PlaybackPhase.FAILED) {
                            if (directUrl != null) {
                                playback.openDirect(channel.id, directUrl, channel.name, channel.logo)
                            } else {
                                playback.open(channel.id, channel.name, channel.logo)
                            }
                        } else {
                            playback.skipMirror()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }

            if (controls || tuning) {
                StatusPill(
                    name = state.channelName.ifBlank { channel.name },
                    channel = channel,
                    state = state,
                    height = quality.currentHeight,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp),
                )
            }

            // The viewer's own mark on this channel, kept on the device. A
            // match link has no catalogue row behind it, so it carries none.
            if (controls && directUrl == null) {
                val starLabel = if (marked) Copy.savedChannel() else Copy.saveChannel()
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onToggleFavourite() }
                        .semantics { contentDescription = starLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (marked) StarColour else Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            if (controls) {
                PlayerControls(
                    held = held,
                    volume = audio.volume,
                    muted = audio.muted,
                    heights = quality.heights,
                    currentHeight = quality.currentHeight,
                    override = quality.override,
                    fullscreen = fullscreen,
                    onTogglePause = { playback.setPaused(!held) },
                    onVolume = host::setVolume,
                    onToggleMute = {
                        if (audio.muted || audio.volume <= 0f) {
                            host.setMuted(false)
                            if (audio.volume <= 0f) host.setVolume(1f)
                        } else {
                            host.setMuted(true)
                        }
                    },
                    onToggleMenu = { menuOpen = !menuOpen },
                    onToggleFullscreen = {
                        fullscreen = !fullscreen
                        controls = true
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }

            // The menu sits on the picture itself, above the bar, so every
            // row of it is inside the frame a finger can reach.
            if (controls && menuOpen && quality.heights.isNotEmpty()) {
                QualityMenu(
                    heights = quality.heights,
                    override = quality.override,
                    onSelect = {
                        host.selectQuality(it)
                        menuOpen = false
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 56.dp),
                )
            }

            // A swipe between channels says what is around the picture: the
            // channel arrived on in the middle, the one before it above and
            // the one after it below, both quiet, so a move in the whole
            // screen is never a move into the unknown.
            switchPreview?.let { preview ->
                val place = channels.indexOfFirst { it.id == preview.id }
                val before = channels.getOrNull(place - 1)
                val after = channels.getOrNull(place + 1)
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(PanelDeep.copy(alpha = 0.9f))
                            .border(1.dp, Slate700.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (before != null) {
                            ChannelLogo(
                                channel = before,
                                size = 40.dp,
                                border = BorderStroke(1.dp, Slate700),
                                shape = CircleShape,
                                modifier = Modifier.alpha(0.35f),
                            )
                        }
                        ChannelLogo(
                            channel = preview,
                            size = 56.dp,
                            border = BorderStroke(1.dp, LiveDot),
                            shape = CircleShape,
                        )
                        Text(
                            text = preview.name,
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (after != null) {
                            ChannelLogo(
                                channel = after,
                                size = 40.dp,
                                border = BorderStroke(1.dp, Slate700),
                                shape = CircleShape,
                                modifier = Modifier.alpha(0.35f),
                            )
                        }
                    }
                }
            }

            // The tuning line is drawn last of all, over everything the
            // player carries, so nothing can hide its edge while it moves.
            if (tuning) {
                PlayerLoadingFrame(modifier = Modifier.fillMaxSize())
            }
        }

        if (!fullscreen) {
            val listAlpha by animateFloatAsState(
                targetValue = if (listDimmed) 0.5f else 1f,
                label = "listAlpha",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            listDimmed = false
                            listTouch++
                        }
                    },
            ) {
                ChannelListCard(
                    channels = channels,
                    current = channel,
                    filter = filter,
                    loading = loading,
                    tuning = tuning,
                    onPickFilter = { filterSlug = it?.slug },
                    onOpenChannel = onOpenChannel,
                    modifier = Modifier
                        .fillMaxSize()
                        .alpha(listAlpha),
                )
            }
        }
    }
}

/**
 * The way back: the site's own small box and the word beside it, and on the
 * right of the same line, in the thinnest letters, which mirror is carrying
 * the picture.
 */
@Composable
private fun WatchBackRow(onClose: () -> Unit, mirrorLabel: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.clickable { onClose() },
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(PanelMid)
                    .border(1.dp, Slate800, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Copy.home(),
                    tint = Slate400,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(
                text = Copy.home(),
                color = Slate400,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        if (mirrorLabel != null) {
            Text(
                text = mirrorLabel,
                color = Slate400,
                fontSize = 10.sp,
                fontWeight = FontWeight.Thin,
                maxLines = 1,
            )
        }
    }
}

/** What the picture is doing, in the pill the site keeps over its player. */
@Composable
private fun StatusPill(
    name: String,
    channel: Channel,
    state: PlaybackState,
    height: Int,
    modifier: Modifier = Modifier,
) {
    val dead = state.phase == PlaybackPhase.EXHAUSTED || state.phase == PlaybackPhase.FAILED
    val label: String
    val tint: Color
    when {
        dead -> {
            label = "NO SIGNAL"
            tint = Color(0xFFF87171)
        }
        !state.onAir && !state.paused -> {
            label = "TUNING"
            tint = Color(0xFF67E8F9)
        }
        state.paused -> {
            label = "PAUSED"
            tint = Color(0xFFCBD5E1)
        }
        else -> {
            label = if (height > 0) "LIVE • ${height}p" else "LIVE"
            tint = LiveDot
        }
    }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PanelDeep.copy(alpha = 0.85f))
            .border(1.dp, Slate700.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The channel's own small logo is on the badge for as long as the
        // badge is there — tuning, on air, held or silent.
        ChannelLogo(
            channel = channel,
            size = 18.dp,
            border = BorderStroke(1.dp, tint.copy(alpha = 0.6f)),
            shape = CircleShape,
        )
        Text(
            text = name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            text = label,
            color = tint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(tint.copy(alpha = 0.1f))
                .border(1.dp, tint.copy(alpha = 0.3f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/** No mirror answered: the truth, and the next mirror. */
@Composable
private fun WeakOverlay(
    state: PlaybackState,
    onSwitchMirror: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.9f))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        val weakTitle = Copy.weakSignalTitle()
        val weakTail = Copy.weakSignalTail()
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.Black)) {
                    append(weakTitle)
                }
                append(" ")
                withStyle(SpanStyle(color = Color(0xFFFBBF24))) {
                    append(weakTail)
                }
            },
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        val reason = listOf(playbackFailure(state), playbackDetail(state))
            .filter { it.isNotBlank() }
            .joinToString(" ")
        if (reason.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = reason,
                color = Slate400,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF10B981))
                .clickable { onSwitchMirror() }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RetryGlyph(tint = Slate950, modifier = Modifier.size(14.dp))
            Text(
                text = Copy.switchNextMirror(),
                color = Slate950,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/**
 * The bar on the picture: play, sound, quality and the whole screen.
 *
 * It is the web player's own bar — the same buttons, the same slider, the
 * same quality menu — and it lives on the picture, never under it.
 */
@Composable
private fun PlayerControls(
    held: Boolean,
    volume: Float,
    muted: Boolean,
    heights: List<Int>,
    currentHeight: Int,
    override: Int?,
    fullscreen: Boolean,
    onTogglePause: () -> Unit,
    onVolume: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleMenu: () -> Unit,
    onToggleFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.Black.copy(alpha = 0.6f),
                    1f to Color.Black.copy(alpha = 0.95f),
                )
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ControlButton(
                label = if (held) Copy.playFeed() else Copy.pauseFeed(),
                onClick = onTogglePause,
            ) {
                if (held) {
                    PlayGlyph(tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    PauseGlyph(tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val silent = muted || volume <= 0f
                val soundLabel = if (silent) Copy.unmute() else Copy.mute()
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { onToggleMute() }
                        .semantics { contentDescription = soundLabel },
                    contentAlignment = Alignment.Center,
                ) {
                    if (silent) {
                        MutedGlyph(tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                    } else {
                        VolumeGlyph(tint = Color(0xFFCBD5E1), modifier = Modifier.size(16.dp))
                    }
                }
                VolumeSlider(
                    value = if (muted) 0f else volume,
                    onChange = onVolume,
                    modifier = Modifier.width(64.dp),
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (heights.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.1f))
                        .clickable { onToggleMenu() }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    GaugeGlyph(tint = LiveDot, modifier = Modifier.size(14.dp))
                    Text(
                        text = if (override == null) {
                            "Auto (${if (currentHeight > 0) currentHeight else "HD"}p)"
                        } else {
                            "${override}p"
                        },
                        color = Slate200,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            ControlButton(
                label = if (fullscreen) Copy.leaveFullscreen() else Copy.fullscreen(),
                onClick = onToggleFullscreen,
            ) {
                if (fullscreen) {
                    LeaveFullscreenGlyph(tint = Color.White, modifier = Modifier.size(16.dp))
                } else {
                    FullscreenGlyph(tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** One of the bar's own square buttons. */
@Composable
private fun ControlButton(
    label: String,
    onClick: () -> Unit,
    glyph: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable { onClick() }
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        glyph()
    }
}

/** The sound level, drawn as the web player's own thin slider. */
@Composable
private fun VolumeSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(18.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onChange((offset.x / size.width).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onChange((change.position.x / size.width).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
        ) {
            val radius = CornerRadius(size.height / 2f)
            drawRoundRect(color = Slate700, cornerRadius = radius)
            if (value > 0f) {
                drawRoundRect(
                    color = Accent,
                    size = Size(size.width * value, size.height),
                    cornerRadius = radius,
                )
            }
            drawCircle(
                color = Color.White,
                radius = 5.dp.toPx(),
                center = Offset(size.width * value, size.height / 2f),
            )
        }
    }
}

/** The sizes the stream offers, the way the web player lists them. */
@Composable
private fun QualityMenu(
    heights: List<Int>,
    override: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(144.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF0C1628))
            .border(1.dp, Slate700, RoundedCornerShape(12.dp))
            .padding(4.dp),
    ) {
        QualityRow(
            label = Copy.autoQuality(),
            selected = override == null,
            onClick = { onSelect(null) },
        )
        heights.forEach { height ->
            QualityRow(
                label = "${height}p",
                selected = override == height,
                onClick = { onSelect(height) },
            )
        }
    }
}

@Composable
private fun QualityRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Color(0xFF10B981) else Color.Transparent)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = if (selected) Slate950 else Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            maxLines = 1,
        )
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Slate950,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

/**
 * The list under the picture: the site's own sidebar card.
 *
 * The heading and the count, the pills, and the grid of channels in the
 * order the site's watch page keeps them — pinned first, then by name. The
 * channel being watched is lit, and the list centres on it when the viewer
 * moves to another one.
 */
@Composable
private fun ChannelListCard(
    channels: List<Channel>,
    current: Channel,
    filter: CategoryFilter?,
    loading: Boolean,
    tuning: Boolean,
    onPickFilter: (CategoryFilter?) -> Unit,
    onOpenChannel: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ordered = remember(channels) { sidebarChannelsInOrder(channels) }
    val visible = remember(ordered, filter) { channelsInFilter(ordered, filter) }
    val gridState = rememberLazyGridState()

    // The active row is centred when the channel or the pill changes — a
    // real move, not every recomposition.
    LaunchedEffect(current.id, filter?.slug, visible.size) {
        val position = visible.indexOfFirst { it.id == current.id }
        if (position < 0) return@LaunchedEffect
        withFrameNanos { }
        val row = position / WATCH_COLUMNS
        val rowHeight = gridState.layoutInfo.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
        val viewport = gridState.layoutInfo.viewportSize.height
        val offset = if (rowHeight > 0 && viewport > rowHeight) {
            -(viewport / 2 - rowHeight / 2)
        } else {
            0
        }
        gridState.animateScrollToItem(row, offset)
    }

    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Panel)
            .border(1.dp, Slate800.copy(alpha = 0.8f), shape),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelMid)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (filter == null) {
                    Copy.allChannelsHeading()
                } else {
                    Copy.categoryChannelsHeading(filter.title)
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Copy.liveCount(visible.size),
                color = LiveDot,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(HeaderBackground)
                    .border(1.dp, LiveDot.copy(alpha = 0.3f), CircleShape)
                    .padding(horizontal = 10.dp, vertical = 2.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Slate800.copy(alpha = 0.8f)),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDeep),
        ) {
            CategoryPillRow(
                selected = filter,
                onPick = onPickFilter,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate800.copy(alpha = 0.6f)),
            )
        }

        when {
            loading && channels.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CircularProgressIndicator(
                    color = Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
                Text(text = Copy.loadingChannels(), color = Slate400, fontSize = 12.sp)
            }

            visible.isEmpty() -> Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TvGlyph(tint = Color(0xFF475569), modifier = Modifier.size(32.dp))
                Text(
                    text = if (filter == null) {
                        Copy.noChannelsRightNow()
                    } else {
                        Copy.noChannelsUnder(filter.name)
                    },
                    color = Slate400,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(WATCH_COLUMNS),
                state = gridState,
                contentPadding = PaddingValues(8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                items(visible, key = { it.id }) { item ->
                    WatchChannelTile(
                        channel = item,
                        active = item.id == current.id,
                        tuning = tuning && item.id == current.id,
                        onPick = { onOpenChannel(item) },
                    )
                }
            }
        }
    }
}

/** One channel in the watch list, lit when it is the one on the picture. */
@Composable
private fun WatchChannelTile(
    channel: Channel,
    active: Boolean,
    tuning: Boolean,
    onPick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (active) Color(0xFF0D1F33) else Color.Transparent)
            .border(1.dp, if (active) Color(0xFF10B981) else Color.Transparent, shape)
            .clickable { onPick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (active) {
                Box(
                    modifier = Modifier
                        .border(2.dp, LiveDot.copy(alpha = 0.3f), CircleShape)
                        .padding(2.dp),
                ) {
                    ChannelLogo(
                        channel = channel,
                        size = 56.dp,
                        border = BorderStroke(1.dp, LiveDot),
                        shape = CircleShape,
                    )
                }
            } else {
                ChannelLogo(
                    channel = channel,
                    size = 56.dp,
                    border = BorderStroke(1.dp, Slate700),
                    shape = CircleShape,
                )
            }

            TileDot(
                color = when {
                    active && tuning -> Color(0xFFFBBF24)
                    active -> LiveDot
                    else -> Color(0xFFEF4444)
                },
                pulse = active && tuning,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp),
            )
        }

        Text(
            text = channel.name,
            color = if (active) EmeraldText else Slate200,
            fontSize = 9.sp,
            fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** The corner dot: the carrier's own colour, pulsing only while tuning. */
@Composable
private fun TileDot(
    color: Color,
    pulse: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha = if (pulse) {
        val transition = rememberInfiniteTransition(label = "tiledot")
        val value by transition.animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "pulse",
        )
        value
    } else {
        1f
    }

    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = alpha))
            .border(2.dp, Panel, CircleShape),
    )
}

/** How many channels stand side by side in the watch list. */
private const val WATCH_COLUMNS = 4

/** How long the controls stay after the last touch. */
private const val CONTROLS_HIDE_MS = 3500L

/** How long the picture plays untouched before the list steps back. */
private const val LIST_DIM_MS = 30_000L

/** How long the arriving channel shows its face after a swipe. */
private const val SWITCH_PREVIEW_MS = 1600L