package com.soluplay.core

import com.soluplay.core.api.JsonPoster
import com.soluplay.core.api.YouTubeResolver
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a YouTube mirror is turned into a video.
 *
 * Most of the catalogue's YouTube mirrors name the video they are showing in
 * the address itself, and those need nothing from the network. The ones that
 * name only a channel cannot be answered by the app — the video that is live
 * changes over time — so the site's own resolver is asked through the route it
 * already publishes.
 */
class YouTubeResolverTest {

    /** A poster that records what it was asked and answers with what it was given. */
    private class RecordingPoster(
        private val answer: String? = null,
        private val fail: Boolean = false,
    ) : JsonPoster {
        val asked = mutableListOf<Pair<String, String>>()

        override suspend fun postJson(url: String, body: String): String {
            asked += url to body
            if (fail) throw java.io.IOException("the site did not answer")
            return answer ?: error("no answer was given")
        }
    }

    @Test
    fun `an address that names a video is answered without a round trip`() {
        val poster = RecordingPoster()
        val resolver = YouTubeResolver(baseUrls = listOf("https://site.test"), poster = poster)

        val videoId = runBlocking {
            resolver.videoId("https://www.youtube.com/watch?v=dQw4w9WgXcQ")
        }

        assertEquals("dQw4w9WgXcQ", videoId)
        assertTrue("the site was asked needlessly", poster.asked.isEmpty())
    }

    @Test
    fun `a channel address is asked through the site's own route`() {
        val poster = RecordingPoster(answer = """{"success":true,"videoId":"abc123"}""")
        val resolver = YouTubeResolver(baseUrls = listOf("https://site.test"), poster = poster)

        val videoId = runBlocking {
            resolver.videoId("https://www.youtube.com/@monirul_Islam_SM/live")
        }

        assertEquals("abc123", videoId)
        assertEquals(1, poster.asked.size)
        assertEquals("https://site.test/api/youtube/resolve", poster.asked.first().first)
        assertEquals(
            """{"url":"https://www.youtube.com/@monirul_Islam_SM/live"}""",
            poster.asked.first().second,
        )
    }

    @Test
    fun `a quote in the address cannot break the body`() {
        val poster = RecordingPoster(answer = """{"success":true,"videoId":"abc123"}""")
        val resolver = YouTubeResolver(baseUrls = listOf("https://site.test"), poster = poster)

        // An address with a quote and a backslash in it.
        val url = "https://www.youtube.com/@monirul_Islam_SM/live?x=" + "\"" + "broken" + "\\"

        runBlocking {
            resolver.videoId(url)
        }

        val body = poster.asked.first().second
        assertTrue(body.startsWith("{\"url\":\"https://www.youtube.com/@monirul_Islam_SM/live?x="))
        assertTrue("the quote was not escaped", body.contains("\\\"broken"))
        assertTrue("the backslash was not escaped", body.contains("\\\\"))
        assertTrue(body.endsWith("\"}"))
    }

    @Test
    fun `the answer is kept for the life of the process`() {
        val poster = RecordingPoster(answer = """{"success":true,"videoId":"abc123"}""")
        val resolver = YouTubeResolver(baseUrls = listOf("https://site.test"), poster = poster)

        runBlocking {
            resolver.videoId("https://www.youtube.com/@monirul_Islam_SM/live")
            resolver.videoId("https://www.youtube.com/@monirul_Islam_SM/live")
        }

        assertEquals("the site was asked twice", 1, poster.asked.size)
    }

    @Test
    fun `a failed lookup answers nothing`() {
        val poster = RecordingPoster(fail = true)
        val resolver = YouTubeResolver(baseUrls = listOf("https://site.test"), poster = poster)

        val videoId = runBlocking {
            resolver.videoId("https://www.youtube.com/@monirul_Islam_SM/live")
        }

        assertNull(videoId)
    }

    @Test
    fun `an answer that reports a failure answers nothing`() {
        val poster = RecordingPoster(answer = """{"success":false,"error":"not found"}""")
        val resolver = YouTubeResolver(baseUrls = listOf("https://site.test"), poster = poster)

        val videoId = runBlocking {
            resolver.videoId("https://www.youtube.com/@monirul_Islam_SM/live")
        }

        assertNull(videoId)
    }

    @Test
    fun `a body that cannot be read answers nothing`() {
        val poster = RecordingPoster(answer = "not json at all")
        val resolver = YouTubeResolver(baseUrls = listOf("https://site.test"), poster = poster)

        val videoId = runBlocking {
            resolver.videoId("https://www.youtube.com/@monirul_Islam_SM/live")
        }

        assertNull(videoId)
    }
}
