package com.soluplay.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.Rail
import com.soluplay.core.browse.railsOf
import com.soluplay.core.model.Channel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.ChannelDetailsCard
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.MatchStrip
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.OutageBanner
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.SourceBadge
import com.soluplay.core.ui.outageMessage
import kotlinx.coroutines.launch

/**
 * The TV is a different app from the phone, not a bigger one: rows, a focus
 * grid and the remote alone.
 *
 * The guide is never re-ranked here either — the rails and their order are the
 * server's.
 */
@Composable
fun TvHomeScreen(viewModel: BrowseViewModel) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val rails = remember(snapshot.channels) { railsOf(snapshot.channels) }

    val refreshRequester = remember { FocusRequester() }
    val railRequesters = remember(rails) { rails.map { rail -> rail.channels.map { FocusRequester() } } }

    var focusedRow by remember { mutableIntStateOf(1) }
    var focusedIndex by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Channel?>(null) }
    val scope = rememberCoroutineScope()

    val verticalScroll = rememberScrollState()
    val window = remember { DrawnBox() }
    val railBoxes = remember(rails) { rails.map { DrawnBox() } }

    val rowCount = railRequesters.size + 1

    fun requesterAt(row: Int, index: Int): FocusRequester? {
        if (row == 0) return refreshRequester
        val rail = railRequesters.getOrNull(row - 1) ?: return null
        return rail.getOrNull(index)
    }

    /**
     * Brings the row the focus is on into the window.
     *
     * A scrolling column does not follow the focus, so the row that was drawn
     * is measured and the column is scrolled by the difference.
     */
    fun revealRail(row: Int) {
        if (row == 0) {
            // The refresh control lives in the top bar, so the guide goes
            // back to its beginning.
            if (verticalScroll.value > 0) {
                scope.launch { verticalScroll.animateScrollTo(0) }
            }
            return
        }
        val box = railBoxes.getOrNull(row - 1) ?: return
        val delta = box.overflowVertically(window.top, window.bottom)
        if (delta != 0f) scope.launch { verticalScroll.animateScrollBy(delta) }
    }

    /**
     * Puts the focus back where the viewer left it.
     *
     * A refresh rebuilds the grid, so the focus would be lost with it. The
     * cell the viewer was on is asked for again, and the first cell is the
     * answer when that one is gone.
     */
    LaunchedEffect(rails) {
        val target = requesterAt(focusedRow, focusedIndex)
            ?: railRequesters.firstOrNull()?.firstOrNull()
            ?: refreshRequester
        target.requestFocus()
    }

    fun move(dRow: Int, dColumn: Int) {
        val row = (focusedRow + dRow).coerceIn(0, rowCount - 1)
        val size = if (row == 0) 1 else (railRequesters.getOrNull(row - 1)?.size ?: 0)
        if (size == 0) return
        val index = (focusedIndex + dColumn).coerceIn(0, size - 1)
        focusedRow = row
        focusedIndex = index
        val requester = requesterAt(row, index) ?: return
        scope.launch { requester.requestFocus() }
    }

    fun channelAt(row: Int, index: Int): Channel? {
        if (row == 0) return null
        return rails.getOrNull(row - 1)?.channels?.getOrNull(index)
    }

    BackHandler(enabled = selected != null) { selected = null }

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .padding(horizontal = 48.dp, vertical = 36.dp)
                    .onGloballyPositioned { window.read(it) }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false

                        // The details card is modal: the arrows belong to the
                        // card until it is closed.
                        val cardOpen = selected != null

                        when (event.key) {
                            Key.DirectionRight -> {
                                if (!cardOpen) move(0, 1)
                                true
                            }
                            Key.DirectionLeft -> {
                                if (!cardOpen) move(0, -1)
                                true
                            }
                            Key.DirectionDown -> {
                                if (!cardOpen) move(1, 0)
                                true
                            }
                            Key.DirectionUp -> {
                                if (!cardOpen) move(-1, 0)
                                true
                            }
                            Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar -> {
                                if (cardOpen) {
                                    selected = null
                                } else {
                                    val channel = channelAt(focusedRow, focusedIndex)
                                    if (channel != null) {
                                        selected = channel
                                    } else {
                                        viewModel.refresh()
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    },
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "SoluPlay",
                        color = Accent,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    SourceBadge(origin = snapshot.origin, fontSize = 18.sp)
                    Spacer(modifier = Modifier.weight(1f))
                    RefreshPill(
                        loading = snapshot.loading,
                        requester = refreshRequester,
                        onFocused = {
                            focusedRow = 0
                            focusedIndex = 0
                        },
                        onRefresh = viewModel::refresh,
                    )
                }

                if (snapshot.outage) {
                    OutageBanner(
                        message = outageMessage(snapshot),
                        fontSize = 18.sp,
                    )
                }

                if (snapshot.fixtures.isNotEmpty()) {
                    Text(
                        text = Copy.todaysMatches(),
                        color = Accent,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    MatchStrip(fixtures = snapshot.fixtures, scale = 1.7f)
                }

                rails.forEachIndexed { rowIndex, rail ->
                    RailRow(
                        rail = rail,
                        requesters = railRequesters.getOrNull(rowIndex).orEmpty(),
                        box = railBoxes.getOrNull(rowIndex) ?: DrawnBox(),
                        onFocused = { index ->
                            focusedRow = rowIndex + 1
                            focusedIndex = index
                            revealRail(rowIndex + 1)
                        },
                    )
                }

                if (rails.isEmpty()) {
                    Text(text = Copy.noCopy(), color = Muted, fontSize = 18.sp)
                }
            }

            val chosen = selected
            if (chosen != null) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.65f)),
                    )
                    ChannelDetailsCard(
                        channel = chosen,
                        origin = snapshot.origin,
                        titleSize = 22.sp,
                        bodySize = 18.sp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(48.dp),
                    )
                }
            }
        }
    }
}

/**
 * Where a thing was drawn, in the coordinates of the screen.
 *
 * The TV is a remote, so the thing the focus is on has to be the thing the
 * viewer can see. A scrolling column does not follow the focus, so what was
 * drawn is measured and the scroll is moved by the difference.
 */
private class DrawnBox {
    var top = 0f
    var bottom = 0f
    var left = 0f
    var right = 0f

    fun read(coords: LayoutCoordinates) {
        val origin = coords.positionInRoot()
        top = origin.y
        bottom = origin.y + coords.size.height
        left = origin.x
        right = origin.x + coords.size.width
    }

    /** How far the scroll has to move for this box to sit inside the window. */
    fun overflowVertically(top: Float, bottom: Float): Float = when {
        this.top < top -> this.top - top
        this.bottom > bottom -> this.bottom - bottom
        else -> 0f
    }

    /** The same, along the row. */
    fun overflowHorizontally(left: Float, right: Float): Float = when {
        this.left < left -> this.left - left
        this.right > right -> this.right - right
        else -> 0f
    }
}

@Composable
private fun RefreshPill(
    loading: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
    onRefresh: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Text(
        text = if (loading) Copy.refreshing() else Copy.refresh(),
        color = if (focused) Background else Muted,
        fontSize = 18.sp,
        modifier = Modifier
            .focusRequester(requester)
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Accent else Panel)
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun RailRow(
    rail: Rail,
    requesters: List<FocusRequester>,
    box: DrawnBox,
    onFocused: (Int) -> Unit,
) {
    val railScroll = rememberScrollState()
    val rowBox = remember { DrawnBox() }
    val tiles = remember(rail.channels) { rail.channels.map { DrawnBox() } }
    val scope = rememberCoroutineScope()

    /** Brings the focused tile into the row, the way the remote expects. */
    fun revealTile(index: Int) {
        val tile = tiles.getOrNull(index) ?: return
        val delta = tile.overflowHorizontally(rowBox.left, rowBox.right)
        if (delta != 0f) scope.launch { railScroll.animateScrollBy(delta) }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = rail.name,
            color = Accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(railScroll)
                .onGloballyPositioned { rowBox.read(it) },
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            rail.channels.forEachIndexed { index, channel ->
                val requester = requesters.getOrNull(index) ?: return@forEachIndexed
                TvChannelTile(
                    channel = channel,
                    requester = requester,
                    tileBox = tiles.getOrNull(index),
                    onFocused = {
                        onFocused(index)
                        revealTile(index)
                    },
                )
            }
        }
    }
}

@Composable
private fun TvChannelTile(
    channel: Channel,
    requester: FocusRequester,
    tileBox: DrawnBox?,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(128.dp)
            .focusRequester(requester)
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .then(
                if (tileBox == null) Modifier else Modifier.onGloballyPositioned { tileBox.read(it) },
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(CircleShape)
                .border(3.dp, if (focused) Accent else Color.Transparent, CircleShape)
                .padding(if (focused) 6.dp else 3.dp),
            contentAlignment = Alignment.Center,
        ) {
            ChannelLogo(channel = channel, size = 84.dp)
        }

        Text(
            text = channel.name,
            color = Color.White,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
