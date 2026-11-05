package com.soluplay.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.input.key.onKeyEvent
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
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.Rail
import com.soluplay.core.browse.railsOf
import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.MatchStrip
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.OutageBanner
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.StarColour
import com.soluplay.core.ui.outageMessage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * How long the remote has to rest on a channel before it starts playing.
 *
 * A channel that is played the moment the remote touches it would start and
 * stop with every arrow press, and the television would be talking to the
 * network more than the viewer is watching it.
 */
private const val PREVIEW_SETTLE_MS = 700L

/**
 * The guide on the television.
 *
 * Rows of channels, walked with the remote. The guide is never re-ranked
 * here — the rails and their order are the server's. A channel opens the
 * watch screen, and a match opens the channel the board says it can be
 * watched on.
 *
 * The bar at the top is the first row of the walk, so the four places, the
 * language and the refresh are always one press away.
 */
@Composable
fun TvGuideScreen(
    snapshot: BrowseSnapshot,
    chrome: TvChrome,
    marked: Set<String>,
    onWatch: (Channel) -> Unit,
    onPickFixture: (Fixture) -> Unit,
    onPreview: (Channel) -> Unit,
) {
    val rails = remember(snapshot.channels) { railsOf(snapshot.channels) }

    val focus = remember { TvFocus() }
    val scope = rememberCoroutineScope()

    val railRequesters = remember(rails) { rails.map { rail -> rail.channels.map { FocusRequester() } } }

    val verticalScroll = rememberScrollState()
    val window = remember { DrawnBox() }
    val railBoxes = remember(rails) { rails.map { DrawnBox() } }

    val rows = remember(railRequesters, chrome) {
        listOf(chrome.requesters) + railRequesters
    }

    fun requesterAt(row: Int, index: Int): FocusRequester? {
        if (row == 0) return chrome.requesters.getOrNull(index)
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
            // The bar lives in the top bar, so the guide goes back to its
            // beginning.
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
        val target = requesterAt(focus.row, focus.column)
            ?: railRequesters.firstOrNull()?.firstOrNull()
            ?: chrome.requesters.firstOrNull()
        target?.askForFocus(scope)
    }

    fun move(dRow: Int, dColumn: Int) {
        focus.move(rows, dRow, dColumn, scope)
        revealRail(focus.row)
    }

    fun channelAt(row: Int, index: Int): Channel? {
        if (row == 0) return null
        return rails.getOrNull(row - 1)?.channels?.getOrNull(index)
    }

    // The channel the remote is standing on plays in the corner, once it has
    // rested there for a moment.
    val focusedChannel = channelAt(focus.row, focus.column)
    LaunchedEffect(focusedChannel?.id) {
        val channel = focusedChannel ?: return@LaunchedEffect
        delay(PREVIEW_SETTLE_MS)
        onPreview(channel)
    }

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> { move(0, 1); true }
                        Key.DirectionLeft -> { move(0, -1); true }
                        Key.DirectionDown -> { move(1, 0); true }
                        Key.DirectionUp -> { move(-1, 0); true }
                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar -> {
                            if (focus.row == 0) {
                                chrome.press(focus.column)
                            } else {
                                val channel = channelAt(focus.row, focus.column)
                                if (channel != null) onWatch(channel)
                            }
                            true
                        }
                        else -> false
                    }
                },
        ) {
            TvTopBar(chrome)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .padding(horizontal = 48.dp)
                    .padding(bottom = 260.dp)
                    .onGloballyPositioned { window.read(it) },
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
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
                    MatchStrip(
                        fixtures = snapshot.fixtures,
                        scale = 1.7f,
                        onPick = onPickFixture,
                    )
                }

                rails.forEachIndexed { rowIndex, rail ->
                    RailRow(
                        rail = rail,
                        requesters = railRequesters.getOrNull(rowIndex).orEmpty(),
                        box = railBoxes.getOrNull(rowIndex) ?: DrawnBox(),
                        marked = marked,
                        onFocused = { index ->
                            focus.place(rowIndex + 1, index, rows, scope)
                            revealRail(rowIndex + 1)
                        },
                    )
                }

                if (rails.isEmpty()) {
                    Text(text = Copy.noCopy(), color = Muted, fontSize = 18.sp)
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
internal class DrawnBox {
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

/** One rail of the guide, walked with the remote. */
@Composable
private fun RailRow(
    rail: Rail,
    requesters: List<FocusRequester>,
    box: DrawnBox,
    marked: Set<String>,
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
                    marked = channel.id in marked,
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

/** One channel in the grid, with the star that says it is on the viewer's list. */
@Composable
private fun TvChannelTile(
    channel: Channel,
    marked: Boolean,
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
        Box {
            ChannelLogo(
                channel = channel,
                size = 96.dp,
                border = if (focused) BorderStroke(3.dp, Accent) else null,
            )
            if (marked) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = Copy.savedChannel(),
                    tint = StarColour,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Background)
                        .padding(2.dp),
                )
            }
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
