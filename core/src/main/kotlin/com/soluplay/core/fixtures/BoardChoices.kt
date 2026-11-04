package com.soluplay.core.fixtures

import com.soluplay.core.model.Fixture
import com.soluplay.core.model.FixtureChoice

/**
 * What a match card can be opened on.
 *
 * The board publishes the channels a card can be watched on, each with the
 * state that says whether it can be opened right now. Only a card with
 * something openable is worth a tap, and a card with more than one thing to
 * choose from has to ask before it opens.
 */
sealed interface FixtureTarget {

    /** One thing to open: the tap goes straight there. */
    data class Open(val choice: FixtureChoice) : FixtureTarget

    /** More than one thing to open: the viewer chooses. */
    data class Choose(val choices: List<FixtureChoice>) : FixtureTarget

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
 * Where a tap on a match card goes.
 *
 * One openable choice is opened. Several are offered. None is a card that
 * cannot be watched from here, and the screen says so rather than opening
 * nothing.
 */
fun fixtureTarget(fixture: Fixture): FixtureTarget {
    val choices = playableChoices(fixture)
    return when {
        choices.isEmpty() -> FixtureTarget.None
        choices.size == 1 -> FixtureTarget.Open(choices.first())
        else -> FixtureTarget.Choose(choices)
    }
}

/** The state the board gives a channel the catalogue is still showing. */
const val READY_STATE = "ready"

/** The source the board publishes today: the channel's own origin. */
const val DIRECT_SOURCE = "direct"
