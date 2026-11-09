package com.soluplay.core

import com.soluplay.core.model.ChannelDetail
import com.soluplay.core.model.StreamLink
import com.soluplay.core.player.FailoverReason
import com.soluplay.core.player.PlaybackCommand
import com.soluplay.core.player.PlaybackMachine
import com.soluplay.core.player.PlaybackPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The mirror the viewer asked for by hand.
 *
 * The published order is the site's and stays the site's: the viewer is given
 * the next published mirror, and the one that was on the screen is not handed
 * back in the same playback.
 */
class PlaybackSkipTest {

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

    private fun List<PlaybackCommand>.cancelledAttempts(): List<Long> =
        filterIsInstance<PlaybackCommand.Cancel>().map { it.attempt }

    private fun PlaybackMachine.play(detail: ChannelDetail): List<PlaybackCommand> {
        val session = beginSession(detail.id, detail.name, detail.logo)
        return start(detail, session)
    }

    @Test
    fun `the next published mirror is the one that is tried`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
            "https://d.test/4.m3u8",
        ))

        val attempts = commands.filterIsInstance<PlaybackCommand.Prepare>().map { it.attempt }
        machine.onFirstFrame(attempts[0])

        val skipped = machine.skipToNextMirror()

        assertEquals(
            listOf(
                "https://b.test/2.m3u8",
                "https://c.test/3.m3u8",
                "https://d.test/4.m3u8",
            ),
            skipped.preparedUrls(),
        )
    }

    @Test
    fun `the mirror that was on the screen is let go of`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
        ))

        val attempts = commands.filterIsInstance<PlaybackCommand.Prepare>().map { it.attempt }
        machine.onFirstFrame(attempts[0])

        val skipped = machine.skipToNextMirror()

        assertTrue(
            "the mirror on the screen was not let go of",
            skipped.cancelledAttempts().contains(attempts[0]),
        )
    }

    @Test
    fun `a skipped mirror is not handed back in the same playback`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
        ))

        val attempts = commands.filterIsInstance<PlaybackCommand.Prepare>().map { it.attempt }
        machine.onFirstFrame(attempts[0])

        val skipped = machine.skipToNextMirror()
        assertEquals(listOf("https://b.test/2.m3u8"), skipped.preparedUrls())

        // The one mirror that was left is given up, and the walk ends rather
        // than going back to the mirror the viewer left.
        clock.advance(10_000)
        val ended = machine.onTick()

        assertTrue("the walk did not stop", ended.last() is PlaybackCommand.Stop)
        assertEquals(PlaybackPhase.EXHAUSTED, machine.state.phase)
    }

    @Test
    fun `the screen says why the picture changed hands`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
        ))

        val attempts = commands.filterIsInstance<PlaybackCommand.Prepare>().map { it.attempt }
        machine.onFirstFrame(attempts[0])

        machine.skipToNextMirror()

        assertEquals(FailoverReason.SKIPPED, machine.state.failure)
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
    }

    @Test
    fun `a channel with one mirror is tried again instead of being given up`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail("https://a.test/1.m3u8"))
        val attempts = commands.filterIsInstance<PlaybackCommand.Prepare>().map { it.attempt }
        machine.onFirstFrame(attempts[0])

        val skipped = machine.skipToNextMirror()

        assertEquals(
            listOf("https://a.test/1.m3u8"),
            skipped.preparedUrls(),
        )
        assertEquals(PlaybackPhase.TUNING, machine.state.phase)
    }

    @Test
    fun `the walk goes back to the earlier mirrors one at a time`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
            "https://c.test/3.m3u8",
        ))

        val attempts = commands.filterIsInstance<PlaybackCommand.Prepare>().map { it.attempt }
        machine.onFirstFrame(attempts[0])

        // The next published mirrors are raced, as they always are.
        val second = machine.skipToNextMirror()
        assertEquals(
            listOf("https://b.test/2.m3u8", "https://c.test/3.m3u8"),
            second.preparedUrls(),
        )

        // The mirror that won the race is given up and the last published
        // mirror is tried.
        machine.onFirstFrame(second.filterIsInstance<PlaybackCommand.Prepare>()[0].attempt)
        val third = machine.skipToNextMirror()
        assertEquals(listOf("https://c.test/3.m3u8"), third.preparedUrls())

        // Nothing is left ahead, so the walk goes back to the first published
        // mirror and the mirrors are tried one after another from then on.
        machine.onFirstFrame(third.filterIsInstance<PlaybackCommand.Prepare>()[0].attempt)
        val wrapped = machine.skipToNextMirror()
        assertEquals(listOf("https://a.test/1.m3u8"), wrapped.preparedUrls())

        machine.onFirstFrame(wrapped.filterIsInstance<PlaybackCommand.Prepare>()[0].attempt)
        val next = machine.skipToNextMirror()
        assertEquals(listOf("https://b.test/2.m3u8"), next.preparedUrls())
    }

    @Test
    fun `the mirror that was left is tried last of all`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        val commands = machine.play(detail(
            "https://a.test/1.m3u8",
            "https://b.test/2.m3u8",
        ))

        val attempts = commands.filterIsInstance<PlaybackCommand.Prepare>()
        machine.onFirstFrame(attempts[0].attempt)

        // The second published mirror is tried next.
        val second = machine.skipToNextMirror()
        assertEquals(listOf("https://b.test/2.m3u8"), second.preparedUrls())

        machine.onFirstFrame(second.filterIsInstance<PlaybackCommand.Prepare>()[0].attempt)

        // Nothing is left ahead, so the walk goes back to the first published
        // mirror and not to the one the viewer has just left.
        val wrapped = machine.skipToNextMirror()
        assertEquals(listOf("https://a.test/1.m3u8"), wrapped.preparedUrls())

        machine.onFirstFrame(wrapped.filterIsInstance<PlaybackCommand.Prepare>()[0].attempt)

        // The mirror that was left is the one that is tried last of all.
        val last = machine.skipToNextMirror()
        assertEquals(listOf("https://b.test/2.m3u8"), last.preparedUrls())
    }

    @Test
    fun `nothing is skipped when nothing is playing`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)

        assertTrue(machine.skipToNextMirror().isEmpty())
    }
}
