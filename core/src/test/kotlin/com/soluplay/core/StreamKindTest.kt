package com.soluplay.core

import com.soluplay.core.player.StreamKind
import com.soluplay.core.player.StreamKinds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a published mirror's address is read.
 *
 * The site publishes an address and nothing else, so the app has to read the
 * kind of a source out of the address itself — the same way the web player
 * does. These are the shapes the live catalogue actually carries: a playlist
 * behind a `.php` page, a raw transport stream, and a YouTube address.
 */
class StreamKindTest {

    @Test
    fun `a playlist behind a php page is a playlist`() {
        val url = "https://sm-monirul.xyz/@monirul_Islam_SM/stream.php?id=sony_max_hd&e=.m3u8"

        assertEquals(StreamKind.HLS, StreamKinds.kindOf(url))
    }

    @Test
    fun `an ordinary playlist is a playlist`() {
        assertEquals(
            StreamKind.HLS,
            StreamKinds.kindOf("https://example.test/live/channel/playlist.m3u8"),
        )
    }

    @Test
    fun `a raw transport stream is a transport stream`() {
        assertEquals(
            StreamKind.MPEG_TS,
            StreamKinds.kindOf("https://example.test/live/channel/stream.ts"),
        )
    }

    @Test
    fun `a transport stream over plain http is a transport stream`() {
        assertEquals(
            StreamKind.MPEG_TS,
            StreamKinds.kindOf("http://rgkkw.live/live/stream.ts"),
        )
    }

    @Test
    fun `a transport stream with a query is a transport stream`() {
        assertEquals(
            StreamKind.MPEG_TS,
            StreamKinds.kindOf("https://example.test/feed.ts?token=abc&x=1"),
        )
    }

    @Test
    fun `a youtube address is a youtube source`() {
        assertEquals(
            StreamKind.YOUTUBE,
            StreamKinds.kindOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ"),
        )
        assertEquals(
            StreamKind.YOUTUBE,
            StreamKinds.kindOf("https://youtu.be/dQw4w9WgXcQ"),
        )
    }

    @Test
    fun `a youtube live address is a youtube source`() {
        assertEquals(
            StreamKind.YOUTUBE,
            StreamKinds.kindOf("https://www.youtube.com/live/abc123XYZ_-?si=xyz"),
        )
    }

    @Test
    fun `a youtube address that names a video gives the video`() {
        assertEquals("dQw4w9WgXcQ", StreamKinds.youTubeVideoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
        assertEquals("dQw4w9WgXcQ", StreamKinds.youTubeVideoId("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(
            "abc123XYZ_-",
            StreamKinds.youTubeVideoId("https://www.youtube.com/live/abc123XYZ_-?si=xyz"),
        )
        assertEquals(
            "abc123XYZ_-",
            StreamKinds.youTubeVideoId("https://www.youtube.com/embed/abc123XYZ_-"),
        )
    }

    @Test
    fun `a youtube channel address names no video`() {
        assertNull(StreamKinds.youTubeVideoId("https://www.youtube.com/@monirul_Islam_SM/live"))
    }

    @Test
    fun `an address that is not youtube names no video`() {
        assertNull(StreamKinds.youTubeVideoId("https://example.test/live/stream.m3u8"))
    }

    @Test
    fun `a youtube address is recognised through its host`() {
        assertTrue(StreamKinds.isYouTubeUrl("https://www.youtube.com/watch?v=x"))
        assertTrue(StreamKinds.isYouTubeUrl("https://m.youtube.com/watch?v=x"))
        assertTrue(StreamKinds.isYouTubeUrl("https://music.youtube.com/watch?v=x"))
        assertFalse(StreamKinds.isYouTubeUrl("https://example.test/watch?v=x"))
    }

    @Test
    fun `a relay that wraps a playlist is still a playlist`() {
        val url = "https://soluplay.vercel.app/api/stream?url=https%3A%2F%2Fexample.test%2Flive%2Fstream.m3u8"

        assertEquals(StreamKind.HLS, StreamKinds.kindOf(url))
        assertFalse(StreamKinds.isMpegTsUrl(url))
    }

    @Test
    fun `a relay that wraps a transport stream is still a transport stream`() {
        val url = "https://soluplay.vercel.app/api/stream?url=https%3A%2F%2Fexample.test%2Flive%2Fstream.ts"

        assertEquals(StreamKind.MPEG_TS, StreamKinds.kindOf(url))
    }

    @Test
    fun `a relay is read back to the address it wrapped`() {
        val url = "https://soluplay.vercel.app/api/stream?url=https%3A%2F%2Fexample.test%2Flive%2Fstream.m3u8"

        assertEquals("https://example.test/live/stream.m3u8", StreamKinds.toDirectStreamUrl(url))
    }

    @Test
    fun `an address that is not behind a relay is left alone`() {
        assertEquals(
            "https://example.test/live/stream.m3u8",
            StreamKinds.toDirectStreamUrl("https://example.test/live/stream.m3u8"),
        )
    }
}
