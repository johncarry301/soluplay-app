package com.soluplay.tv

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.fixtures.boardSectionsOf
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.LogoPlate
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.matchCountdown
import com.soluplay.core.ui.rememberNowMillis
import kotlinx.coroutines.launch

/**
 * The match board on the television.
 *
 * The board is read in three parts: what is on now, what is still to come and
 * what is over. The site publishes the phase of every card, so the app never
 * decides for itself that a match is finished — the clock is only used for a
 * card that carries no phase at all.
 *
 * The cards of a part sit in one row, so the remote walks along the part and
 * down to the next one.
 */
@Composable
fun TvBoardScreen(
    fixtures: List<Fixture>,
    chrome: TvChrome,
    onPickFixture: (Fixture) -> Unit,
) {
    val now = rememberNowMillis()

    // The parts are read once per set of cards: a part that is rebuilt every
    // second would take the focus with it.
    val sections = remember(fixtures) { boardSectionsOf(fixtures, System.currentTimeMillis()) }
    val parts = listOf(sections.live, sections.upcoming, sections.finished)

    val requesters = remember(sections) {
        parts.map { part -> part.map { FocusRequester() } }
    }
    val rows = remember(requesters, chrome) { listOf(chrome.requesters) + requesters }

    val focus = remember { TvFocus() }
    val scope = rememberCoroutineScope()

    val verticalScroll = rememberScrollState()
    val window = remember { DrawnBox() }
    val partBoxes = remember(sections) { parts.map { DrawnBox() } }

    /** Brings the part the focus is on into the window. */
    fun revealPart(row: Int) {
        if (row == 0) {
            if (verticalScroll.value > 0) scope.launch { verticalScroll.animateScrollTo(0) }
            return
        }
        val box = partBoxes.getOrNull(row - 1) ?: return
        val delta = box.overflowVertically(window.top, window.bottom)
        if (delta != 0f) scope.launch { verticalScroll.animateScrollBy(delta) }
    }

    LaunchedEffect(sections) {
        val target = rows.getOrNull(focus.row)?.getOrNull(focus.column)
            ?: chrome.requesters.firstOrNull()
        target?.askForFocus(scope)
    }

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> {
                            focus.move(rows, 0, 1, scope)
                            true
                        }
                        Key.DirectionLeft -> {
                            focus.move(rows, 0, -1, scope)
                            true
                        }
                        Key.DirectionDown -> {
                            focus.move(rows, 1, 0, scope)
                            revealPart(focus.row)
                            true
                        }
                        Key.DirectionUp -> {
                            focus.move(rows, -1, 0, scope)
                            revealPart(focus.row)
                            true
                        }
                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar -> {
                            if (focus.row == 0) {
                                chrome.press(focus.column)
                            } else {
                                val card = parts.getOrNull(focus.row - 1)?.getOrNull(focus.column)
                                if (card != null) onPickFixture(card)
                            }
                            true
                        }
                        else -> false
                    }
                },
        ) {
            TvTopBar(chrome)

            if (sections.empty) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 120.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = Copy.noMatches(),
                        color = Muted,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                return@Column
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(verticalScroll)
                    .padding(horizontal = 48.dp)
                    .padding(bottom = 48.dp)
                    .onGloballyPositioned { window.read(it) },
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BoardPart(
                    label = Copy.liveNow(),
                    cards = sections.live,
                    requesters = requesters.getOrNull(0).orEmpty(),
                    box = partBoxes.getOrNull(0) ?: DrawnBox(),
                    nowMillis = now,
                    onFocused = { index ->
                        focus.place(1, index, rows, scope)
                        revealPart(1)
                    },
                )

                BoardPart(
                    label = Copy.comingUp(),
                    cards = sections.upcoming,
                    requesters = requesters.getOrNull(1).orEmpty(),
                    box = partBoxes.getOrNull(1) ?: DrawnBox(),
                    nowMillis = now,
                    onFocused = { index ->
                        focus.place(2, index, rows, scope)
                        revealPart(2)
                    },
                )

                BoardPart(
                    label = Copy.over(),
                    cards = sections.finished,
                    requesters = requesters.getOrNull(2).orEmpty(),
                    box = partBoxes.getOrNull(2) ?: DrawnBox(),
                    nowMillis = now,
                    onFocused = { index ->
                        focus.place(3, index, rows, scope)
                        revealPart(3)
                    },
                )
            }
        }
    }
}

/** One part of the board: its name and the cards in it. */
@Composable
private fun BoardPart(
    label: String,
    cards: List<Fixture>,
    requesters: List<FocusRequester>,
    box: DrawnBox,
    nowMillis: Long,
    onFocused: (Int) -> Unit,
) {
    if (cards.isEmpty()) return

    Column(
        modifier = Modifier.onGloballyPositioned { box.read(it) },
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            color = Accent,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            cards.forEachIndexed { index, fixture ->
                val requester = requesters.getOrNull(index) ?: return@forEachIndexed
                TvMatchCard(
                    fixture = fixture,
                    nowMillis = nowMillis,
                    requester = requester,
                    onFocused = { onFocused(index) },
                )
            }
        }
    }
}

/** One match on the board, walked with the remote. */
@Composable
private fun TvMatchCard(
    fixture: Fixture,
    nowMillis: Long,
    requester: FocusRequester,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(300.dp)
            .focusRequester(requester)
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .border(3.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = fixture.competition.ifBlank { fixture.title },
            color = Muted,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TvTeam(
                logoUrl = fixture.teamALogo,
                name = fixture.teamA,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "vs",
                color = Muted,
                fontSize = 16.sp,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            TvTeam(
                logoUrl = fixture.teamBLogo,
                name = fixture.teamB,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = matchCountdown(fixture, nowMillis),
            color = Accent,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TvTeam(
    logoUrl: String,
    name: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LogoPlate(
            url = logoUrl,
            fallbackText = name,
            size = 52.dp,
            modifier = Modifier.size(52.dp),
        )
        Text(
            text = name,
            color = Color.White,
            fontSize = 17.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
