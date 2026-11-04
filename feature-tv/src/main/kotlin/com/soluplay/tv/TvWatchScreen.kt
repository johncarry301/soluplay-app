package com.soluplay.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.app.player.PlayerSurface
import com.soluplay.core.player.PlaybackPhase
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.MirrorBadge
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.WeakSignalPanel
import com.soluplay.core.ui.playbackStatus

/**
 * The watch screen on the television.
 *
 * The picture fills the screen and the words sit over it, in the size the
 * room can read. The remote leaves through its own back key and through the
 * one button on screen, because a viewer holding a remote should never have
 * to guess.
 */
@Composable
fun TvWatchScreen(
    channelId: String,
    channelName: String,
    logoUrl: String,
    host: Media3PlaybackHost,
    playback: PlaybackViewModel,
    onClose: () -> Unit,
) {
    val state by playback.state.collectAsStateWithLifecycle()

    LaunchedEffect(channelId) {
        playback.open(channelId, channelName, logoUrl)
    }
    DisposableEffect(channelId) {
        onDispose { playback.close() }
    }
    BackHandler { onClose() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        PlayerSurface(host = host, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 48.dp, end = 48.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = state.channelName.ifBlank { channelName },
                color = Color.White,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            if (state.category.isNotBlank()) {
                Text(
                    text = state.category,
                    color = Muted,
                    fontSize = 20.sp,
                )
            }
            Text(
                text = playbackStatus(state),
                color = if (state.onAir) Accent else Muted,
                fontSize = 22.sp,
            )
            MirrorBadge(state = state, fontSize = 20.sp)
        }

        TvBackPill(
            onClose = onClose,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 48.dp, bottom = 40.dp),
        )

        val dead = state.phase == PlaybackPhase.EXHAUSTED || state.phase == PlaybackPhase.FAILED
        if (dead) {
            WeakSignalPanel(
                state = state,
                titleSize = 26.sp,
                bodySize = 20.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }
}

/** The one button the remote can press to leave the picture. */
@Composable
private fun TvBackPill(onClose: () -> Unit, modifier: Modifier = Modifier) {
    var focused by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        requester.requestFocus()
    }

    Text(
        text = Copy.back(),
        color = if (focused) Background else Accent,
        fontSize = 20.sp,
        modifier = modifier
            .focusRequester(requester)
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar -> {
                        onClose()
                        true
                    }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Accent else Panel)
            .border(2.dp, Accent, RoundedCornerShape(50))
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}
