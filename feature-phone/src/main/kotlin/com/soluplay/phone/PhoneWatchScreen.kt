package com.soluplay.phone

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.FavouriteStar
import com.soluplay.core.ui.MirrorBadge
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
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

    LaunchedEffect(channel.id) {
        held = false
        playback.open(channel.id, channel.name, channel.logo)
    }
    DisposableEffect(channel.id) {
        onDispose { playback.close() }
    }
    BackHandler { onClose() }

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

        if (chrome) {
            Text(
                text = Copy.back(),
                color = Accent,
                fontSize = 13.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Panel)
                    .clickable { onClose() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )

            FavouriteStar(
                marked = marked,
                onToggle = onToggleFavourite,
                size = 26.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            )

            if (held) {
                Text(
                    text = Copy.heldFeed(),
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 18.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Panel)
                        .clickable {
                            held = false
                            playback.setPaused(false)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f, fill = false),
                ) {
                    Text(
                        text = state.channelName.ifBlank { channel.name },
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (state.category.isNotBlank()) {
                        Text(
                            text = state.category,
                            color = Muted,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Text(
                    text = playbackStatus(state),
                    color = if (state.onAir) Accent else Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                )
                MirrorBadge(state = state, fontSize = 12.sp)
                Text(
                    text = Copy.nextMirror(),
                    color = Accent,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Panel)
                        .clickable { playback.skipMirror() }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
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
