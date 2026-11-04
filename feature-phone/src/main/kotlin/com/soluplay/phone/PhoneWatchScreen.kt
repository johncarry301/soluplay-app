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
import com.soluplay.core.player.PlaybackPhase
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.MirrorBadge
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.WeakSignalPanel
import com.soluplay.core.ui.playbackStatus

/**
 * The watch screen.
 *
 * The picture is the screen: the words sit over it and never take it away,
 * and a tap only brings the words in and out — it never pauses the feed.
 *
 * The one thing that is always on screen is the truth about the feed: what
 * the app is doing, and why the last mirror was given up.
 */
@Composable
fun PhoneWatchScreen(
    channelId: String,
    channelName: String,
    logoUrl: String,
    host: Media3PlaybackHost,
    playback: PlaybackViewModel,
    onClose: () -> Unit,
) {
    val state by playback.state.collectAsStateWithLifecycle()
    var chrome by remember { mutableStateOf(true) }

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

        // The tap layer sits over the picture so a tap reaches the app and not
        // the decoder.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable { chrome = !chrome },
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
                        text = state.channelName.ifBlank { channelName },
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
