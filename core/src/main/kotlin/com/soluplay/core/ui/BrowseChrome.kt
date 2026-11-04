package com.soluplay.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.CatalogueOrigin

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
