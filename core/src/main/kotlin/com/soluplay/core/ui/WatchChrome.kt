package com.soluplay.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.player.FailoverReason
import com.soluplay.core.player.PlaybackPhase
import com.soluplay.core.player.PlaybackState

/**
 * The one line the watch screen says about the playback.
 *
 * A viewer is told what is happening in words they can act on: the picture is
 * coming, it is here, or it stopped. The engine's reasons are never shown raw.
 */
@Composable
fun playbackStatus(state: PlaybackState): String = when (state.phase) {
    PlaybackPhase.IDLE -> ""
    PlaybackPhase.LOADING -> Copy.loadingLadder()
    PlaybackPhase.TUNING -> if (state.racing > 1) {
        Copy.racing(state.racing)
    } else {
        Copy.tuning(state.mirror, state.mirrorCount)
    }
    PlaybackPhase.ON_AIR -> Copy.live()
    PlaybackPhase.RECOVERING -> Copy.recovering()
    PlaybackPhase.EXHAUSTED -> when (state.failure) {
        FailoverReason.NO_MIRROR -> Copy.noMirror()
        else -> Copy.exhausted()
    }
    PlaybackPhase.FAILED -> Copy.ladderUnreadable()
}

/**
 * Why the last mirror was stepped over.
 *
 * This is the smaller line under the status: the picture is on screen, and the
 * viewer is told which mirror was given up and why.
 */
@Composable
fun playbackFailure(state: PlaybackState): String = when (state.failure) {
    null -> ""
    FailoverReason.NO_PICTURE -> text("ছবি আসেনি।", "The picture did not arrive.")
    FailoverReason.FROZEN -> text("ছবি থেমে গেছে।", "The picture stopped arriving.")
    FailoverReason.PLAYER_ERROR -> text("মিররটি চালানো যায়নি।", "That mirror could not be played.")
    FailoverReason.NO_MIRROR -> Copy.noMirror()
    FailoverReason.LADDER_UNREADABLE -> Copy.ladderUnreadable()
}

/**
 * The player's own reason for the last failure.
 *
 * It is the line under the reason: the reason says which mirror was given up,
 * and this says what the player said about it — the number YouTube refused a
 * video with, for instance. It is shown only when the player gave one.
 */
@Composable
fun playbackDetail(state: PlaybackState): String {
    val detail = state.detail
    if (detail.isBlank()) return ""
    val words = failureDetail(detail)
    return text(words.bengali, words.english)
}

/**
 * Where in the ladder the picture is coming from.
 *
 * The number is the published one, so a viewer who counts mirrors sees the
 * same order the site published.
 */
@Composable
fun MirrorBadge(
    state: PlaybackState,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
) {
    if (state.mirrorCount <= 0) return

    Text(
        text = text(
            "মিরর ${state.mirror} / ${state.mirrorCount}",
            "Mirror ${state.mirror} of ${state.mirrorCount}",
        ),
        color = Muted,
        fontSize = fontSize,
        modifier = modifier,
    )
}

/**
 * The panel the watch screen shows when nothing is playing.
 *
 * The picture area stays where it is: the message sits inside the frame the
 * video occupies, so a dead feed never becomes a separate screen.
 */
@Composable
fun WeakSignalPanel(
    state: PlaybackState,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 16.sp,
    bodySize: TextUnit = 13.sp,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black.copy(alpha = 0.62f))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = playbackStatus(state),
            color = Accent,
            fontSize = titleSize,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = playbackFailure(state),
            color = Muted,
            fontSize = bodySize,
        )
        val detail = playbackDetail(state)
        if (detail.isNotBlank()) {
            Text(
                text = detail,
                color = Muted,
                fontSize = (bodySize.value - 1f).coerceAtLeast(9f).sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The bar under the picture: the channel, the ladder and the way back.
 */
@Composable
fun WatchFooter(
    state: PlaybackState,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 15.sp,
    bodySize: TextUnit = 12.sp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = state.channelName,
                color = Color.White,
                fontSize = titleSize,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = playbackStatus(state),
                color = if (state.onAir) Accent else Muted,
                fontSize = bodySize,
                maxLines = 1,
            )
        }

        MirrorBadge(state = state, fontSize = bodySize)

        if (onBack != null) {
            Text(
                text = Copy.back(),
                color = Accent,
                fontSize = bodySize,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Panel)
                    .clickable { onBack() }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}
