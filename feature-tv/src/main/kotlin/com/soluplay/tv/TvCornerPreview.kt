package com.soluplay.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.app.player.PlayerSurface
import com.soluplay.core.model.Channel
import com.soluplay.core.player.PlaybackState
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.playbackStatus

/**
 * The channel that is playing in the corner.
 *
 * While the guide is being browsed, the channel the remote is standing on
 * plays in the corner of the screen: the viewer sees what the channel is
 * before opening it, and the sound carries on while the guide is read.
 *
 * The preview is a player of its own, so the picture of the guide and the
 * picture of the watch screen never fight over the same decoder.
 */
@Composable
fun TvCornerPreview(
    host: Media3PlaybackHost,
    state: PlaybackState,
    channel: Channel,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(380.dp)
            .height(214.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black)
            .border(2.dp, Accent, RoundedCornerShape(12.dp)),
    ) {
        PlayerSurface(host = host, modifier = Modifier.fillMaxSize())

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = channel.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = playbackStatus(state),
                color = if (state.onAir) Accent else Muted,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
