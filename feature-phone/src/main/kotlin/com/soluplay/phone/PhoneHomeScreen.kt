package com.soluplay.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.CATEGORY_FILTERS
import com.soluplay.core.browse.CategoryFilter
import com.soluplay.core.browse.allChannelsInOrder
import com.soluplay.core.browse.channelsInFilter
import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.PanelDeep
import com.soluplay.core.ui.PanelMid
import com.soluplay.core.ui.SiteChannelTile
import com.soluplay.core.ui.SiteFixtureBoard
import com.soluplay.core.ui.Slate400
import com.soluplay.core.ui.Slate800
import com.soluplay.core.ui.Slate950

/** How many channels stand side by side, the way the site stands them. */
private const val COLUMNS = 4

/**
 * The phone home: the board of matches, then the guide.
 *
 * The guide is the site's guide: one flat grid in the order the server
 * published, the categories as a row of pills that filter it in place, and
 * every channel on a round white plate with its name underneath. A channel
 * opens the watch screen, and a match opens the channel the board says it can
 * be watched on.
 *
 * A star on a channel says whether it is on the viewer's own list, and
 * tapping the star marks it without opening it.
 */
@Composable
fun PhoneHomeScreen(
    snapshot: BrowseSnapshot,
    marked: Set<String>,
    onToggleFavourite: (Channel) -> Unit,
    onOpen: (Channel) -> Unit,
    onPickFixture: (Fixture) -> Unit,
) {
    val channels = remember(snapshot.channels) { allChannelsInOrder(snapshot.channels) }

    // Which pill is on. A null one is "all", which is every channel the server
    // sent — the same list the site starts with.
    var filter by remember { mutableStateOf<CategoryFilter?>(null) }
    val visible = remember(channels, filter) { channelsInFilter(channels, filter) }
    val rows = remember(visible) { visible.chunked(COLUMNS) }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        if (snapshot.fixtures.isNotEmpty()) {
            item(key = "board") {
                SiteFixtureBoard(
                    fixtures = snapshot.fixtures,
                    onPick = onPickFixture,
                    modifier = Modifier.padding(top = 12.dp, bottom = 16.dp),
                )
            }
        }

        item(key = "pills") {
            PillRow(
                selected = filter,
                onPick = { filter = it },
            )
        }

        itemsIndexed(rows, key = { index, _ -> "row:$index" }) { index, row ->
            ChannelRow(
                channels = row,
                first = index == 0,
                last = index == rows.lastIndex,
                marked = marked,
                onToggleFavourite = onToggleFavourite,
                onPick = onOpen,
            )
        }

        if (visible.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = Copy.noChannelsOnline(),
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

/**
 * The row of pills that filters the guide.
 *
 * The pills sit on their own darker band, the way the site draws them, and
 * the one that is on is filled with the site's green.
 */
@Composable
private fun PillRow(
    selected: CategoryFilter?,
    onPick: (CategoryFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .background(PanelDeep),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            FilterPill(label = Copy.allChannels(), active = selected == null) {
                onPick(null)
            }
            CATEGORY_FILTERS.forEach { category ->
                FilterPill(label = category.name, active = selected == category) {
                    onPick(category)
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Slate800.copy(alpha = 0.6f)),
        )
    }
}

@Composable
private fun FilterPill(label: String, active: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        color = if (active) Slate950 else Slate400,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (active) Accent else PanelMid)
            .then(if (active) Modifier else Modifier.border(1.dp, Slate800, shape))
            .clickable { onPick() }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

/**
 * One row of the grid.
 *
 * The row is drawn on the guide's own panel, so the grid reads as one block
 * of channels rather than a list of loose tiles.
 */
@Composable
private fun ChannelRow(
    channels: List<Channel>,
    first: Boolean,
    last: Boolean,
    marked: Set<String>,
    onToggleFavourite: (Channel) -> Unit,
    onPick: (Channel) -> Unit,
) {
    val corners = RoundedCornerShape(
        bottomStart = if (last) 16.dp else 0.dp,
        bottomEnd = if (last) 16.dp else 0.dp,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(corners)
            .background(Panel)
            .padding(
                start = 8.dp,
                end = 8.dp,
                top = if (first) 8.dp else 3.dp,
                bottom = if (last) 8.dp else 3.dp,
            ),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        channels.forEach { channel ->
            SiteChannelTile(
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
