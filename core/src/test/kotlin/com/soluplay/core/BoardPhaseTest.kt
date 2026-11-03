package com.soluplay.core

import com.soluplay.core.fixtures.BoardPhase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The phase of a match, as the board publishes it.
 *
 * The site decides when a match is on and when it is over; the app reads that
 * answer rather than working it out from the clock a second time.
 */
class BoardPhaseTest {

    @Test
    fun `the three phases the board publishes are read`() {
        assertEquals(BoardPhase.UPCOMING, BoardPhase.of("upcoming"))
        assertEquals(BoardPhase.LIVE, BoardPhase.of("live"))
        assertEquals(BoardPhase.FINISHED, BoardPhase.of("finished"))
    }

    @Test
    fun `a phase is read whatever the board wrote`() {
        assertEquals(BoardPhase.LIVE, BoardPhase.of("LIVE"))
        assertEquals(BoardPhase.LIVE, BoardPhase.of(" live "))
    }

    @Test
    fun `a phase the app does not know is not guessed at`() {
        assertEquals(BoardPhase.UNKNOWN, BoardPhase.of(""))
        assertEquals(BoardPhase.UNKNOWN, BoardPhase.of("half-time"))
    }
}
