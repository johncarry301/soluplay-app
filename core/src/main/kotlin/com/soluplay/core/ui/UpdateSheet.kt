package com.soluplay.core.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.update.BlockReason
import com.soluplay.core.update.UpdateOffer
import com.soluplay.core.update.UpdateState
import com.soluplay.core.update.offerOrNull

/**
 * The sheet that asks about a newer build.
 *
 * It is written once and drawn on both surfaces: the phone lays it at the
 * bottom of the screen, the television in the middle of it. Nothing here
 * decides anything — the state it is given already says what is being asked.
 */
@Composable
fun UpdateSheet(
    state: UpdateState,
    alignment: Alignment,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    panelWidth: Dp = 420.dp,
    onInstall: () -> Unit,
    onAllowInstall: () -> Unit,
    onLater: () -> Unit,
    onRetry: () -> Unit,
) {
    if (state is UpdateState.Idle || state is UpdateState.Checking) return
    if (state is UpdateState.Blocked) return
    val offer = state.offerOrNull() ?: return

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f)),
        )

        Column(
            modifier = Modifier
                .align(alignment)
                .fillMaxWidth()
                .widthIn(max = panelWidth)
                .clip(RoundedCornerShape(18.dp))
                .background(Panel)
                .padding((16 * scale).dp),
            verticalArrangement = Arrangement.spacedBy((10 * scale).dp),
        ) {
            Text(
                text = Copy.newVersion(offer.versionName),
                color = Accent,
                fontSize = (16 * scale).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Text(
                text = bodyOf(state, offer),
                color = Muted,
                fontSize = (13 * scale).sp,
            )

            statusOf(state, offer, scale)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                when (state) {
                    is UpdateState.PermissionNeeded -> UpdateAction(
                        label = Copy.allowInstall(),
                        primary = true,
                        scale = scale,
                        onClick = onAllowInstall,
                    )

                    is UpdateState.Failed -> UpdateAction(
                        label = Copy.tryAgain(),
                        primary = true,
                        scale = scale,
                        onClick = onRetry,
                    )

                    else -> UpdateAction(
                        label = Copy.installNow(),
                        primary = true,
                        scale = scale,
                        onClick = onInstall,
                    )
                }

                if (state is UpdateState.Offered || state is UpdateState.Failed) {
                    UpdateAction(
                        label = Copy.later(),
                        primary = false,
                        scale = scale,
                        onClick = onLater,
                    )
                }
            }
        }
    }
}

/**
 * The panel that stands in for the guide when the app may not play.
 *
 * A retired build and a mandatory update that has outlasted its grace both
 * stop the app from playing, and both are shown the same way: the reason, and
 * the one button that fixes it.
 */
@Composable
fun UpdateBlockPanel(
    state: UpdateState.Blocked,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    onInstall: () -> Unit,
    onAllowInstall: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Panel)
                .padding((24 * scale).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy((12 * scale).dp),
        ) {
            Text(
                text = Copy.updateRequired(),
                color = Accent,
                fontSize = (19 * scale).sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (state.reason == BlockReason.RETIRED) {
                    Copy.updateRequiredBody()
                } else {
                    Copy.updateMandatoryBody()
                },
                color = Muted,
                fontSize = (14 * scale).sp,
                textAlign = TextAlign.Center,
            )

            UpdateAction(
                label = Copy.installNow(),
                primary = true,
                scale = scale,
                onClick = onInstall,
            )
        }
    }
}

/** The words under the title: what the release says, or why it matters. */
@Composable
private fun bodyOf(state: UpdateState, offer: UpdateOffer): String {
    if (state is UpdateState.Blocked) {
        return if (state.reason == BlockReason.RETIRED) {
            Copy.updateRequiredBody()
        } else {
            Copy.updateMandatoryBody()
        }
    }
    if (offer.releaseNotes.isNotBlank()) return offer.releaseNotes
    if (offer.mandatory) return Copy.updateMandatoryBody()
    return Copy.updateAvailableBody()
}

/** The line that says what is happening right now. */
@Composable
private fun statusOf(state: UpdateState, offer: UpdateOffer, scale: Float) {
    when (state) {
        is UpdateState.Downloading -> {
            val fraction = if (state.total > 0L) {
                (state.downloaded.toDouble() / state.total.toDouble()).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
            Text(
                text = Copy.downloadingUpdate(),
                color = Accent,
                fontSize = (12 * scale).sp,
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((6 * scale).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Panel),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(fraction.toFloat())
                        .height((6 * scale).dp)
                        .clip(RoundedCornerShape(50))
                        .background(Accent),
                )
            }
        }

        is UpdateState.Installing -> Text(
            text = Copy.installingUpdate(),
            color = Accent,
            fontSize = (12 * scale).sp,
        )

        is UpdateState.Ready -> Text(
            text = Copy.readyToInstall(),
            color = Accent,
            fontSize = (12 * scale).sp,
        )

        is UpdateState.PermissionNeeded -> Text(
            text = Copy.updatePermissionNeeded(),
            color = Accent,
            fontSize = (12 * scale).sp,
        )

        is UpdateState.Failed -> Text(
            text = Copy.updateFailed(),
            color = Muted,
            fontSize = (12 * scale).sp,
        )

        else -> Unit
    }
}

/** One button, in the chrome of the app. */
@Composable
private fun UpdateAction(
    label: String,
    primary: Boolean,
    scale: Float,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (primary) Background else Accent,
        fontSize = (13 * scale).sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (primary) Accent else Background)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 9.dp),
    )
}
