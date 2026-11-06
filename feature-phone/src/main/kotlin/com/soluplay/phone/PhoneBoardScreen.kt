package com.soluplay.phone

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.fixtures.boardSectionsOf
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.SiteFixtureCard
import com.soluplay.core.ui.fixtureClock

/**
 * The match board.
 *
 * The board is read in three parts: what is on now, what is still to come and
 * what is over. The site publishes the phase of every card, so the app never
 * decides for itself that a match is finished — the clock is only used for a
 * card that carries no phase at all.
 *
 * The cards themselves are the site's cards: the name in a box at the top, the
 * two sides' flags under it, and the countdown or the way in at the foot.
 */
@Composable
fun PhoneBoardScreen(
    fixtures: List<Fixture>,
    onPickFixture: (Fixture) -> Unit,
) {
    val sections = remember(fixtures) { boardSectionsOf(fixtures, System.currentTimeMillis()) }
    val clock = fixtureClock()

    if (sections.empty) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                text = Copy.noMatches(),
                color = Muted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 40.dp),
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (sections.live.isNotEmpty()) {
            item(key = "live-heading") { SectionHeading(Copy.liveNow()) }
            item(key = "live") {
                MatchGrid(fixtures = sections.live, clock = clock, onPick = onPickFixture)
            }
        }

        if (sections.upcoming.isNotEmpty()) {
            item(key = "upcoming-heading") { SectionHeading(Copy.comingUp()) }
            item(key = "upcoming") {
                MatchGrid(fixtures = sections.upcoming, clock = clock, onPick = onPickFixture)
            }
        }

        if (sections.finished.isNotEmpty()) {
            item(key = "finished-heading") { SectionHeading(Copy.over()) }
            item(key = "finished") {
                MatchGrid(fixtures = sections.finished, clock = clock, onPick = onPickFixture)
            }
        }
    }
}

/** The name of one part of the board. */
@Composable
private fun SectionHeading(label: String) {
    Text(
        text = label,
        color = Color.White,
        fontSize = 14.sp,
        fontWeight = FontWeight.Black,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/**
 * The cards of one part of the board, two to a line.
 *
 * The site's board puts two cards to a line on a phone, and a card with no
 * partner keeps its half of the line, so the two lines below it stay in step.
 */
@Composable
private fun MatchGrid(
    fixtures: List<Fixture>,
    clock: State<Long>,
    onPick: (Fixture) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        fixtures.chunked(2).forEach { pair ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pair.forEach { fixture ->
                    SiteFixtureCard(
                        fixture = fixture,
                        clock = clock,
                        onPick = { onPick(fixture) },
                        modifier = Modifier.weight(1f),
                    )
                }

                if (pair.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}
