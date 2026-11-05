package com.soluplay.core

import com.soluplay.core.fixtures.boardSectionsOf
import com.soluplay.core.model.Fixture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The board, in three parts.
 *
 * The site says which phase a match is in, and the app reads that rather than
 * working it out again: a match the site has not published a phase for is
 * placed by its kick-off.
 */
class BoardSectionsTest {

    private val now = 1_700_000_000_000L

    private fun fixture(
        id: String,
        phase: String = "",
        startsAt: String = "2023-11-14T22:13:20.000Z",
    ) = Fixture(
        id = id,
        title = "Match $id",
        teamA = "Bangladesh",
        teamB = "India",
        startsAt = startsAt,
        phase = phase,
    )

    @Test
    fun `an empty board has nothing in it`() {
        val sections = boardSectionsOf(emptyList(), now)

        assertTrue(sections.empty)
    }

    @Test
    fun `the three phases are read as the site published them`() {
        val sections = boardSectionsOf(
            listOf(
                fixture("live", phase = "live"),
                fixture("next", phase = "upcoming"),
                fixture("done", phase = "finished"),
            ),
            now,
        )

        assertEquals(listOf("live"), sections.live.map { it.id })
        assertEquals(listOf("next"), sections.upcoming.map { it.id })
        assertEquals(listOf("done"), sections.finished.map { it.id })
    }

    @Test
    fun `the phase is read whichever case the site published it in`() {
        val sections = boardSectionsOf(
            listOf(fixture("live", phase = "LIVE")),
            now,
        )

        assertEquals(listOf("live"), sections.live.map { it.id })
    }

    @Test
    fun `a card with no phase is placed by its kick-off`() {
        val sections = boardSectionsOf(
            listOf(
                fixture("past", startsAt = "2023-11-14T20:00:00.000Z"),
                fixture("future", startsAt = "2023-11-15T20:00:00.000Z"),
            ),
            now,
        )

        assertEquals(listOf("past"), sections.finished.map { it.id })
        assertEquals(listOf("future"), sections.upcoming.map { it.id })
    }

    @Test
    fun `a card with a phase the app does not know is placed by its kick-off`() {
        val sections = boardSectionsOf(
            listOf(
                fixture("odd", phase = "postponed", startsAt = "2023-11-15T20:00:00.000Z"),
            ),
            now,
        )

        assertEquals(listOf("odd"), sections.upcoming.map { it.id })
        assertTrue(sections.finished.isEmpty())
    }

    @Test
    fun `a card with neither a phase nor a readable kick-off is kept`() {
        val sections = boardSectionsOf(
            listOf(fixture("broken", phase = "", startsAt = "not a date")),
            now,
        )

        assertEquals(listOf("broken"), sections.upcoming.map { it.id })
        assertFalse(sections.empty)
    }

    @Test
    fun `the matches still to come are the soonest first`() {
        val sections = boardSectionsOf(
            listOf(
                fixture("late", phase = "upcoming", startsAt = "2023-11-16T20:00:00.000Z"),
                fixture("soon", phase = "upcoming", startsAt = "2023-11-15T20:00:00.000Z"),
            ),
            now,
        )

        assertEquals(listOf("soon", "late"), sections.upcoming.map { it.id })
    }

    @Test
    fun `the matches that are over are the most recent first`() {
        val sections = boardSectionsOf(
            listOf(
                fixture("older", phase = "finished", startsAt = "2023-11-12T20:00:00.000Z"),
                fixture("newer", phase = "finished", startsAt = "2023-11-14T20:00:00.000Z"),
            ),
            now,
        )

        assertEquals(listOf("newer", "older"), sections.finished.map { it.id })
    }

    @Test
    fun `the board is not empty when there is a match in it`() {
        val sections = boardSectionsOf(listOf(fixture("live", phase = "live")), now)

        assertFalse(sections.empty)
    }
}
