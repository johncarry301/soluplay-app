package com.soluplay.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.model.Channel

/**
 * One channel on the site's grid.
 *
 * The logo stands on the same white plate every logo stands on — the search
 * page's plate, so a channel looks the same on every screen — with the live
 * dot on its corner and the name under it.
 *
 * The dot and the star belong to the caller: the guide shows the name and the
 * picture alone, and the places that mark a channel ask for the star.
 *
 * The channel that is being watched is the one that is lit: its plate takes
 * the site's green edge and its name takes the site's green text.
 */
@Composable
fun SiteChannelTile(
    channel: Channel,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    marked: Boolean = false,
    onToggleFavourite: (() -> Unit)? = null,
    showLiveDot: Boolean = true,
) {
    Column(
        modifier = modifier.clickable { onPick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            ChannelLogo(
                channel = channel,
                size = 56.dp,
                border = BorderStroke(1.dp, if (active) LiveDot else Slate700),
            )

            if (showLiveDot) {
                SiteLiveDot(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = 2.dp, y = (-2).dp),
                )
            }

            if (onToggleFavourite != null) {
                FavouriteStar(
                    marked = marked,
                    onToggle = onToggleFavourite,
                    size = 15.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 3.dp, y = 3.dp),
                )
            }
        }

        Text(
            text = channel.name,
            color = if (active) EmeraldText else Slate200,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
