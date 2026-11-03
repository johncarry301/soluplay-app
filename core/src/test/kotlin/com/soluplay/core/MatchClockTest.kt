package com.soluplay.core

import com.soluplay.core.fixtures.MatchClock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The clock on a match card.
 */
class MatchClockTest {

    @Test
    fun `a kick-off in utc is read`() {
        assertEquals(1_793_815_200_000L, MatchClock.parseKickOff("2026-11-04T18:00:00.000Z"))
    }

    @Test
    fun `a kick-off with an offset is read`() {
        assertEquals(1_793_793_600_000L, MatchClock.parseKickOff("2026-11-04T18:00:00+06:00"))
    }

    @Test
    fun `a kick-off behind utc is read behind utc`() {
        // -05:30 means five and a half hours behind, so the moment is later
        // than the same clock reading in utc.
        assertEquals(1_793_835_000_000L, MatchClock.parseKickOff("2026-11-04T18:00:00-05:30"))
    }

    @Test
    fun `a kick-off behind utc without a colon is read behind utc`() {
        assertEquals(1_793_835_000_000L, MatchClock.parseKickOff("2026-11-04T18:00:00-0530"))
    }

    @Test
    fun `a kick-off ahead of utc without a colon is read ahead of utc`() {
        assertEquals(1_793_795_400_000L, MatchClock.parseKickOff("2026-11-04T18:00:00+0530"))
    }

    @Test
    fun `a kick-off without a fraction is read`() {
        assertEquals(1_793_815_200_000L, MatchClock.parseKickOff("2026-11-04T18:00:00Z"))
    }

    @Test
    fun `a kick-off given as epoch millis is read`() {
        assertEquals(1_793_815_200_000L, MatchClock.parseKickOff("1793815200000"))
    }

    @Test
    fun `anything the board cannot be asked about reads as nothing`() {
        assertNull(MatchClock.parseKickOff(""))
        assertNull(MatchClock.parseKickOff("later"))
        assertNull(MatchClock.parseKickOff("04/11/2026 18:00"))
    }

    @Test
    fun `the clock left is read in hours, minutes and seconds`() {
        val kickOff = 1_793_815_200_000L

        val remaining = MatchClock.remaining(kickOff, kickOff - 3_723_000)

        assertEquals(1, remaining?.hours)
        assertEquals(2, remaining?.minutes)
        assertEquals(3, remaining?.seconds)
    }

    @Test
    fun `a match that has started has no clock left`() {
        val kickOff = 1_793_815_200_000L

        assertNull(MatchClock.remaining(kickOff, kickOff))
        assertNull(MatchClock.remaining(kickOff, kickOff + 1))
    }
}
