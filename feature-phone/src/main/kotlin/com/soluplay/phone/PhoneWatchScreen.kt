package com.soluplay.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.app.player.PlayerSurface
import com.soluplay.core.gestures.GestureIntent
import com.soluplay.core.model.Channel
import com.soluplay.core.player.PlaybackPhase
import com.soluplay.core.player.PlaybackState
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.FavouriteStar
import com.soluplay.core.ui.HeaderBackground
import com.soluplay.core.ui.LiveBadge
import com.soluplay.core.ui.LiveDot
import com.soluplay.core.ui.LiveText
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.MirrorBadge
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.Slate700
import com.soluplay.core.ui.Slate800
import com.soluplay.core.ui.WeakSignalPanel
import com.soluplay.core.ui.playbackStatus
import com.soluplay.core.ui.watchGestures

/**
 * The watch screen.
 *
 * The picture is the screen: the words sit over it and never take it away,
 * and a tap only brings the words in and out — it never pauses the feed.
 *
 * The finger does the rest: a swipe up or down changes the channel, a swipe
 * sideways asks for another mirror of this one, and two fingers hold the
 * feed where it is.
 *
 * The one thing that is always on screen is the truth about the feed: what
 * the app is doing, and why the last mirror was given up.
 */
@Composable
fun PhoneWatchScreen(
    channel: Channel,
    channels: List<Channel>,
    host: Media3PlaybackHost,
    playback: PlaybackViewModel,
    marked: Boolean,
    onToggleFavourite: () -> Unit,
    onOpenChannel: (Channel) -> Unit,
    onClose: () -> Unit,
) {
    val state by playback.state.collectAsStateWithLifecycle()
    var chrome by remember { mutableStateOf(true) }
    var held by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    LaunchedEffect(channel.id) {
        held = false
        playback.open(channel.id, channel.name, channel.logo)
    }
    DisposableEffect(channel.id) {
        onDispose { playback.close() }
    }
    BackHandler {
        if (picking) picking = false else onClose()
    }

    // The channels in the order the site published them, so a swipe changes
    // the channel the way the guide lists it.
    val index = channels.indexOfFirst { it.id == channel.id }
    val next = channels.getOrNull(index + 1)
    val previous = channels.getOrNull(index - 1)

    fun onIntent(intent: GestureIntent) {
        when (intent) {
            // A tap brings the words in and out and never pauses the feed.
            GestureIntent.REVEAL_CONTROLS -> chrome = !chrome

            // Two taps give the picture the whole screen.
            GestureIntent.FULLSCREEN -> chrome = false

            // Two fingers hold the feed where it is.
            GestureIntent.PAUSE -> {
                held = true
                playback.setPaused(true)
            }

            // A sideways swipe stays on this channel and asks for another
            // mirror of it, in the order the site published them.
            GestureIntent.NEXT_MIRROR -> playback.skipMirror()

            GestureIntent.NEXT_CHANNEL -> next?.let { onOpenChannel(it) }
            GestureIntent.PREVIOUS_CHANNEL -> previous?.let { onOpenChannel(it) }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerSurface(host = host, modifier = Modifier.fillMaxSize())

        // The gesture layer sits over the picture so a finger reaches the app
        // and not the decoder.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .watchGestures { intent -> onIntent(intent) },
        )

        if (chrome && !picking) {
            WatchTopBar(
                marked = marked,
                onToggleFavourite = onToggleFavourite,
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter),
            )

            if (held) {
                Text(
                    text = Copy.heldFeed(),
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 62.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Panel)
                        .clickable {
                            held = false
                            playback.setPaused(false)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            WatchBand(
                channel = channel,
                state = state,
                onSkipMirror = { playback.skipMirror() },
                onPickChannel = { picking = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        if (picking) {
            PhoneChannelSheet(
                channels = channels,
                current = channel,
                onPick = {
                    picking = false
                    onOpenChannel(it)
                },
                onDismiss = { picking = false },
            )
        }

        val dead = state.phase == PlaybackPhase.EXHAUSTED || state.phase == PlaybackPhase.FAILED
        if (dead) {
            WeakSignalPanel(
                state = state,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/**
 * The bar at the top of the picture.
 *
 * It is the site's own bar: the same dark band, the same hairline under it,
 * and the way back on the left where the site keeps its own.
 */
@Composable
private fun WatchTopBar(
    marked: Boolean,
    onToggleFavourite: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(HeaderBackground.copy(alpha = 0.92f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Panel)
                    .clickable { onClose() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = Copy.back(),
                    tint = Accent,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            FavouriteStar(
                marked = marked,
                onToggle = onToggleFavourite,
                size = 24.dp,
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Slate800.copy(alpha = 0.8f)),
        )
    }
}

/**
 * The band under the picture: the channel, the truth about the feed and the
 * two things a viewer can ask for.
 *
 * It is the site's channel band — the logo on a white plate, the name, the
 * category's own pill and the badge that says the picture is on — with the
 * app's own line about the feed kept under it.
 */
@Composable
private fun WatchBand(
    channel: Channel,
    state: PlaybackState,
    onSkipMirror: () -> Unit,
    onPickChannel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val category = state.category.ifBlank { channel.category }
    val name = state.channelName.ifBlank { channel.name }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Panel)
                .border(1.dp, Slate800.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelLogo(
                channel = channel,
                size = 56.dp,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Slate700),
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = name,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    if (category.isNotBlank()) {
                        CategoryPill(category)
                    }

                    if (state.onAir) {
                        LivePill()
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = playbackStatus(state),
                        color = if (state.onAir) Accent else Muted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )

                    MirrorBadge(state = state, fontSize = 11.sp)
                }
            }
        }

        // The two things a viewer can ask for while the picture is on.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BandButton(text = Copy.nextMirror(), onClick = onSkipMirror)
            BandButton(text = Copy.channels(), onClick = onPickChannel)
        }
    }
}

/** The category's own pill, the way the site writes it under the channel. */
@Composable
private fun CategoryPill(category: String) {
    Text(
        text = category,
        color = LiveDot,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(LiveDot.copy(alpha = 0.15f))
            .border(1.dp, LiveDot.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

/** The badge that says the picture is on. */
@Composable
private fun LivePill() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(LiveBadge.copy(alpha = 0.15f))
            .border(1.dp, LiveBadge.copy(alpha = 0.3f), CircleShape)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(5.dp)
                .clip(CircleShape)
                .background(LiveText),
        )
        Text(
            text = Copy.live(),
            color = LiveText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/** One of the buttons under the picture. */
@Composable
private fun BandButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = text,
        color = Accent,
        fontSize = 12.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(Panel)
            .border(1.dp, Slate800, shape)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
