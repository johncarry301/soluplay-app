package com.soluplay.core.fixtures

import com.soluplay.core.model.Fixture

/**
 * The board, in the three parts a viewer reads it in.
 *
 * A match that is on now comes first, a match that is still to come next, and
 * a match that is over last. The three lists are read from the board the
 * server published, and the app never decides that a match is over: the phase
 * on the card is the answer, and the kick-off clock is only used for a card
 * that carries no phase at all.
 */
data class BoardSections(
    /** Matches that are on now. */
    val live: List<Fixture> = emptyList(),
    /** Matches that have not started yet, soonest first. */
    val upcoming: List<Fixture> = emptyList(),
    /** Matches that are over, most recent first. */
    val finished: List<Fixture> = emptyList(),
) {
    /** True when there is nothing on the board at all. */
    val empty: Boolean get() = live.isEmpty() && upcoming.isEmpty() && finished.isEmpty()
}

/**
 * The board, read in three parts.
 *
 * The published phase decides where a card belongs. A card with no phase is
 * placed by its kick-off: a kick-off in the future is still to come, and one
 * in the past is over. A card with neither a phase nor a readable kick-off is
 * kept with the ones still to come, because a card that cannot be placed is
 * better shown than dropped.
 */
fun boardSectionsOf(fixtures: List<Fixture>, nowMillis: Long): BoardSections {
    val live = mutableListOf<Fixture>()
    val upcoming = mutableListOf<Fixture>()
    val finished = mutableListOf<Fixture>()

    fixtures.forEach { fixture ->
        when (BoardPhase.of(fixture.phase)) {
            BoardPhase.LIVE -> live += fixture

            BoardPhase.FINISHED -> finished += fixture

            BoardPhase.UPCOMING -> upcoming += fixture

            BoardPhase.UNKNOWN -> {
                val kickOff = MatchClock.parseKickOff(fixture.startsAt)
                if (kickOff != null && kickOff <= nowMillis) finished += fixture else upcoming += fixture
            }
        }
    }

    return BoardSections(
        live = live,
        upcoming = upcoming.sortedBy { kickOffOf(it) ?: Long.MAX_VALUE },
        finished = finished.sortedByDescending { kickOffOf(it) ?: Long.MIN_VALUE },
    )
}

/** The kick-off of a card, or null when it carries none that can be read. */
private fun kickOffOf(fixture: Fixture): Long? = MatchClock.parseKickOff(fixture.startsAt)
