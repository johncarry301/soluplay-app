package com.soluplay.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.fixtures.BoardPhase
import com.soluplay.core.fixtures.MatchClock
import com.soluplay.core.model.Fixture
import kotlinx.coroutines.delay

/**
 * The matches, on one line, above the guide.
 *
 * The board is empty most of the time, and an empty strip is worse than no
 * strip — so it renders nothing at all when there is nothing on it.
 *
 * [scale] carries the surface's type size: the phone reads it at 1, the TV at
 * the size its own rule demands. A card that can be opened says so by being
 * tappable, and [onPick] is told which one was.
 */
@Composable
fun MatchStrip(
    fixtures: List<Fixture>,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    onPick: (Fixture) -> Unit = {},
) {
    if (fixtures.isEmpty()) return

    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1000)
        }
    }

    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy((10 * scale).dp),
    ) {
        fixtures.forEach { fixture ->
            MatchCard(
                fixture = fixture,
                nowMillis = now,
                scale = scale,
                onClick = { onPick(fixture) },
            )
        }
    }
}

@Composable
private fun MatchCard(
    fixture: Fixture,
    nowMillis: Long,
    scale: Float,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width((196 * scale).dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel)
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = fixture.competition.ifBlank { fixture.title },
            color = Muted,
            fontSize = (11 * scale).sp,
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
                scale = scale,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "vs",
                color = Muted,
                fontSize = (11 * scale).sp,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Team(
                logoUrl = fixture.teamBLogo,
                name = fixture.teamB,
                scale = scale,
                modifier = Modifier.weight(1f),
            )
        }

        Text(
            text = countdownLine(fixture, nowMillis),
            color = Accent,
            fontSize = (11 * scale).sp,
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
    scale: Float,
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
            size = (30 * scale).dp,
        )
        Text(
            text = name,
            color = Muted,
            fontSize = (10 * scale).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * What the card says under the teams.
 *
 * The board publishes the phase of every match, and that is the answer the
 * card gives: the site decides when a match is on and when it is over, from
 * the kick-off and the length it was given. The clock is only the countdown
 * to a match that is still to come.
 */
@Composable
private fun countdownLine(fixture: Fixture, nowMillis: Long): String {
    when (BoardPhase.of(fixture.phase)) {
        BoardPhase.LIVE -> return text("খেলা চলছে", "The match is on")
        BoardPhase.FINISHED -> return text("খেলা শেষ হয়েছে", "The match is over")
        BoardPhase.UPCOMING, BoardPhase.UNKNOWN -> Unit
    }

    val kickOff = MatchClock.parseKickOff(fixture.startsAt)
    if (kickOff == null) return ""

    val remaining = MatchClock.remaining(kickOff, nowMillis)
        ?: return text("খেলা শুরু হয়েছে", "The match has started")

    val clock = "%02d : %02d : %02d".format(remaining.hours, remaining.minutes, remaining.seconds)
    return text("$clock মিনিট পর খেলা শুরু হবে", "starts in $clock")
}
