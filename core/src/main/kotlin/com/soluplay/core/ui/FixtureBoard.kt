package com.soluplay.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.soluplay.core.fixtures.BoardPhase
import com.soluplay.core.fixtures.MatchClock
import com.soluplay.core.fixtures.Remaining
import com.soluplay.core.fixtures.TeamFlags
import com.soluplay.core.fixtures.playableChoices
import com.soluplay.core.model.Fixture
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The match board, as the site draws it.
 *
 * The board is the site's own: the section's name, the cards in a row, and the
 * phone's second column as a stage the cards take turns on. A card says which
 * two sides are playing, whether the match is on, and — when it is not — how
 * long is left until it begins.
 */
@Composable
fun SiteFixtureBoard(
    fixtures: List<Fixture>,
    onPick: (Fixture) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (fixtures.isEmpty()) return

    val clock = fixtureClock()
    val turns = fixtures.size - 1
    var slide by remember { mutableIntStateOf(0) }

    // The phone's second column is a stage: every card after the first takes a
    // turn on it, sliding in from the right over the card that was there, and
    // the turn after the last one belongs to the second card again.
    LaunchedEffect(fixtures) {
        if (turns < 2) return@LaunchedEffect
        while (true) {
            delay(SLIDE_INTERVAL_MILLIS)
            slide = (slide + 1) % turns
        }
    }

    val onStage = if (turns > 0) slide % turns else 0

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = Copy.sportsSection(),
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Black,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // The first card keeps its place and is never covered by anything.
            SiteFixtureCard(
                fixture = fixtures[0],
                clock = clock,
                onPick = { onPick(fixtures[0]) },
                modifier = Modifier.weight(1f),
            )

            if (turns > 0) {
                FixtureStage(
                    fixtures = fixtures.drop(1),
                    onStage = onStage,
                    clock = clock,
                    onPick = onPick,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * The phone's second column: one place the cards take turns on.
 *
 * The cards share one cell, so the cell is as tall as the tallest of them and
 * every card covers the one it replaces down to the last pixel.
 */
@Composable
private fun FixtureStage(
    fixtures: List<Fixture>,
    onStage: Int,
    clock: State<Long>,
    onPick: (Fixture) -> Unit,
    modifier: Modifier = Modifier,
) {
    var width by remember { mutableIntStateOf(0) }

    Box(
        modifier = modifier
            .clipToBounds()
            .onSizeChanged { width = it.width },
    ) {
        fixtures.forEachIndexed { index, fixture ->
            val slide by animateFloatAsState(
                targetValue = if (index == onStage) 0f else 1f,
                animationSpec = tween(durationMillis = SLIDE_ANIMATION_MILLIS, easing = FastOutSlowInEasing),
                label = "stage",
            )

            SiteFixtureCard(
                fixture = fixture,
                clock = clock,
                onPick = { onPick(fixture) },
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (index == onStage) 1f else 0f)
                    .offset { IntOffset((slide * width).roundToInt(), 0) },
            )
        }
    }
}

/**
 * One match, as the site's card draws it.
 *
 * The card is the site's card: the match's name in a box at the top with the
 * sport's mark beside it, the two sides' flags at the ends of the row under
 * it, the badge or the kick-off time in the middle, and the way in — or the
 * clock — in the bar at the foot.
 */
@Composable
fun SiteFixtureCard(
    fixture: Fixture,
    clock: State<Long>,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val live = BoardPhase.of(fixture.phase) == BoardPhase.LIVE
    val title = fixture.title.trim()
    val competition = fixture.competition.trim()
    val kickOff = MatchClock.parseKickOff(fixture.startsAt)
    val kickoff = kickOff?.let { MatchClock.kickoffInBdt(it) }

    // A live match whose channels are all shut says so on its face: a red
    // badge would promise a stream the card cannot open.
    val noOffer = live && playableChoices(fixture).isEmpty()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(CardTop, CardMiddle, CardBottom)))
            .clickable { onPick() }
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // The match's name, in the box that sits at the top of the card. The
        // sport's mark is at the edge of the box, so the name keeps the whole
        // of the middle to itself and stays centred on it.
        if (title.isNotEmpty() || competition.isNotEmpty()) {
            val named = title.ifBlank { competition }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .border(1.dp, Slate800.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                SportMark(
                    sport = fixture.sport,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    text = named.uppercase(Locale.US),
                    color = Slate200,
                    fontSize = titleSizeOf(named),
                    letterSpacing = titleTrackingOf(named),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }
        }

        // The competition only keeps a line of its own when the title did not
        // already say it.
        if (title.isNotEmpty() && competition.isNotEmpty()) {
            Text(
                text = competition.uppercase(Locale.US),
                color = Slate500,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.18.em,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 12.dp),
            )
        }

        // The two sides, spread across the card: the flags take the two ends
        // and the middle says when the match begins — or, once it has begun,
        // that it is running now.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TeamColumn(name = fixture.teamA, logoUrl = fixture.teamALogo)

            if (live) {
                LiveBadge()
            } else {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 2.dp),
                ) {
                    Text(
                        text = Copy.startsAt(),
                        color = Slate500,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.12.em,
                        maxLines = 1,
                    )
                    DayWordText(
                        kickOffMillis = kickOff,
                        fallback = kickoff?.date.orEmpty(),
                        clock = clock,
                    )
                    Text(
                        text = kickoff?.time.orEmpty(),
                        color = Slate400,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }

            TeamColumn(name = fixture.teamB, logoUrl = fixture.teamBLogo)
        }

        // The foot of the card: the countdown for a match that has not started,
        // and the way in for one that has. It sits directly under the teams,
        // so nothing opens up between them.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 22.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.3f))
                .border(1.dp, Slate800.copy(alpha = 0.8f), RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (live) {
                if (noOffer) {
                    Text(
                        text = Copy.nothingToOpen(),
                        color = Amber.copy(alpha = 0.9f),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = EmeraldText,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = Copy.watchLive(),
                        color = EmeraldText,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.05.em,
                        maxLines = 1,
                    )
                }
            } else {
                CountdownText(kickOffMillis = kickOff, clock = clock)
            }
        }
    }
}

/** One side of a match: its flag and its name. */
@Composable
private fun TeamColumn(name: String, logoUrl: String) {
    Column(
        modifier = Modifier.width(38.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TeamFlag(name = name, logoUrl = logoUrl)
        Text(
            text = name.uppercase(Locale.US),
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.05.em,
            textAlign = TextAlign.Center,
            lineHeight = 10.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.heightIn(min = 20.dp),
        )
    }
}

/**
 * The team's flag, drawn as a panel.
 *
 * The flag comes first because recognition is the point — a national side is
 * recognised from its name, so an admin never has to find, crop and upload a
 * picture of a flag. A logo they entered is the next thing to try and the
 * initials are the last, so a card is never blank, and a picture that fails to
 * load costs the card its picture and nothing else.
 *
 * A country flag fills the panel edge to edge: the flag sheet and the wrapper
 * are both 4:3, so the picture covers the wrapper and no white margin is left
 * showing around it. A crest is a different shape, so it is contained rather
 * than cropped.
 */
@Composable
private fun TeamFlag(name: String, logoUrl: String) {
    val flagUrl = remember(name) { TeamFlags.teamFlagUrl(name) }

    val picture by produceState<Pair<ImageBitmap?, Boolean>?>(null, flagUrl, logoUrl) {
        val flag = flagUrl?.let { LogoLoader.load(it) }
        val logo = if (flag == null && logoUrl.isNotBlank()) LogoLoader.load(logoUrl) else null
        value = (flag ?: logo) to (flag != null)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(LogoPlateBackground.copy(alpha = 0.95f))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val (bitmap, isFlag) = picture ?: (null to false)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = if (isFlag) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(if (isFlag) 0.dp else 2.dp),
            )
        } else {
            Text(
                text = initialsOf(name),
                color = Slate700,
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
            )
        }
    }
}

/**
 * The sport's mark, small and quiet beside the match's name.
 *
 * A goal for football, and a bat with the stumps for cricket — drawn here
 * because the icon set has no cricket mark to pick.
 */
@Composable
private fun SportMark(sport: String, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(14.dp)) {
        val stroke = Stroke(
            width = 1.8.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )

        fun point(x: Float, y: Float) = Offset(x / 24f * size.width, y / 24f * size.height)

        if (sport.lowercase(Locale.US) == "cricket") {
            drawPath(
                path = Path().apply {
                    moveTo(point(4.6f, 20.4f).x, point(4.6f, 20.4f).y)
                    lineTo(point(13.4f, 6.6f).x, point(13.4f, 6.6f).y)
                },
                color = IndigoMark,
                style = stroke,
            )
            drawPath(
                path = Path().apply {
                    moveTo(point(13.4f, 6.6f).x, point(13.4f, 6.6f).y)
                    lineTo(point(16.2f, 2.9f).x, point(16.2f, 2.9f).y)
                },
                color = IndigoMark,
                style = stroke,
            )
            drawPath(
                path = Path().apply {
                    moveTo(point(14.5f, 5.1f).x, point(14.5f, 5.1f).y)
                    lineTo(point(16.5f, 6.4f).x, point(16.5f, 6.4f).y)
                },
                color = IndigoMark,
                style = stroke,
            )
            listOf(16.6f, 19.6f, 22.6f).forEach { x ->
                drawPath(
                    path = Path().apply {
                        moveTo(point(x, 12.4f).x, point(x, 12.4f).y)
                        lineTo(point(x, 20.6f).x, point(x, 20.6f).y)
                    },
                    color = IndigoMark,
                    style = stroke,
                )
            }
            drawPath(
                path = Path().apply {
                    moveTo(point(16.6f, 11.9f).x, point(16.6f, 11.9f).y)
                    lineTo(point(22.6f, 11.9f).x, point(22.6f, 11.9f).y)
                },
                color = IndigoMark,
                style = stroke,
            )
        } else {
            drawCircle(
                color = IndigoMark,
                radius = size.minDimension / 2f - stroke.width,
                style = stroke,
            )
            val radius = size.minDimension * 0.22f
            val pentagon = Path()
            repeat(5) { index ->
                val angle = Math.toRadians((-90.0 + index * 72.0))
                val x = center.x + radius * cos(angle).toFloat()
                val y = center.y + radius * sin(angle).toFloat()
                if (index == 0) pentagon.moveTo(x, y) else pentagon.lineTo(x, y)
            }
            pentagon.close()
            drawPath(path = pentagon, color = IndigoMark, style = stroke)
        }
    }
}

/** The red badge that says the match is running now. */
@Composable
private fun LiveBadge() {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(LiveBadge)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(color = Color.White)
        Text(
            text = Copy.liveNowBadge(),
            color = Color.White,
            fontSize = 8.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.05.em,
            maxLines = 1,
        )
    }
}

/** A dot that breathes, the way the site's live badge's dot does. */
@Composable
private fun PulsingDot(color: Color, size: Dp = 6.dp) {
    val pulse by rememberInfiniteTransition(label = "badge").animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = pulse)),
    )
}

/**
 * The title's type size, stepped down as the name gets longer.
 *
 * A card is half the page wide on a phone, so a long name has to give up its
 * size before it gives up its place: the smaller the name, the smaller the
 * type, until the whole thing fits the box instead of being cut off.
 */
private fun titleSizeOf(title: String): TextUnit = when {
    title.length <= 18 -> 11.sp
    title.length <= 26 -> 10.sp
    title.length <= 34 -> 9.sp
    title.length <= 44 -> 8.sp
    else -> 7.sp
}

private fun titleTrackingOf(title: String): TextUnit = when {
    title.length <= 18 -> 0.12.em
    title.length <= 26 -> 0.10.em
    title.length <= 34 -> 0.08.em
    title.length <= 44 -> 0.06.em
    else -> 0.04.em
}

/** The first two letters of a name, for a card that has no picture to show. */
private fun initialsOf(name: String): String =
    name.trim()
        .split(Regex("\\s+"))
        .take(2)
        .map { it.firstOrNull()?.toString().orEmpty() }
        .joinToString("")
        .uppercase(Locale.US)
        .ifBlank { "?" }

/**
 * The clock the countdowns are read from.
 *
 * One clock for a whole board, and the cards read it only where a time is
 * printed, so a second that passes redraws a line of text and not a card.
 */
@Composable
fun fixtureClock(): State<Long> {
    val clock = remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            clock.value = System.currentTimeMillis()
            delay(1000)
        }
    }
    return clock
}

/**
 * The day a match falls on, in words where words help.
 *
 * The clock is read here and not in the card, so a day that turns redraws this
 * line and nothing else on the card.
 */
@Composable
private fun DayWordText(kickOffMillis: Long?, fallback: String, clock: State<Long>) {
    val now = clock.value
    val day = kickOffMillis?.let { MatchClock.dayWord(it, now) }.orEmpty()

    Text(
        text = day.ifBlank { fallback },
        color = Slate200,
        fontSize = 9.sp,
        fontWeight = FontWeight.Black,
        maxLines = 1,
    )
}

/**
 * The clock left until the kick-off.
 *
 * A match whose time has come shows a clock at zero rather than an empty line,
 * which is what the site's card shows.
 */
@Composable
private fun CountdownText(kickOffMillis: Long?, clock: State<Long>) {
    val now = clock.value
    val remaining = kickOffMillis?.let { MatchClock.remaining(it, now) } ?: Remaining(0, 0, 0, 0)

    Text(
        text = MatchClock.countdownLabel(remaining),
        color = Amber.copy(alpha = 0.9f),
        fontSize = 10.sp,
        fontWeight = FontWeight.Black,
        style = TextStyle(fontFeatureSettings = "tnum"),
        maxLines = 1,
    )
}

/** How long a card waits before it takes its turn on the stage. */
private const val SLIDE_INTERVAL_MILLIS = 5_000L

/** How long the slide from one card to the next takes. */
private const val SLIDE_ANIMATION_MILLIS = 700
