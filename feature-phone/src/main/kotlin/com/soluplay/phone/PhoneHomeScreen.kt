package com.soluplay.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.CategoryFilter
import com.soluplay.core.browse.allChannelsInOrder
import com.soluplay.core.browse.channelsInFilter
import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.CategoryPillRow
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.PanelDeep
import com.soluplay.core.ui.SiteChannelTile
import com.soluplay.core.ui.SiteFixtureBoard
import com.soluplay.core.ui.Slate800

/** How many channels stand side by side, the way the site stands them. */
private const val COLUMNS = 4

/**
 * The phone home: the board of matches, then the guide.
 *
 * The guide is the site's guide: one flat grid in the order the server
 * published, the categories as a row of pills that filter it in place, and
 * every channel on the same white plate with its name underneath. A channel
 * opens the watch screen, and a match opens the channel the board says it can
 * be watched on.
 *
 * The guide shows the picture and the name and nothing else: the live dot and
 * the star are the watch screen's business, and a grid of channels is easier
 * to read when the plate carries nothing but the logo.
 */
@Composable
fun PhoneHomeScreen(
    snapshot: BrowseSnapshot,
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
 * the one that is on is filled with the site's green. All of them are on one
 * line: a filter a viewer has to scroll to find is a filter nobody uses.
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
        CategoryPillRow(
            selected = selected,
            onPick = onPick,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Slate800.copy(alpha = 0.6f)),
        )
    }
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
                onPick = { onPick(channel) },
                showLiveDot = false,
                modifier = Modifier.weight(1f),
            )
        }
        repeat(COLUMNS - channels.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}
