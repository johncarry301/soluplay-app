package com.soluplay.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.fixtures.boardSectionsOf
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.LogoPlate
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.matchCountdown
import com.soluplay.core.ui.rememberNowMillis

/**
 * The match board.
 *
 * The board is read in three parts: what is on now, what is still to come and
 * what is over. The site publishes the phase of every card, so the app never
 * decides for itself that a match is finished — the clock is only used for a
 * card that carries no phase at all.
 */
@Composable
fun PhoneBoardScreen(
    fixtures: List<Fixture>,
    onPickFixture: (Fixture) -> Unit,
) {
    val now = rememberNowMillis()
    val sections = remember(fixtures, now) { boardSectionsOf(fixtures, now) }

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
            item(key = "live") { SectionHeading(Copy.liveNow()) }
            items(sections.live.size, key = { "live:${sections.live[it].id}" }) { index ->
                BoardCard(
                    fixture = sections.live[index],
                    nowMillis = now,
                    onPick = { onPickFixture(sections.live[index]) },
                )
            }
        }

        if (sections.upcoming.isNotEmpty()) {
            item(key = "upcoming") { SectionHeading(Copy.comingUp()) }
            items(sections.upcoming.size, key = { "upcoming:${sections.upcoming[it].id}" }) { index ->
                BoardCard(
                    fixture = sections.upcoming[index],
                    nowMillis = now,
                    onPick = { onPickFixture(sections.upcoming[index]) },
                )
            }
        }

        if (sections.finished.isNotEmpty()) {
            item(key = "finished") { SectionHeading(Copy.over()) }
            items(sections.finished.size, key = { "finished:${sections.finished[it].id}" }) { index ->
                BoardCard(
                    fixture = sections.finished[index],
                    nowMillis = now,
                    onPick = { onPickFixture(sections.finished[index]) },
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(label: String) {
    Text(
        text = label,
        color = Accent,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(top = 10.dp),
    )
}

/**
 * One match on the board.
 *
 * The card says which competition it is from, which two sides are playing and
 * how long it is until the match starts. Tapping it opens the channel the
 * board says the match can be watched on.
 */
@Composable
private fun BoardCard(
    fixture: Fixture,
    nowMillis: Long,
    onPick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .clickable { onPick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = fixture.competition.ifBlank { fixture.title },
            color = Muted,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Team(
                logoUrl = fixture.teamALogo,
                name = fixture.teamA,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "vs",
                color = Muted,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Team(
                logoUrl = fixture.teamBLogo,
                name = fixture.teamB,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = matchCountdown(fixture, nowMillis),
            color = Accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun Team(
    logoUrl: String,
    name: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LogoPlate(
            url = logoUrl,
            fallbackText = name,
            size = 34.dp,
            modifier = Modifier.size(34.dp),
        )
        Text(
            text = name,
            color = Color.White,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
