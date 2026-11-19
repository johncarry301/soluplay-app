package com.soluplay.phone

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.CategoryFilter
import com.soluplay.core.browse.allChannelsInOrder
import com.soluplay.core.browse.channelsInFilter
import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.CategoryPillRow
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.PanelDeep
import com.soluplay.core.ui.PanelMid
import com.soluplay.core.ui.SiteChannelTile
import com.soluplay.core.ui.SiteFixtureBoard
import com.soluplay.core.ui.Slate400
import com.soluplay.core.ui.Slate800

/** How many channels stand side by side, the way the site stands them. */
private const val COLUMNS = 4

/**
 * The phone home, as the site's own home on a phone.
 *
 * The board of matches, then the guide in one panel: the pills on their dark
 * band, the grid of channels on the same round white plates the site uses,
 * every plate with its live dot.
 *
 * There is no refresh button: the guide refreshes itself on every fresh
 * start, and a hard pull down asks for it again — the pull is the site's own
 * gesture, done by the app's own hand.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneHomeScreen(
    snapshot: BrowseSnapshot,
    onOpen: (Channel) -> Unit,
    onPickFixture: (Fixture) -> Unit,
    filter: CategoryFilter?,
    onFilter: (CategoryFilter?) -> Unit,
    onRefresh: () -> Unit,
) {
    val channels = remember(snapshot.channels) { allChannelsInOrder(snapshot.channels) }
    val visible = remember(channels, filter) { channelsInFilter(channels, filter) }
    val rows = remember(visible) { visible.chunked(COLUMNS) }

    val refreshState = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = snapshot.loading,
        onRefresh = onRefresh,
        state = refreshState,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = refreshState,
                isRefreshing = snapshot.loading,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = Panel,
                color = Accent,
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 20.dp),
        ) {
            if (snapshot.fixtures.isNotEmpty()) {
                item(key = "board") {
                    SiteFixtureBoard(
                        fixtures = snapshot.fixtures,
                        onPick = onPickFixture,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
            }

            item(key = "guide") {
                GuidePanel(
                    loading = snapshot.loading && channels.isEmpty(),
                    rows = rows,
                    empty = visible.isEmpty(),
                    filter = filter,
                    onFilter = onFilter,
                    onOpen = onOpen,
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        top = if (snapshot.fixtures.isNotEmpty()) 20.dp else 0.dp,
                        bottom = 16.dp,
                    ),
                )
            }
        }
    }
}

/**
 * The guide's own panel: the pill band, then the grid — or the skeleton the
 * site shows while the channels are coming, or the words it shows when
 * nothing is on.
 */
@Composable
private fun GuidePanel(
    loading: Boolean,
    rows: List<List<Channel>>,
    empty: Boolean,
    filter: CategoryFilter?,
    onFilter: (CategoryFilter?) -> Unit,
    onOpen: (Channel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Panel)
            .border(1.dp, Slate800.copy(alpha = 0.8f), shape),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(PanelDeep),
        ) {
            CategoryPillRow(
                selected = filter,
                onPick = onFilter,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate800.copy(alpha = 0.6f)),
            )
        }

        when {
            loading -> SkeletonGrid()
            empty -> EmptyGuide(filter = filter)
            else -> rows.forEachIndexed { index, row ->
                ChannelRow(
                    channels = row,
                    first = index == 0,
                    last = index == rows.lastIndex,
                    onPick = onOpen,
                )
            }
        }
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                showLiveDot = true,
                shape = CircleShape,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
            )
        }
        repeat(COLUMNS - channels.size) {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

/** The grid the site pulses while the channels are on their way. */
@Composable
private fun SkeletonGrid() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(6) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(COLUMNS) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(PanelMid.copy(alpha = alpha)),
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(12.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(PanelMid.copy(alpha = alpha)),
                        )
                    }
                }
            }
        }
    }
}

/** The words the site shows when a grid has nothing on it. */
@Composable
private fun EmptyGuide(filter: CategoryFilter?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TvGlyph(tint = Color(0xFF475569), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (filter == null) {
                Copy.noChannelsOnlineTitle()
            } else {
                Copy.noCategoryChannelsTitle(filter.name)
            },
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (filter == null) Copy.emptyAllBody() else Copy.emptyCategoryBody(),
            color = Slate400,
            fontSize = 12.sp,
            textAlign = TextAlign.Center,
        )
    }
}