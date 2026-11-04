package com.soluplay.core

import com.soluplay.core.model.ChannelDetail
import com.soluplay.core.model.StreamLink
import com.soluplay.core.player.FailoverReason
import com.soluplay.core.player.PlaybackCommand
import com.soluplay.core.player.PlaybackFault
import com.soluplay.core.player.PlaybackMachine
import com.soluplay.core.player.PlaybackPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The race.
 *
 * The first published mirrors are tuned together and the first of them to put
 * a picture on the screen keeps it. The ones that lost are let go of at once,
 * and the walk goes on to the next published mirrors only when the whole batch
 * has failed.
 *
 * The published order is never changed. The race only decides how many of the
 * published mirrors are in flight at the same moment.
 */
class PlaybackRaceTest {

    private class Clock(var moment: Long = 0L) {
        fun advance(ms: Long) {
            moment += ms
        }

        fun now(): Long = moment
    }

    private fun detail(vararg urls: String): ChannelDetail = ChannelDetail(
        id = "c1",
        name = "Sports One",
        logo = "https://logos.test/one.png",
        streams = urls.mapIndexed { index, url ->
            StreamLink(id = "s$index", url = url, priority = index + 1)
        },
    )

    private fun List<PlaybackCommand>.preparedUrls(): List<String> =
        filterIsInstance<PlaybackCommand.Prepare>().map { it.url }

    private fun List<PlaybackCommand>.preparedAttempts(): List<Long> =
        filterIsInstance<PlaybackCommand.Prepare>().map { it.attempt }

    private fun List<PlaybackCommand>.cancelledAttempts(): List<Long> =
        filterIsInstance<PlaybackCommand.Cancel>().map { it.attempt }

    private fun List<PlaybackCommand>.selectedAttempt(): Long? =
        filterIsInstance<PlaybackCommand.Select>().firstOrNull()?.attempt

    private fun PlaybackMachine.play(detail: ChannelDetail): List<PlaybackCommand> {
        val session = beginSession(detail.id, detail.name, detail.logo)
        return start(detail, session)
    }

    @Test
    fun `the first three published mirrors are tuned together`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))

        assertEquals(
            listOf(
                "https://a.test/1.m3u8",
                "https://b.test/2.m3u8",
                "https://c.test/3.m3u8",
            ),
            commands.preparedUrls(),
        )
        assertEquals("the fourth mirror was tuned too early", 3, machine.state.racing)
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
        assertEquals(1, machine.state.mirror)
        assertEquals(4, machine.state.mirrorCount)
    }

    @Test
    fun `the mirror that proves a picture first keeps it`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))

        // The second mirror is the first to send a picture.
        val winner = commands.preparedAttempts()[1]
        val selection = machine.onFirstFrame(winner)

        assertEquals(winner, selection.selectedAttempt())
        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)
        assertTrue(machine.state.onAir)
        assertEquals("the winner is not the mirror on screen", 2, machine.state.mirror)
        assertEquals("the race is still running", 0, machine.state.racing)
    }

    @Test
    fun `the mirrors that lost the race are let go of at once`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))

        val attempts = commands.preparedAttempts()
        val selection = machine.onFirstFrame(attempts[1])

        assertEquals(
            "a loser was left holding its player",
            listOf(attempts[0], attempts[2]),
            selection.cancelledAttempts(),
        )
        assertEquals(attempts[1], selection.selectedAttempt())
    }

    @Test
    fun `a picture from a mirror that lost the race changes nothing`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))

        val attempts = commands.preparedAttempts()
        machine.onFirstFrame(attempts[1])

        val late = machine.onFirstFrame(attempts[0])

        assertTrue("a stale picture changed the state", late.isEmpty())
        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)
        assertEquals(2, machine.state.mirror)
    }

    @Test
    fun `the whole batch failing moves to the next three mirrors`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
            "https://e.test/5.m3u8",
            "https://f.test/6.m3u8",
            "https://g.test/7.m3u8",
        ))

        clock.advance(9_999)
        assertTrue("the deadline was early", machine.onTick().isEmpty())

        clock.advance(1)
        val commands = machine.onTick()

        assertEquals(
            listOf(
                "https://d.test/4.m3u8",
                "https://e.test/5.m3u8",
                "https://f.test/6.m3u8",
            ),
            commands.preparedUrls(),
        )
        assertEquals(3, machine.state.racing)
        assertEquals("the walk left the published order", 4, machine.state.mirror)
        assertEquals(FailoverReason.NO_PICTURE, machine.state.failure)
    }

    @Test
    fun `the mirrors that are left are tried together`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
            "https://e.test/5.m3u8",
        ))

        // The first batch of three is given up, and the two that are left are
        // tried together.
        clock.advance(10_000)
        val commands = machine.onTick()

        assertEquals(
            listOf("https://d.test/4.m3u8", "https://e.test/5.m3u8"),
            commands.preparedUrls(),
        )
        assertEquals(2, machine.state.racing)
        assertEquals(4, machine.state.mirror)
    }

    @Test
    fun `no mirror left ends the walk`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        machine.play(detail("https://a.test/1.m3u8"))

        clock.advance(10_000)
        val commands = machine.onTick()

        assertTrue("the walk did not stop", commands.last() is PlaybackCommand.Stop)
        assertEquals(PlaybackPhase.EXHAUSTED, machine.state.phase)
        assertEquals(FailoverReason.NO_PICTURE, machine.state.failure)
    }

    @Test
    fun `a broken mirror leaves the race and the others go on`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))

        val attempts = commands.preparedAttempts()
        val broken = machine.onError(PlaybackFault.FATAL, attempts[0])

        assertEquals(listOf(attempts[0]), broken.cancelledAttempts())
        assertTrue("a new mirror was tuned", broken.preparedUrls().isEmpty())
        assertEquals("the race went on without the broken mirror", 2, machine.state.racing)
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
        assertEquals(FailoverReason.PLAYER_ERROR, machine.state.failure)
    }

    @Test
    fun `the walk moves on when the whole race is broken`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))

        val attempts = commands.preparedAttempts()
        machine.onError(PlaybackFault.FATAL, attempts[0])
        machine.onError(PlaybackFault.FATAL, attempts[1])

        val moved = machine.onError(PlaybackFault.FATAL, attempts[2])

        assertEquals(listOf("https://d.test/4.m3u8"), moved.preparedUrls())
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
        assertEquals(4, machine.state.mirror)
    }

    @Test
    fun `the mirrors that lost are raced again when the picture dies`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))

        val attempts = commands.preparedAttempts()
        machine.onFirstFrame(attempts[0])
        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)

        // The picture stops moving and does not come back.
        clock.advance(1_000)
        machine.onPosition(1_000)
        clock.advance(1_000)
        machine.onPosition(1_000)
        clock.advance(1_200)
        machine.onTick()
        assertEquals(PlaybackPhase.RECOVERING, machine.state.phase)

        clock.advance(10_000)
        val moved = machine.onTick()

        assertEquals(
            "the published order was not kept",
            listOf(
                "https://b.test/2.m3u8",
                "https://c.test/3.m3u8",
                "https://d.test/4.m3u8",
            ),
            moved.preparedUrls(),
        )
        assertEquals(FailoverReason.FROZEN, machine.state.failure)
    }
}
