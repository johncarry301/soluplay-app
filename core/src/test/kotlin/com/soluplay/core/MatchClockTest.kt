package com.soluplay.core

import com.soluplay.core.fixtures.MatchClock
import com.soluplay.core.fixtures.Remaining
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

    @Test
    fun `the kick-off is printed in Bangladesh time`() {
        // 18:00 in utc is half past midnight the next day in Bangladesh.
        val printed = MatchClock.kickoffInBdt(1_793_815_200_000L)

        assertEquals("NOV 5, 2026", printed.date)
        assertEquals("12:00 AM BDT", printed.time)
    }

    @Test
    fun `a kick-off in the afternoon is printed in the afternoon`() {
        // 12:00 in utc is 18:00 in Bangladesh.
        val printed = MatchClock.kickoffInBdt(1_793_793_600_000L)

        assertEquals("06:00 PM BDT", printed.time)
    }

    @Test
    fun `the day a match falls on is named where words help`() {
        // Noon in utc is 18:00 in Bangladesh, so the day does not change
        // underneath the test.
        val kickOff = 1_793_793_600_000L

        assertEquals("TODAY", MatchClock.dayWord(kickOff, kickOff - 60_000))
        assertEquals("TOMORROW", MatchClock.dayWord(kickOff + 86_400_000, kickOff))
        assertEquals("", MatchClock.dayWord(kickOff + 5 * 86_400_000, kickOff))
    }

    @Test
    fun `the clock counts days while the match is more than a day away`() {
        val printed = MatchClock.countdownLabel(Remaining(days = 2, hours = 3, minutes = 4, seconds = 5))

        assertEquals("2d 03h 04m", printed)
    }

    @Test
    fun `the clock counts seconds in the last day`() {
        val printed = MatchClock.countdownLabel(Remaining(days = 0, hours = 1, minutes = 2, seconds = 3))

        assertEquals("01h 02m 03s", printed)
    }
}
