package com.soluplay.core

import com.soluplay.core.api.CatalogueClient
import com.soluplay.core.api.CatalogueSource
import com.soluplay.core.api.HttpStatusException
import com.soluplay.core.api.HttpTransport
import com.soluplay.core.browse.BrowseSnapshot
import com.soluplay.core.browse.CatalogueOrigin
import com.soluplay.core.browse.CatalogueRepository
import com.soluplay.core.browse.SeedProvider
import com.soluplay.core.browse.mergeSnapshot
import com.soluplay.core.cache.CachedCatalogue
import com.soluplay.core.cache.CatalogueCache
import com.soluplay.core.model.Channel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Where the guide comes from when the site does not answer.
 *
 * The rule the app is built on: a failure is never shown as an empty guide.
 * The order of the fallbacks is the order of trust — live, then the copy this
 * device saved, then the copy baked into the APK.
 */
class CatalogueFallbackTest {

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

    private class FakeSource(
        private val channels: String? = null,
        private val fixtures: String? = null,
        private val channelsError: Exception? = null,
        private val fixturesError: Exception? = null,
        private val channelDetail: String? = null,
    ) : CatalogueSource {
        override suspend fun channelsJson(): String {
            channelsError?.let { throw it }
            return channels ?: throw IOException("no channels")
        }

        override suspend fun fixturesJson(): String {
            fixturesError?.let { throw it }
            return fixtures ?: throw IOException("no fixtures")
        }

        override suspend fun channelJson(id: String): String =
            channelDetail ?: throw IOException("no channel detail")
    }

    private class FakeCache(
        var saved: CachedCatalogue? = null,
    ) : CatalogueCache {
        var written: Pair<String, String?>? = null

        override fun read(): CachedCatalogue? = saved

        override fun write(channelsJson: String, fixturesJson: String?) {
            written = channelsJson to fixturesJson
        }
    }

    private class FakeSeed(private val body: String? = null) : SeedProvider {
        override fun channelsJson(): String? = body
    }

    @Test
    fun `the live catalogue is the one on screen`() = runBlocking {
        val cache = FakeCache()
        val repository = CatalogueRepository(
            source = FakeSource(channels = liveCatalogue, fixtures = liveFixtures),
            cache = cache,
            seed = FakeSeed(),
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.LIVE, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertEquals(1, snapshot.fixtures.size)
        assertFalse(snapshot.outage)
        assertNotNull(cache.written)
    }

    @Test
    fun `a site that cannot be reached falls back to the copy on the device`() = runBlocking {
        val repository = CatalogueRepository(
            source = FakeSource(channelsError = IOException("site is down")),
            cache = FakeCache(saved = CachedCatalogue(liveCatalogue, liveFixtures, savedAt = 1_700_000_000_000)),
            seed = FakeSeed(),
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.CACHED, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertEquals(1, snapshot.fixtures.size)
        assertTrue(snapshot.outage)
        assertFalse("the site was reachable", snapshot.siteReachable)
        assertEquals(1_700_000_000_000, snapshot.savedAt)
    }

    @Test
    fun `with no saved copy the baked catalogue is used`() = runBlocking {
        val repository = CatalogueRepository(
            source = FakeSource(channelsError = IOException("site is down")),
            cache = FakeCache(),
            seed = FakeSeed(liveCatalogue),
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.SEED, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertTrue(snapshot.outage)
    }

    @Test
    fun `nothing at all is not an empty guide`() = runBlocking {
        val repository = CatalogueRepository(
            source = FakeSource(channelsError = IOException("site is down")),
            cache = FakeCache(),
            seed = FakeSeed(),
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.NONE, snapshot.origin)
        assertTrue(snapshot.channels.isEmpty())
        assertTrue(snapshot.outage)
        assertTrue(snapshot.error.isNotBlank())
    }

    @Test
    fun `a route that failed is not a guide`() = runBlocking {
        val repository = CatalogueRepository(
            source = FakeSource(channels = """{ "success": false, "error": "Catalogue source unavailable" }"""),
            cache = FakeCache(saved = CachedCatalogue(liveCatalogue, null, savedAt = 1)),
            seed = FakeSeed(),
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.CACHED, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertTrue("the site answered, it just had nothing", snapshot.siteReachable)
    }

    @Test
    fun `a catalogue with no channels is not a guide`() = runBlocking {
        val repository = CatalogueRepository(
            source = FakeSource(channels = """{ "success": true, "count": 0, "channels": [] }"""),
            cache = FakeCache(saved = CachedCatalogue(liveCatalogue, null, savedAt = 1)),
            seed = FakeSeed(),
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.CACHED, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
    }

    @Test
    fun `the match board failing does not cost the guide`() = runBlocking {
        val repository = CatalogueRepository(
            source = FakeSource(channels = liveCatalogue, fixturesError = IOException("board is down")),
            cache = FakeCache(),
            seed = FakeSeed(),
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.LIVE, snapshot.origin)
        assertEquals(2, snapshot.channels.size)
        assertTrue(snapshot.fixtures.isEmpty())
    }

    @Test
    fun `the backup address is tried when the primary does not answer`() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://backup.test/api/channels" to liveCatalogue,
            ),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test", "https://backup.test"),
            transport = transport,
        )

        val body = client.channelsJson()

        assertEquals(liveCatalogue, body)
        assertEquals(
            listOf("https://primary.test/api/channels", "https://backup.test/api/channels"),
            transport.requested,
        )
    }

    @Test
    fun `the first address that answers is the one used`() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://primary.test/api/channels" to liveCatalogue,
                "https://backup.test/api/channels" to """{ "success": true, "channels": [] }""",
            ),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test", "https://backup.test"),
            transport = transport,
        )

        client.channelsJson()

        assertEquals(listOf("https://primary.test/api/channels"), transport.requested)
    }

    @Test
    fun `an address that answers with nothing is passed over`() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://primary.test/api/channels" to """{ "success": true, "count": 0, "channels": [] }""",
                "https://backup.test/api/channels" to liveCatalogue,
            ),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test", "https://backup.test"),
            transport = transport,
        )

        val body = client.channelsJson()

        assertEquals(liveCatalogue, body)
        assertEquals(
            listOf("https://primary.test/api/channels", "https://backup.test/api/channels"),
            transport.requested,
        )
    }

    @Test
    fun `a site that answered with nothing is told apart from a site that did not`() = runBlocking {
        val answered = CatalogueRepository(
            source = FakeSource(channels = """{ "success": false, "error": "no catalogue" }"""),
            cache = FakeCache(),
            seed = FakeSeed(liveCatalogue),
        ).load()

        val silent = CatalogueRepository(
            source = FakeSource(channelsError = IOException("site is down")),
            cache = FakeCache(),
            seed = FakeSeed(liveCatalogue),
        ).load()

        assertTrue("a site that answered was called silent", answered.siteReachable)
        assertFalse("a site that did not answer was called reachable", silent.siteReachable)
    }

    @Test
    fun `a board that could not be read is not kept on the device`() = runBlocking {
        val cache = FakeCache()
        val repository = CatalogueRepository(
            source = FakeSource(channels = liveCatalogue, fixturesError = IOException("board is down")),
            cache = cache,
            seed = FakeSeed(),
        )

        repository.load()

        assertEquals(liveCatalogue, cache.written?.first)
        assertEquals(null, cache.written?.second)
    }

    @Test
    fun `a board that failed is not kept on the device either`() = runBlocking {
        val cache = FakeCache()
        val repository = CatalogueRepository(
            source = FakeSource(
                channels = liveCatalogue,
                fixtures = """{ "success": false, "error": "no board" }""",
            ),
            cache = cache,
            seed = FakeSeed(),
        )

        repository.load()

        assertEquals(null, cache.written?.second)
    }

    @Test
    fun `a guide that is left on the screen is not called live`() {
        val current = BrowseSnapshot(
            channels = listOf(Channel(id = "a1", name = "One")),
            origin = CatalogueOrigin.LIVE,
        )
        val next = BrowseSnapshot(origin = CatalogueOrigin.NONE, siteReachable = false)

        val merged = mergeSnapshot(current, next)

        assertEquals(CatalogueOrigin.CACHED, merged.origin)
        assertEquals(1, merged.channels.size)
        assertTrue(merged.outage)
    }

    @Test
    fun `a guide that is left on the screen keeps saying which copy it is`() {
        val current = BrowseSnapshot(
            channels = listOf(Channel(id = "a1", name = "One")),
            origin = CatalogueOrigin.SEED,
        )
        val next = BrowseSnapshot(origin = CatalogueOrigin.NONE, siteReachable = true)

        val merged = mergeSnapshot(current, next)

        assertEquals(CatalogueOrigin.SEED, merged.origin)
        assertTrue(merged.siteReachable)
    }

    @Test
    fun `no address answering is an error, not an empty catalogue`() = runBlocking {
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test"),
            transport = FakeTransport(emptyMap()),
        )

        val failure = runCatching { client.channelsJson() }.exceptionOrNull()

        assertTrue(failure is IOException)
    }

    private class FakeTransport(
        private val bodies: Map<String, String>,
    ) : HttpTransport {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String): String {
            requested += url
            return bodies[url] ?: throw HttpStatusException(503, "site is down")
        }
    }
}
