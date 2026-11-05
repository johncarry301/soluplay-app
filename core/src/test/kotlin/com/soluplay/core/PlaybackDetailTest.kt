package com.soluplay.core

import com.soluplay.core.model.ChannelDetail
import com.soluplay.core.model.StreamLink
import com.soluplay.core.player.FailoverReason
import com.soluplay.core.player.PlaybackCommand
import com.soluplay.core.player.PlaybackDetails
import com.soluplay.core.player.PlaybackFault
import com.soluplay.core.player.PlaybackMachine
import com.soluplay.core.player.PlaybackPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The player's own reason for a failure.
 *
 * A failure that says nothing is a failure the viewer cannot act on and a
 * device test that cannot be read: "it did not play" is not a report. So the
 * reason a player gave is carried through to the screen, and the reason a
 * YouTube player never showed a picture names the player that was waited on.
 */
class PlaybackDetailTest {

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

    private fun PlaybackMachine.play(detail: ChannelDetail): List<PlaybackCommand> {
        val session = beginSession(detail.id, detail.name, detail.logo)
        return start(detail, session)
    }

    @Test
    fun `a player's own reason reaches the screen`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(detail("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        val attempt = commands.filterIsInstance<PlaybackCommand.Prepare>().single().attempt

        machine.onError(PlaybackFault.FATAL, attempt, PlaybackDetails.youTubeError(150))

        assertEquals("youtube.error.150", machine.state.detail)
        assertEquals(FailoverReason.PLAYER_ERROR, machine.state.failure)
    }

    @Test
    fun `a youtube mirror that shows no picture names the player`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        machine.play(detail("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))

        clock.advance(40_000)
        machine.onTick()

        assertEquals(PlaybackPhase.EXHAUSTED, machine.state.phase)
        assertEquals(PlaybackDetails.YOUTUBE_NO_PICTURE, machine.state.detail)
    }

    @Test
    fun `a feed the app fetches itself gives no player reason`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        machine.play(detail("https://example.test/live/stream.ts"))

        clock.advance(10_000)
        machine.onTick()

        assertEquals("", machine.state.detail)
    }

    @Test
    fun `the reason is forgotten when the next channel is opened`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(detail("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        val attempt = commands.filterIsInstance<PlaybackCommand.Prepare>().single().attempt
        machine.onError(PlaybackFault.FATAL, attempt, PlaybackDetails.youTubeError(150))

        machine.beginSession("c2", "Sports Two", "")

        assertEquals("", machine.state.detail)
    }

    @Test
    fun `a failure that says nothing leaves no reason behind`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(detail("https://example.test/live/stream.m3u8"))
        val attempt = commands.filterIsInstance<PlaybackCommand.Prepare>().single().attempt

        machine.onError(PlaybackFault.FATAL, attempt)

        assertEquals("", machine.state.detail)
        assertEquals(FailoverReason.PLAYER_ERROR, machine.state.failure)
    }

    @Test
    fun `a certificate refusal reaches the screen`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(detail("https://example.test/live/stream.m3u8"))
        val attempt = commands.filterIsInstance<PlaybackCommand.Prepare>().single().attempt

        machine.onError(PlaybackFault.FATAL, attempt, PlaybackDetails.TLS_EXPIRED)

        assertEquals("tls-expired", machine.state.detail)
        assertEquals(FailoverReason.PLAYER_ERROR, machine.state.failure)
    }

    @Test
    fun `a certificate refusal moves the walk on at once`() {
        val clock = Clock()
        val machine = PlaybackMachine(now = clock::now)
        val commands = machine.play(
            detail(
                "https://example.test/live/one.m3u8",
                "https://example.test/live/two.m3u8",
            ),
        )
        val attempt = commands.filterIsInstance<PlaybackCommand.Prepare>().first().attempt

        val after = machine.onError(PlaybackFault.FATAL, attempt, PlaybackDetails.TLS_ISSUER)

        assertTrue("the broken mirror was not given up", after.any { it is PlaybackCommand.Cancel })
    }
}
