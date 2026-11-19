package com.soluplay.tv

import android.view.KeyEvent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.app.player.PlayerSurface
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.allChannelsInOrder
import com.soluplay.core.favourites.FavouritesViewModel
import com.soluplay.core.model.Channel
import com.soluplay.core.player.PlaybackPhase
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.EmeraldText
import com.soluplay.core.ui.KeepScreenOn
import com.soluplay.core.ui.LiveDot
import com.soluplay.core.ui.PanelDeep
import com.soluplay.core.ui.PlayerLoadingFrame
import com.soluplay.core.ui.Slate200
import com.soluplay.core.ui.Slate400
import com.soluplay.core.ui.Slate700
import com.soluplay.core.ui.StarColour

/**
 * The television: one picture, the whole screen, and nothing else.
 *
 * The first channel of the list plays the moment the list is there. The
 * remote is the whole interface: up and down change the channel one by one,
 * held down they run through the list fast and the picture follows when the
 * key is let go; the middle button opens the channel list over the picture —
 * never beside it, so the picture is never compressed — where up and down
 * move, the middle plays, right marks a favourite and back puts the list
 * away. The volume keys belong to the television itself.
 *
 * While a mirror is being tried the edge of the screen carries the travelling
 * line; a channel that no mirror can play leaves the static of a television
 * with nothing on it.
 */
@Composable
fun TvScreen(
    viewModel: BrowseViewModel,
    playback: PlaybackViewModel,
    host: Media3PlaybackHost,
    favourites: FavouritesViewModel,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val saved by favourites.snapshot.collectAsStateWithLifecycle()
    val state by playback.state.collectAsStateWithLifecycle()

    // The viewer's own channels stand first, the rest in the server's order.
    val marked = remember(saved.items) { saved.items.map { it.channelId }.toSet() }
    val channels = remember(snapshot.channels, marked) {
        val base = allChannelsInOrder(snapshot.channels)
        base.filter { it.id in marked } + base.filter { it.id !in marked }
    }

    var current by remember { mutableStateOf<Channel?>(null) }
    var sidebar by remember { mutableStateOf(false) }
    var highlight by remember { mutableStateOf(0) }
    var zap by remember { mutableStateOf<Int?>(null) }

    val focus = remember { FocusRequester() }
    val listState = rememberLazyListState()

    fun play(channel: Channel) {
        current = channel
        zap = null
        playback.open(channel.id, channel.name, channel.logo)
    }

    // The first channel, the moment there is a list to take it from.
    LaunchedEffect(channels.isEmpty()) {
        if (current == null && channels.isNotEmpty()) play(channels.first())
    }

    DisposableEffect(Unit) {
        onDispose { playback.close() }
    }

    LaunchedEffect(Unit) { focus.requestFocus() }

    val index = channels.indexOfFirst { it.id == current?.id }.coerceAtLeast(0)

    fun wrap(place: Int): Int = ((place % channels.size) + channels.size) % channels.size

    /** One step of the channel keys: played at once, or held for the let-go. */
    fun step(delta: Int, commit: Boolean) {
        if (channels.isEmpty()) return
        val next = wrap((zap ?: index) + delta)
        if (commit) play(channels[next]) else zap = next
    }

    fun commitZap() {
        val target = zap ?: return
        if (channels.isNotEmpty()) play(channels[wrap(target)])
    }

    fun onKey(event: KeyEvent): Boolean {
        val down = event.action == KeyEvent.ACTION_DOWN
        val up = event.action == KeyEvent.ACTION_UP
        val fresh = down && event.repeatCount == 0

        if (sidebar) {
            return when (event.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                    if (down && channels.isNotEmpty()) highlight = wrap(highlight - 1)
                    true
                }
                KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                    if (down && channels.isNotEmpty()) highlight = wrap(highlight + 1)
                    true
                }
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    if (fresh) {
                        channels.getOrNull(highlight)?.let { play(it) }
                        sidebar = false
                    }
                    true
                }
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (fresh) channels.getOrNull(highlight)?.let { favourites.toggle(it) }
                    true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_BACK -> {
                    if (down) sidebar = false
                    true
                }
                else -> false
            }
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_CHANNEL_UP -> {
                if (down) step(-1, event.repeatCount == 0) else if (up) commitZap()
                true
            }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_CHANNEL_DOWN -> {
                if (down) step(1, event.repeatCount == 0) else if (up) commitZap()
                true
            }
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
            KeyEvent.KEYCODE_MENU, KeyEvent.KEYCODE_INFO, KeyEvent.KEYCODE_GUIDE,
            -> {
                if (fresh) {
                    highlight = index
                    sidebar = true
                }
                true
            }
            else -> false
        }
    }

    // The lit row stays in the middle of the list while the remote moves.
    LaunchedEffect(highlight, sidebar) {
        if (!sidebar || channels.isEmpty()) return@LaunchedEffect
        listState.scrollToItem(highlight)
        withFrameNanos { }
        val row = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == highlight }?.size ?: 0
        val viewport = listState.layoutInfo.viewportSize.height
        val offset = if (row > 0 && viewport > row) -(viewport / 2 - row / 2) else 0
        listState.scrollToItem(highlight, offset)
    }

    val dead = state.phase == PlaybackPhase.EXHAUSTED || state.phase == PlaybackPhase.FAILED
    val tuning = current != null && !state.onAir && !dead && !state.paused
    val running = state.phase != PlaybackPhase.IDLE && !dead
    KeepScreenOn(enabled = running)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .focusRequester(focus)
            .onPreviewKeyEvent { onKey(it.nativeKeyEvent) },
    ) {
        PlayerSurface(host = host, modifier = Modifier.fillMaxSize())

        if (current == null || tuning) {
            PlayerLoadingFrame(modifier = Modifier.fillMaxSize())
        }

        if (dead) {
            TvStatic(modifier = Modifier.fillMaxSize())
        }

        // The channel a held key is running past, said out loud over the
        // picture until the key is let go and the picture follows.
        val zapped = zap?.let { channels.getOrNull(it) }
        if (zapped != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(PanelDeep.copy(alpha = 0.9f))
                        .border(1.dp, Slate700.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .padding(horizontal = 32.dp, vertical = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ChannelLogo(
                        channel = zapped,
                        size = 72.dp,
                        border = BorderStroke(1.dp, LiveDot),
                        shape = CircleShape,
                    )
                    Text(
                        text = zapped.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // The list stands over the picture, never beside it: the picture
        // keeps the whole screen while the list is open.
        if (sidebar) {
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(340.dp)
                    .background(PanelDeep.copy(alpha = 0.94f)),
            ) {
                Text(
                    text = Copy.channelList(),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                )
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    itemsIndexed(channels, key = { _, channel -> channel.id }) { place, channel ->
                        val lit = place == highlight
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(if (lit) Color(0xFF0D1F33) else Color.Transparent)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            ChannelLogo(
                                channel = channel,
                                size = 44.dp,
                                border = BorderStroke(
                                    1.dp,
                                    if (channel.id in marked) LiveDot else Slate700,
                                ),
                                shape = CircleShape,
                            )
                            Text(
                                text = channel.name,
                                color = if (lit) EmeraldText else Slate200,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f),
                            )
                            if (channel.id in marked) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = StarColour,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }
                    }
                }
                Text(
                    text = Copy.tvSidebarHint(),
                    color = Slate400,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }
}