package com.soluplay.core.fixtures

import com.soluplay.core.model.Fixture
import com.soluplay.core.model.FixtureChoice

/**
 * One thing a match card can be opened on: a catalogue channel, or the
 * match's own broadcast link.
 *
 * The link is published beside the channels and is offered the same way, so a
 * card's rule — one thing opens, several ask, none says so — does not have to
 * know which kind it is counting.
 */
sealed interface FixtureOption {

    /** A catalogue channel, played from its own ladder. */
    data class Channel(val choice: FixtureChoice) : FixtureOption

    /** The match's own address, played as a one-mirror ladder. */
    data class MatchLink(val url: String) : FixtureOption
}

/**
 * What a match card can be opened on.
 *
 * The board publishes the channels a card can be watched on, each with the
 * state that says whether it can be opened right now, and possibly one address
 * for the match itself. Only a card with something openable is worth a tap,
 * and a card with more than one thing to choose from has to ask before it
 * opens.
 */
sealed interface FixtureTarget {

    /** One thing to open: the tap goes straight there. */
    data class Open(val option: FixtureOption) : FixtureTarget

    /** More than one thing to open: the viewer chooses. */
    data class Choose(val options: List<FixtureOption>) : FixtureTarget

    /** Nothing on the card can be opened. */
    data object None : FixtureTarget
}

/**
 * The choices on a card that the app can open.
 *
 * A choice is openable when the board says it is ready and it names a direct
 * channel: the board publishes the web route for it, but the app opens the
 * channel itself, so the route is not what decides.
 */
fun playableChoices(fixture: Fixture): List<FixtureChoice> =
    fixture.choices.filter { choice ->
        choice.state == READY_STATE && choice.sourceType == DIRECT_SOURCE
    }

/**
 * Everything on the card the app can open, in the order the admin set: the
 * ready direct channels, then the match's own broadcast link.
 *
 * The link is last because it belongs to the match rather than to the guide,
 * and the channels are what a viewer already knows how to pick from.
 */
fun openableOptions(fixture: Fixture): List<FixtureOption> {
    val options = playableChoices(fixture).map { FixtureOption.Channel(it) }
    val link = fixture.directUrl.trim()
    return if (link.isEmpty()) options else options + FixtureOption.MatchLink(link)
}

/**
 * Where a tap on a match card goes.
 *
 * One openable option is opened. Several are offered. None is a card that
 * cannot be watched from here, and the screen says so rather than opening
 * nothing.
 */
fun fixtureTarget(fixture: Fixture): FixtureTarget {
    val options = openableOptions(fixture)
    return when {
        options.isEmpty() -> FixtureTarget.None
        options.size == 1 -> FixtureTarget.Open(options.first())
        else -> FixtureTarget.Choose(options)
    }
}

/** The state the board gives a channel the catalogue is still showing. */
const val READY_STATE = "ready"

/** The source the board publishes today: the channel's own origin. */
const val DIRECT_SOURCE = "direct"
