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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.CatalogueOrigin
import com.soluplay.core.browse.Rail
import com.soluplay.core.browse.railsOf
import com.soluplay.core.model.Channel
import com.soluplay.core.ui.ChannelDetailsCard
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.MatchStrip
import com.soluplay.core.ui.OutageBanner
import com.soluplay.core.ui.SourceBadge
import com.soluplay.core.ui.outageMessage
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.Accent
import kotlinx.coroutines.launch

private const val COLUMNS = 4

/**
 * The phone home surface: the matches, then the rails, then the logo grid.
 *
 * The guide is the server's order and is never re-ranked here; a rail is
 * simply the channels the server filed under one category.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneHomeScreen(viewModel: BrowseViewModel) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val rails = remember(snapshot.channels) { railsOf(snapshot.channels) }

    var selected by remember { mutableStateOf<Channel?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var pillHeight by remember { mutableIntStateOf(0) }

    val hasStrip = snapshot.fixtures.isNotEmpty()
    val hasPills = rails.isNotEmpty()
    val leadingItems = (if (hasStrip) 1 else 0) + (if (hasPills) 1 else 0)

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopBar(
                    origin = snapshot.origin,
                    loading = snapshot.loading,
                    onRefresh = viewModel::refresh,
                )

                if (snapshot.outage) {
                    OutageBanner(
                        message = outageMessage(snapshot),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }

                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (hasStrip) {
                        item(key = "matches") {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    text = Copy.todaysMatches(),
                                    color = Accent,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                MatchStrip(fixtures = snapshot.fixtures)
                            }
                        }
                    }

                    if (hasPills) {
                        stickyHeader(key = "pills") {
                            PillRow(
                                rails = rails,
                                onPick = { index ->
                                    scope.launch {
                                        // The pill row is pinned to the top, so a
                                        // rail that lands flush with the top
                                        // would hide its own title under it.
                                        // A negative offset puts the rail below
                                        // the pill row instead.
                                        listState.animateScrollToItem(
                                            index = leadingItems + index,
                                            scrollOffset = -pillHeight,
                                        )
                                    }
                                },
                                modifier = Modifier.onSizeChanged { pillHeight = it.height },
                            )
                        }
                    }

                    itemsIndexed(rails, key = { _, rail -> rail.name }) { _, rail ->
                        RailSection(rail = rail, onPick = { selected = it })
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

            val chosen = selected
            if (chosen != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f))
                            .clickable { selected = null },
                    )
                    ChannelDetailsCard(
                        channel = chosen,
                        origin = snapshot.origin,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    origin: CatalogueOrigin,
    loading: Boolean,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "SoluPlay",
            color = Accent,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.width(10.dp))
        SourceBadge(origin = origin)
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = if (loading) Copy.refreshing() else Copy.refresh(),
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(Panel)
                .clickable(enabled = !loading) { onRefresh() }
                .padding(horizontal = 14.dp, vertical = 7.dp),
        )
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
private fun RailSection(
    rail: Rail,
    onPick: (Channel) -> Unit,
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = rail.name,
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = rail.channels.size.toString(), color = Muted, fontSize = 12.sp)
        }

        ChannelGrid(channels = rail.channels, onPick = onPick)
    }
}

@Composable
private fun ChannelGrid(
    channels: List<Channel>,
    onPick: (Channel) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        channels.chunked(COLUMNS).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                row.forEach { channel ->
                    ChannelTile(
                        channel = channel,
                        onPick = { onPick(channel) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(COLUMNS - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ChannelTile(
    channel: Channel,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable { onPick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        ChannelLogo(channel = channel, size = 46.dp)
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
