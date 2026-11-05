package com.soluplay.phone

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.GuideItem
import com.soluplay.core.browse.Rail
import com.soluplay.core.browse.guideItemsOf
import com.soluplay.core.browse.railHeadingsOf
import com.soluplay.core.browse.railsOf
import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.FavouriteStar
import com.soluplay.core.ui.MatchStrip
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import kotlinx.coroutines.launch

private const val COLUMNS = 4

/** The gaps the guide's list keeps between its parts. */
private val GAP_RAIL = 10.dp
private val GAP_ROW = 12.dp
private val GAP_SECTION = 18.dp

/**
 * The phone home: the matches, then the rails, then the logo grid.
 *
 * The guide is the server's order and is never re-ranked here; a rail is
 * simply the channels the server filed under one category. A channel opens the
 * watch screen, and a match opens the channel the board says it can be
 * watched on.
 *
 * A star on a channel says whether it is on the viewer's own list, and
 * tapping the star marks it without opening it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneHomeScreen(
    snapshot: BrowseSnapshot,
    marked: Set<String>,
    onToggleFavourite: (Channel) -> Unit,
    onOpen: (Channel) -> Unit,
    onPickFixture: (Fixture) -> Unit,
) {
    val rails = remember(snapshot.channels) { railsOf(snapshot.channels) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var pillHeight by remember { mutableIntStateOf(0) }

    val hasStrip = snapshot.fixtures.isNotEmpty()
    val hasPills = rails.isNotEmpty()
    val leadingItems = (if (hasStrip) 1 else 0) + (if (hasPills) 1 else 0)

    // The guide's list is a flat one, so the list draws only what is on the
    // screen: a heading, and a row of channels at a time.
    val guideItems = remember(rails) { guideItemsOf(rails, COLUMNS) }

    // Where the heading of each rail sits in that list, so a pill can jump to
    // the rail it names.
    val railHeadings = remember(rails) { railHeadingsOf(rails, COLUMNS) }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (hasStrip) {
            item(key = "matches") {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = GAP_SECTION,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = Copy.todaysMatches(),
                        color = Accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    MatchStrip(
                        fixtures = snapshot.fixtures,
                        onPick = onPickFixture,
                    )
                }
            }
        }

        if (hasPills) {
            stickyHeader(key = "pills") {
                PillRow(
                    rails = rails,
                    onPick = { index ->
                        scope.launch {
                            // The pill row is pinned to the top, so a rail
                            // that lands flush with the top would hide its own
                            // title under it. A negative offset puts the rail
                            // below the pill row instead.
                            listState.animateScrollToItem(
                                index = leadingItems +
                                    railHeadings.getOrElse(index) { 0 },
                                scrollOffset = -pillHeight,
                            )
                        }
                    },
                    modifier = Modifier
                        .onSizeChanged { pillHeight = it.height }
                        .padding(bottom = GAP_SECTION),
                )
            }
        }

        items(guideItems, key = { it.key }) { item ->
            when (item) {
                is GuideItem.Heading -> RailHeading(
                    name = item.name,
                    count = item.count,
                )

                is GuideItem.Row -> ChannelRow(
                    channels = item.channels,
                    last = item.last,
                    marked = marked,
                    onToggleFavourite = onToggleFavourite,
                    onPick = onOpen,
                )
            }
        }

        if (!hasPills) {
            item(key = "empty") {
                Text(
                    text = Copy.noCopy(),
                    color = Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 40.dp),
                )
            }
        }
    }
}

@Composable
private fun PillRow(
    rails: List<Rail>,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rails.forEachIndexed { index, rail ->
            Text(
                text = rail.name,
                color = Accent,
                fontSize = 12.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Panel)
                    .clickable { onPick(index) }
                    .padding(horizontal = 14.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun RailHeading(name: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = GAP_RAIL),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = name,
            color = Accent,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = count.toString(), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun ChannelRow(
    channels: List<Channel>,
    last: Boolean,
    marked: Set<String>,
    onToggleFavourite: (Channel) -> Unit,
    onPick: (Channel) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = if (last) GAP_SECTION else GAP_ROW),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        channels.forEach { channel ->
            ChannelTile(
                channel = channel,
                marked = channel.id in marked,
                onToggleFavourite = { onToggleFavourite(channel) },
                onPick = { onPick(channel) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(COLUMNS - channels.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChannelTile(
    channel: Channel,
    marked: Boolean,
    onToggleFavourite: () -> Unit,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable { onPick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            ChannelLogo(channel = channel, size = 46.dp)
            FavouriteStar(
                marked = marked,
                onToggle = onToggleFavourite,
                size = 15.dp,
                modifier = Modifier.padding(1.dp),
            )
        }
        Text(
            text = channel.name,
            color = Color.White,
            fontSize = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
