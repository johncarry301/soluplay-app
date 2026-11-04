package com.soluplay.core

import com.soluplay.core.model.ChannelDetail
import com.soluplay.core.model.StreamLink
import com.soluplay.core.player.FailoverReason
import com.soluplay.core.player.PlaybackCommand
import com.soluplay.core.player.PlaybackFault
import com.soluplay.core.player.PlaybackMachine
import com.soluplay.core.player.PlaybackPhase
import com.soluplay.core.player.PlaybackRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guards, and the second chance a failing mirror gets.
 *
 * The machine is told about the world by events that arrive late: a picture
 * from a mirror the walk has already left, a ladder for a channel the viewer
 * has already closed, an error from a try at playing that is over. None of
 * those may change what is on the screen.
 *
 * The second chance is the web player's own: a network failure is re-read from
 * the same published address before that mirror is given up.
 */
class PlaybackGuardTest {

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

    private fun List<PlaybackCommand>.preparedUrl(): String? =
        filterIsInstance<PlaybackCommand.Prepare>().firstOrNull()?.url

    private fun List<PlaybackCommand>.preparedAttempt(): Long? =
        filterIsInstance<PlaybackCommand.Prepare>().firstOrNull()?.attempt

    private fun List<PlaybackCommand>.cancelledAttempts(): List<Long> =
        filterIsInstance<PlaybackCommand.Cancel>().map { it.attempt }

    private fun PlaybackMachine.play(detail: ChannelDetail): List<PlaybackCommand> {
        val session = beginSession(detail.id, detail.name, detail.logo)
        return start(detail, session)
    }

    @Test
    fun `a picture from a try that is over does not put the channel on air`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))
        val stale = first.preparedAttempt()!!

        // The first race is given up and the walk moves on.
        clock.advance(10_000)
        machine.onTick()
        assertEquals(4, machine.state.mirror)

        // The picture of the mirror that was given up arrives late.
        val commands = machine.onFirstFrame(stale)

        assertTrue("a stale picture changed the state", commands.isEmpty())
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
        assertTrue("a stale picture claimed the channel was live", !machine.state.onAir)
    }

    @Test
    fun `an error from a try that is over does not move the walk on`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))
        val stale = first.preparedAttempt()!!

        clock.advance(10_000)
        machine.onTick()
        assertEquals(4, machine.state.mirror)

        val commands = machine.onError(PlaybackFault.FATAL, stale)

        assertTrue("a stale error moved the walk on", commands.isEmpty())
        assertEquals(4, machine.state.mirror)
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
    }

    @Test
    fun `a stall from a try that is over does not start the grace window`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))
        val stale = first.preparedAttempt()!!
        val second = machine.onError(PlaybackFault.TRANSIENT, stale)

        // The second try comes on and proves itself.
        machine.onFirstFrame(second.preparedAttempt()!!)
        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)

        // A stall from the try that is over must not hold the picture.
        machine.onStall(stale)

        clock.advance(1_500)
        machine.onTick()

        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)
    }

    @Test
    fun `every try at playing is a new attempt`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))
        val second = machine.onError(PlaybackFault.TRANSIENT, first.preparedAttempt()!!)

        assertTrue("the attempt number was reused", second.preparedAttempt() != first.preparedAttempt())
        assertEquals(machine.currentAttempt, second.preparedAttempt())
    }

    @Test
    fun `a ladder for a session that is over is refused`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.beginSession("c1", "Sports One", "")
        // The viewer opens another channel before the first ladder arrives.
        machine.beginSession("c2", "Sports Two", "")

        val commands = machine.start(detail("https://a.test/1.m3u8"), first)

        assertTrue("a ladder from an old session was played", commands.isEmpty())
        assertEquals(PlaybackPhase.LOADING, machine.state.phase)
    }

    @Test
    fun `a ladder that arrives after the channel was closed is refused`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val session = machine.beginSession("c1", "Sports One", "")
        machine.stop()

        val commands = machine.start(detail("https://a.test/1.m3u8"), session)

        assertTrue("a ladder from a closed session was played", commands.isEmpty())
        assertEquals(PlaybackPhase.IDLE, machine.state.phase)
    }

    @Test
    fun `a network failure gives the same published mirror a second chance`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))
        val commands = machine.onError(PlaybackFault.TRANSIENT, first.preparedAttempt()!!)

        assertEquals("https://a.test/1.m3u8", commands.preparedUrl())
        assertEquals(1, machine.state.mirror)
        assertEquals(3, machine.state.mirrorCount)
        assertEquals("the mirror left the race", 3, machine.state.racing)
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
    }

    @Test
    fun `the second chances are the web player's own two`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))

        val second = machine.onError(PlaybackFault.TRANSIENT, first.preparedAttempt()!!)
        assertEquals("https://a.test/1.m3u8", second.preparedUrl())
        assertEquals(1, machine.state.mirror)

        val third = machine.onError(PlaybackFault.TRANSIENT, second.preparedAttempt()!!)
        assertEquals("https://a.test/1.m3u8", third.preparedUrl())
        assertEquals(1, machine.state.mirror)
    }

    @Test
    fun `the last try at a failing mirror gets the web player's three seconds`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))

        val second = machine.onError(PlaybackFault.TRANSIENT, first.preparedAttempt()!!)
        val third = machine.onError(PlaybackFault.TRANSIENT, second.preparedAttempt()!!)
        val last = machine.onError(PlaybackFault.TRANSIENT, third.preparedAttempt()!!)

        assertEquals("https://a.test/1.m3u8", last.preparedUrl())
        assertEquals(1, machine.state.mirror)

        // The recovery window is the web player's own.
        clock.advance(PlaybackRules.TRANSIENT_RECOVERY_MS - 1)
        assertTrue("the recovery window was cut short", machine.onTick().isEmpty())

        clock.advance(1)
        val commands = machine.onTick()

        assertEquals(
            "the failing mirror was not let go of",
            listOf(last.preparedAttempt()!!),
            commands.cancelledAttempts(),
        )
        assertEquals("the other mirrors of the race went on", 2, machine.state.racing)
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
    }

    @Test
    fun `a broken mirror is given up at once`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))
        val broken = first.preparedAttempt()!!
        val commands = machine.onError(PlaybackFault.FATAL, broken)

        assertEquals(listOf(broken), commands.cancelledAttempts())
        assertEquals("the broken mirror was still racing", 2, machine.state.racing)
        assertEquals(FailoverReason.PLAYER_ERROR, machine.state.failure)
    }

    @Test
    fun `a mirror that proves itself after a second chance is on air`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))
        val second = machine.onError(PlaybackFault.TRANSIENT, first.preparedAttempt()!!)

        machine.onFirstFrame(second.preparedAttempt()!!)

        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)
        assertEquals(1, machine.state.mirror)
    }

    @Test
    fun `a mirror that was on air and loses the network has to prove itself again`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val first = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))
        machine.onFirstFrame(first.preparedAttempt()!!)
        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)

        val second = machine.onError(PlaybackFault.TRANSIENT, first.preparedAttempt()!!)

        assertEquals("https://a.test/1.m3u8", second.preparedUrl())
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
        assertEquals(1, machine.state.mirror)

        // The mirror is given the budget of a first frame to prove itself.
        clock.advance(9_999)
        assertTrue("the mirror was given up early", machine.onTick().isEmpty())

        clock.advance(1)
        val commands = machine.onTick()

        assertTrue(
            "the mirror was not let go of",
            commands.any { it is PlaybackCommand.Cancel },
        )
        assertEquals(2, machine.state.racing)
        assertEquals(2, machine.state.mirror)
    }
}
