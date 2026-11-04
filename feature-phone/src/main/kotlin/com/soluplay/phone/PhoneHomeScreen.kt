package com.soluplay.phone

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.CatalogueOrigin
import com.soluplay.core.browse.GuideItem
import com.soluplay.core.browse.Rail
import com.soluplay.core.browse.guideItemsOf
import com.soluplay.core.browse.railHeadingsOf
import com.soluplay.core.browse.railsOf
import com.soluplay.core.fixtures.FixtureTarget
import com.soluplay.core.fixtures.fixtureTarget
import com.soluplay.core.fixtures.playableChoices
import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture
import com.soluplay.core.model.FixtureChoice
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.LogoPlate
import com.soluplay.core.ui.MatchStrip
import com.soluplay.core.ui.OutageBanner
import com.soluplay.core.ui.SourceBadge
import com.soluplay.core.ui.outageMessage
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.Accent
import kotlinx.coroutines.launch

private const val COLUMNS = 4

/** The gaps the guide's list keeps between its parts. */
private val GAP_RAIL = 10.dp
private val GAP_ROW = 12.dp
private val GAP_SECTION = 18.dp

/** Where the phone is: the guide, or a channel being watched. */
private sealed interface PhoneRoute {
    data object Home : PhoneRoute
    data class Watch(
        val channelId: String,
        val channelName: String,
        val logoUrl: String,
    ) : PhoneRoute
}

/**
 * The phone home surface: the matches, then the rails, then the logo grid.
 *
 * The guide is the server's order and is never re-ranked here; a rail is
 * simply the channels the server filed under one category. A channel opens the
 * watch screen, and a match opens the channel the board says it can be
 * watched on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PhoneHomeScreen(
    viewModel: BrowseViewModel,
    playback: PlaybackViewModel,
    host: Media3PlaybackHost,
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val rails = remember(snapshot.channels) { railsOf(snapshot.channels) }

    var route by remember { mutableStateOf<PhoneRoute>(PhoneRoute.Home) }
    var sheet by remember { mutableStateOf<Fixture?>(null) }
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

    val watching = route as? PhoneRoute.Watch
    if (watching != null) {
        PhoneWatchScreen(
            channelId = watching.channelId,
            channelName = watching.channelName,
            logoUrl = watching.logoUrl,
            host = host,
            playback = playback,
            onClose = { route = PhoneRoute.Home },
        )
        return
    }

    // Back puts the choice sheet away before it leaves the screen, the same
    // way the sheet's own button does.
    BackHandler(enabled = sheet != null) { sheet = null }

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
                                    onPick = { fixture ->
                                        when (val target = fixtureTarget(fixture)) {
                                            is FixtureTarget.Open -> route = PhoneRoute.Watch(
                                                channelId = target.choice.channelId,
                                                channelName = target.choice.name,
                                                logoUrl = target.choice.logoUrl,
                                            )
                                            // More than one channel, or none that
                                            // can be opened: the sheet says which.
                                            else -> sheet = fixture
                                        }
                                    },
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
                                        // The pill row is pinned to the top, so a
                                        // rail that lands flush with the top
                                        // would hide its own title under it.
                                        // A negative offset puts the rail below
                                        // the pill row instead.
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
                                onPick = { channel ->
                                    route = PhoneRoute.Watch(
                                        channelId = channel.id,
                                        channelName = channel.name,
                                        logoUrl = channel.logo,
                                    )
                                },
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

            val open = sheet
            if (open != null) {
                ChoiceSheet(
                    fixture = open,
                    choices = playableChoices(open),
                    onPick = { choice ->
                        sheet = null
                        route = PhoneRoute.Watch(
                            channelId = choice.channelId,
                            channelName = choice.name,
                            logoUrl = choice.logoUrl,
                        )
                    },
                    onDismiss = { sheet = null },
                )
            }
        }
    }
}

/**
 * The channels a match card can be watched on.
 *
 * A card with one channel opens it, a card with several asks, and a card with
 * none says so instead of opening nothing.
 */
@Composable
private fun ChoiceSheet(
    fixture: Fixture,
    choices: List<FixtureChoice>,
    onPick: (FixtureChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Panel)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = fixture.title.ifBlank { fixture.competition },
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (choices.isEmpty()) {
                Text(
                    text = Copy.nothingToOpen(),
                    color = Muted,
                    fontSize = 13.sp,
                )
            } else {
                Text(
                    text = Copy.chooseChannel(),
                    color = Muted,
                    fontSize = 12.sp,
                )
                choices.forEach { choice ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPick(choice) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(40.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            LogoPlate(
                                url = choice.logoUrl,
                                fallbackText = choice.name,
                                size = 40.dp,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = choice.name,
                                color = Color.White,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = choice.category,
                                color = Muted,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            Text(
                text = Copy.close(),
                color = Accent,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Background)
                    .clickable { onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
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
