package com.soluplay.core

import com.soluplay.core.model.ChannelDetail
import com.soluplay.core.model.StreamLink
import com.soluplay.core.player.FailoverReason
import com.soluplay.core.player.PlaybackCommand
import com.soluplay.core.player.PlaybackFault
import com.soluplay.core.player.PlaybackMachine
import com.soluplay.core.player.PlaybackPhase
import com.soluplay.core.player.PlaybackRules
import com.soluplay.core.player.StreamKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The playback rules.
 *
 * The app fails over at the same moment the web player does and for the same
 * reasons, so these are the web's own numbers and the web's own order: the
 * published ladder is raced as it came, a mirror that has not proved a
 * picture in ten seconds is given up, and a frozen picture is given the grace
 * window and then one more chance before the mirror is.
 */
class PlaybackMachineTest {

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

    /** The addresses that were asked for, in the order they were asked for. */
    private fun List<PlaybackCommand>.preparedUrls(): List<String> =
        filterIsInstance<PlaybackCommand.Prepare>().map { it.url }

    /** The attempt of the first prepare that was asked for, if one was. */
    private fun List<PlaybackCommand>.preparedAttempt(): Long? =
        (firstOrNull() as? PlaybackCommand.Prepare)?.attempt

    /** The attempt of the first mirror of the race. */
    private fun List<PlaybackCommand>.firstAttempt(): Long =
        filterIsInstance<PlaybackCommand.Prepare>().first().attempt

    /** Opens a channel and hands its ladder to the machine. */
    private fun PlaybackMachine.play(detail: ChannelDetail): List<PlaybackCommand> {
        val session = beginSession(detail.id, detail.name, detail.logo)
        return start(detail, session)
    }

    @Test
    fun `the deadlines are the web player's own`() {
        assertEquals(10_000L, PlaybackRules.FIRST_FRAME_DEADLINE_MS)
        assertEquals(1_200L, PlaybackRules.STALL_GRACE_MS)
        assertEquals(3, PlaybackRules.RACE_SIZE)
    }

    @Test
    fun `the race begins at the first published mirrors`() {
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
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
        assertEquals(1, machine.state.mirror)
        assertEquals(4, machine.state.mirrorCount)
        assertEquals("Sports One", machine.state.channelName)
    }

    @Test
    fun `the published order is the order that is raced`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        // The site published this order. The app does not get a vote.
        val published = detail("https://b.test/2.m3u8", "https://a.test/1.m3u8")
        val commands = machine.play(published)

        assertEquals(
            listOf("https://b.test/2.m3u8", "https://a.test/1.m3u8"),
            commands.preparedUrls(),
        )
    }

    @Test
    fun `a mirror that proves a picture is on air`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        machine.play(detail("https://a.test/1.m3u8"))

        machine.onFirstFrame(machine.currentAttempt)

        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)
        assertTrue(machine.state.onAir)
    }

    @Test
    fun `a mirror that sends no picture is given up at the deadline`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))

        clock.advance(9_999)
        assertTrue("the deadline was early", machine.onTick().isEmpty())

        clock.advance(1)
        val commands = machine.onTick()

        assertEquals(listOf("https://d.test/4.m3u8"), commands.preparedUrls())
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
        assertEquals(4, machine.state.mirror)
        assertEquals(FailoverReason.NO_PICTURE, machine.state.failure)
    }

    @Test
    fun `the last mirror failing ends the walk`() {
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
    fun `a channel with no mirror is exhausted at once`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail())

        assertEquals(listOf(PlaybackCommand.Stop), commands)
        assertEquals(PlaybackPhase.EXHAUSTED, machine.state.phase)
        assertEquals(FailoverReason.NO_MIRROR, machine.state.failure)
    }

    @Test
    fun `a player failure moves the walk on when the race is gone`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))

        val attempts = commands.filterIsInstance<PlaybackCommand.Prepare>().map { it.attempt }
        assertEquals(3, attempts.size)

        val first = machine.onError(PlaybackFault.FATAL, attempts[0])
        assertTrue("the walk moved on with mirrors still racing", first.preparedUrls().isEmpty())
        assertEquals(2, machine.state.racing)

        machine.onError(PlaybackFault.FATAL, attempts[1])
        val moved = machine.onError(PlaybackFault.FATAL, attempts[2])

        assertEquals(listOf("https://d.test/4.m3u8"), moved.preparedUrls())
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
        assertEquals(4, machine.state.mirror)
        assertEquals(FailoverReason.PLAYER_ERROR, machine.state.failure)
    }

    @Test
    fun `a frozen picture is held through the grace window`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))
        machine.onFirstFrame(commands.firstAttempt())

        // The picture is there and the clock is moving.
        clock.advance(1_000)
        machine.onPosition(1_000)

        // Then the clock stops.
        clock.advance(1_000)
        machine.onPosition(1_000)

        clock.advance(1_199)
        assertTrue("the grace window was cut short", machine.onTick().isEmpty())
        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)
    }

    @Test
    fun `a frozen picture that does not come back moves the walk on`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))
        machine.onFirstFrame(commands.firstAttempt())

        clock.advance(1_000)
        machine.onPosition(1_000)
        clock.advance(1_000)
        machine.onPosition(1_000)
        clock.advance(1_200)
        machine.onTick()

        assertEquals(PlaybackPhase.RECOVERING, machine.state.phase)
        assertEquals(1, machine.state.mirror)

        // The mirror has the same budget a first frame gets.
        clock.advance(9_999)
        assertTrue("the mirror was given up early", machine.onTick().isEmpty())

        clock.advance(1)
        val moved = machine.onTick()

        assertEquals(
            listOf(
                "https://b.test/2.m3u8",
                "https://c.test/3.m3u8",
                "https://d.test/4.m3u8",
            ),
            moved.preparedUrls(),
        )
        assertEquals(FailoverReason.FROZEN, machine.state.failure)
    }

    @Test
    fun `a picture that comes back keeps the mirror`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))
        machine.onFirstFrame(commands.firstAttempt())

        clock.advance(1_000)
        machine.onPosition(1_000)
        clock.advance(1_000)
        machine.onPosition(1_000)
        clock.advance(1_200)
        machine.onTick()
        assertEquals(PlaybackPhase.RECOVERING, machine.state.phase)

        // The feed comes back.
        clock.advance(500)
        machine.onPosition(2_500)

        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)
        assertEquals(1, machine.state.mirror)

        // And the walk is not restarted by the recovery.
        clock.advance(20_000)
        assertTrue(machine.onTick().isEmpty())
        assertEquals(PlaybackPhase.ON_AIR, machine.state.phase)
    }

    @Test
    fun `a ladder that cannot be read is not dressed up as an outage`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        machine.beginSession("c1", "Sports One", "")
        assertEquals(PlaybackPhase.LOADING, machine.state.phase)

        machine.fail(FailoverReason.LADDER_UNREADABLE)

        assertEquals(PlaybackPhase.FAILED, machine.state.phase)
        assertEquals(FailoverReason.LADDER_UNREADABLE, machine.state.failure)
    }

    @Test
    fun `each mirror is prepared with the kind it has to be played as`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://sm-monirul.xyz/@monirul_Islam_SM/stream.php?id=sony_max_hd&e=.m3u8",
            "https://example.test/live/stream.ts",
            "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
        ))

        assertEquals(
            listOf(StreamKind.HLS, StreamKind.MPEG_TS, StreamKind.YOUTUBE),
            commands.filterIsInstance<PlaybackCommand.Prepare>().map { it.kind },
        )
    }

    @Test
    fun `a playlist behind a php page is prepared as a playlist`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://sm-monirul.xyz/@monirul_Islam_SM/stream.php?id=sony_max_hd&e=.m3u8",
        ))

        val prepare = commands.filterIsInstance<PlaybackCommand.Prepare>().single()
        assertEquals(StreamKind.HLS, prepare.kind)
    }

    @Test
    fun `a youtube mirror is given longer to prove a picture`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        machine.play(detail("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))

        clock.advance(10_000)
        assertTrue("the youtube mirror was given up too early", machine.onTick().isEmpty())
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)

        clock.advance(29_999)
        assertTrue("the youtube mirror was given up too early", machine.onTick().isEmpty())

        clock.advance(1)
        machine.onTick()

        assertEquals(PlaybackPhase.EXHAUSTED, machine.state.phase)
        assertEquals(FailoverReason.NO_PICTURE, machine.state.failure)
    }

    @Test
    fun `a feed the app fetches itself is still given ten seconds`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        machine.play(detail("https://example.test/live/stream.ts"))

        clock.advance(9_999)
        assertTrue("the mirror was given up early", machine.onTick().isEmpty())

        clock.advance(1)
        machine.onTick()

        assertEquals(PlaybackPhase.EXHAUSTED, machine.state.phase)
    }
}
