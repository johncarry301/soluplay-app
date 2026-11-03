package com.soluplay.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.CatalogueOrigin
import com.soluplay.core.model.Channel

/**
 * Which copy of the guide the screen is showing.
 *
 * The site is the source of truth, so when the guide is not live the viewer
 * has to be told — a saved list looks exactly like a live one otherwise.
 */
@Composable
fun SourceBadge(
    origin: CatalogueOrigin,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 12.sp,
) {
    val label = when (origin) {
        CatalogueOrigin.LIVE -> Copy.live()
        CatalogueOrigin.CACHED -> Copy.savedCopy()
        CatalogueOrigin.SEED -> Copy.builtInCopy()
        CatalogueOrigin.NONE -> Copy.noCopy()
    }
    val tint = if (origin == CatalogueOrigin.LIVE) Accent else Muted

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint),
        )
        Text(text = label, color = tint, fontSize = fontSize)
    }
}

/** The banner that says the site could not be reached. */
@Composable
fun OutageBanner(
    message: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 13.sp,
) {
    Text(
        text = message,
        color = Muted,
        fontSize = fontSize,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

/**
 * The line the outage banner shows.
 *
 * A site that cannot be reached and a site that answered with nothing are
 * different things, and the copy on screen differs too — so the banner says
 * which one it was and the badge says which list is on screen.
 */
@Composable
fun outageMessage(snapshot: BrowseSnapshot): String = when {
    snapshot.origin == CatalogueOrigin.NONE && snapshot.siteReachable -> Copy.siteGaveNothingNoCopy()
    snapshot.origin == CatalogueOrigin.NONE -> Copy.siteDownNoCopy()
    snapshot.siteReachable -> Copy.siteGaveNothing()
    else -> Copy.siteDown()
}

/**
 * What is known about a channel that was picked.
 *
 * Phase 1 browses; the mirror ladder is walked in Phase 2, so the card says
 * how many mirrors exist and stops there.
 */
@Composable
fun ChannelDetailsCard(
    channel: Channel,
    origin: CatalogueOrigin,
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 17.sp,
    bodySize: TextUnit = 13.sp,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChannelLogo(channel = channel, size = 56.dp)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = channel.name,
                    color = Accent,
                    fontSize = titleSize,
                    fontWeight = FontWeight.Bold,
                )
                Text(text = channel.category, color = Muted, fontSize = bodySize)
            }
        }

        Text(
            text = when {
                // The baked copy carries no ladder, so it cannot say whether
                // there are mirrors — and must not say there are none.
                origin == CatalogueOrigin.SEED -> Copy.mirrorsUnknown()
                channel.activeStreamCount > 0 -> Copy.mirrors(channel.activeStreamCount)
                else -> Copy.noMirror()
            },
            color = Muted,
            fontSize = bodySize,
        )

        Text(text = Copy.playbackLater(), color = Muted, fontSize = (bodySize.value - 1).sp)
    }
}
