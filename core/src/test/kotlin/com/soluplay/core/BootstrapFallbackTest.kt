package com.soluplay.core

import com.soluplay.core.api.CatalogueClient
import com.soluplay.core.api.CatalogueSource
import com.soluplay.core.api.HttpStatusException
import com.soluplay.core.api.HttpTransport
import com.soluplay.core.browse.CatalogueOrigin
import com.soluplay.core.browse.CatalogueRepository
import com.soluplay.core.browse.SeedProvider
import com.soluplay.core.cache.CachedCatalogue
import com.soluplay.core.cache.CatalogueCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * The one-body route, and what happens when the site has not got it.
 *
 * The app asks the bootstrap route first and falls back to the two older
 * routes. The fallback is not a downgrade: it is what the app did before the
 * bootstrap route existed, and the site has not got the route until the
 * change in `freetv` is deployed.
 */
class BootstrapFallbackTest {

    private val liveCatalogue = """
        {
          "success": true,
          "count": 2,
          "channels": [
            { "_id": "a1", "name": "Sports One", "category": "Sports", "priorityOrder": 1, "activeStreamCount": 2 },
            { "_id": "a2", "name": "Sports Two", "category": "Sports", "priorityOrder": 2, "activeStreamCount": 1 }
          ]
        }
    """.trimIndent()

    private val liveFixtures = """
        {
          "success": true,
          "count": 1,
          "fixtures": [
            {
              "id": "f1",
              "title": "One v Two",
              "teamA": "One",
              "teamB": "Two",
              "startsAt": "2026-11-04T18:00:00.000Z"
            }
          ]
        }
    """.trimIndent()

    private val liveBootstrap = """
        {
          "success": true,
          "count": 2,
          "channels": [
            { "_id": "a1", "name": "Sports One", "category": "Sports", "priorityOrder": 1, "activeStreamCount": 2 },
            { "_id": "a2", "name": "Sports Two", "category": "Sports", "priorityOrder": 2, "activeStreamCount": 1 }
          ],
          "fixtures": [
            {
              "id": "f1",
              "title": "One v Two",
              "teamA": "One",
              "teamB": "Two",
              "startsAt": "2026-11-04T18:00:00.000Z"
            }
          ],
          "fixturesAvailable": true,
          "updatedAt": "2026-11-05T00:00:00.000Z"
        }
    """.trimIndent()

    /** A source that records what it was asked for. */
    private class RecordingSource(
        private val bootstrap: String? = null,
        private val channels: String? = null,
        private val fixtures: String? = null,
    ) : CatalogueSource {
        val asked = mutableListOf<String>()

        override suspend fun bootstrapJson(): String? {
            asked += "bootstrap"
            return bootstrap
        }

        override suspend fun channelsJson(): String {
            asked += "channels"
            return channels ?: throw IOException("no channels")
        }

        override suspend fun fixturesJson(): String {
            asked += "fixtures"
            return fixtures ?: throw IOException("no fixtures")
        }

        override suspend fun channelJson(id: String): String =
            throw IOException("no channel detail")
    }

    private class FakeCache : CatalogueCache {
        var written: Pair<String, String?>? = null

        override fun read(): CachedCatalogue? = null

        override fun write(channelsJson: String, fixturesJson: String?) {
            written = channelsJson to fixturesJson
        }
    }

    private class FakeSeed(private val body: String? = null) : SeedProvider {
        override fun channelsJson(): String? = body
    }

    @Test
    fun `the bootstrap body is the guide and the board in one`() = runBlocking {
        val source = RecordingSource(bootstrap = liveBootstrap)
        val cache = FakeCache()

        val snapshot = CatalogueRepository(source, cache, FakeSeed()).load()

        assertEquals(CatalogueOrigin.LIVE, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertEquals(1, snapshot.fixtures.size)
        assertEquals(listOf("bootstrap"), source.asked)
        assertNotNull(cache.written)
        assertEquals(liveBootstrap, cache.written?.second)
    }

    @Test
    fun `a site without the bootstrap route falls back to the two routes`() = runBlocking {
        val source = RecordingSource(channels = liveCatalogue, fixtures = liveFixtures)

        val snapshot = CatalogueRepository(source, FakeCache(), FakeSeed()).load()

        assertEquals(CatalogueOrigin.LIVE, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertEquals(1, snapshot.fixtures.size)
        assertEquals(listOf("bootstrap", "channels", "fixtures"), source.asked)
    }

    @Test
    fun `a bootstrap body that cannot be read falls back to the two routes`() = runBlocking {
        val source = RecordingSource(
            bootstrap = """{ "success": false, "error": "no guide" }""",
            channels = liveCatalogue,
            fixtures = liveFixtures,
        )

        val snapshot = CatalogueRepository(source, FakeCache(), FakeSeed()).load()

        assertEquals(CatalogueOrigin.LIVE, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertEquals(1, snapshot.fixtures.size)
        assertEquals(listOf("bootstrap", "channels", "fixtures"), source.asked)
    }

    @Test
    fun `a bootstrap body with no channels is not a guide`() = runBlocking {
        val source = RecordingSource(
            bootstrap = """{ "success": true, "count": 0, "channels": [] }""",
            channels = liveCatalogue,
            fixtures = liveFixtures,
        )

        val snapshot = CatalogueRepository(source, FakeCache(), FakeSeed()).load()

        assertEquals(CatalogueOrigin.LIVE, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertEquals(listOf("bootstrap", "channels", "fixtures"), source.asked)
    }

    @Test
    fun `a board the bootstrap could not read is not kept on the device`() = runBlocking {
        val cache = FakeCache()
        val source = RecordingSource(
            bootstrap = """
                {
                  "success": true,
                  "count": 2,
                  "channels": [
                    { "_id": "a1", "name": "Sports One", "category": "Sports" },
                    { "_id": "a2", "name": "Sports Two", "category": "Sports" }
                  ],
                  "fixtures": [],
                  "fixturesAvailable": false
                }
            """.trimIndent(),
        )

        val snapshot = CatalogueRepository(source, cache, FakeSeed()).load()

        assertEquals(CatalogueOrigin.LIVE, snapshot.origin)
        assertTrue(snapshot.fixtures.isEmpty())
        assertEquals(null, cache.written?.second)
    }

    @Test
    fun `the client reads the bootstrap route when the site has it`() = runBlocking {
        val transport = FakeTransport(
            mapOf("https://site.test/api/app/bootstrap" to liveBootstrap),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://site.test"),
            transport = transport,
        )

        val body = client.bootstrapJson()

        assertEquals(liveBootstrap, body)
        assertEquals(listOf("https://site.test/api/app/bootstrap"), transport.requested)
    }

    @Test
    fun `a site without the bootstrap route is not a failure`() = runBlocking {
        val transport = FakeTransport(
            mapOf("https://site.test/api/channels" to liveCatalogue),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://site.test"),
            transport = transport,
        )

        val body = client.bootstrapJson()

        assertNull(body)
    }

    @Test
    fun `the bootstrap route is asked of the backup address too`() = runBlocking {
        val transport = FakeTransport(
            mapOf("https://backup.test/api/app/bootstrap" to liveBootstrap),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test", "https://backup.test"),
            transport = transport,
        )

        val body = client.bootstrapJson()

        assertEquals(liveBootstrap, body)
        assertEquals(
            listOf(
                "https://primary.test/api/app/bootstrap",
                "https://backup.test/api/app/bootstrap",
            ),
            transport.requested,
        )
    }

    @Test
    fun `a bootstrap route that answers with rubbish is passed over`() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://primary.test/api/app/bootstrap" to """{ "success": true, "count": 0 }""",
                "https://backup.test/api/app/bootstrap" to liveBootstrap,
            ),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test", "https://backup.test"),
            transport = transport,
        )

        val body = client.bootstrapJson()

        assertEquals(liveBootstrap, body)
    }

    private class FakeTransport(
        private val bodies: Map<String, String>,
    ) : HttpTransport {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String): String {
            requested += url
            return bodies[url] ?: throw HttpStatusException(404, "not found")
        }
    }
}
