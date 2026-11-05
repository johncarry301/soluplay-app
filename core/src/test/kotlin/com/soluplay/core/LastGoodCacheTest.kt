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
import com.soluplay.core.cache.FileCatalogueCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

/**
 * The copy the device keeps, and the addresses it is fetched from.
 *
 * The rule is that a good copy is never lost to a bad one: an answer that is
 * not a guide leaves the saved copy alone, and the copy that is on the device
 * is written in one piece so that a crash halfway through cannot take it.
 */
class LastGoodCacheTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val liveCatalogue = """
        {
          "success": true,
          "count": 1,
          "channels": [
            { "_id": "a1", "name": "Sports One", "category": "Sports", "priorityOrder": 1, "activeStreamCount": 1 }
          ]
        }
    """.trimIndent()

    private val liveFixtures = """
        {
          "success": true,
          "count": 0,
          "fixtures": []
        }
    """.trimIndent()

    private val channelDetail = """
        {
          "success": true,
          "playable": true,
          "channel": {
            "_id": "a1",
            "name": "Sports One",
            "streams": [
              { "id": "s1", "url": "https://cdn.test/live/one.m3u8", "priority": 1 }
            ]
          }
        }
    """.trimIndent()

    private class FakeSource(private val channels: String?) : CatalogueSource {
        override suspend fun channelsJson(): String = channels ?: throw IOException("no channels")
        override suspend fun fixturesJson(): String = throw IOException("no board")
        override suspend fun channelJson(id: String): String = throw IOException("no channel")
    }

    private class FakeCache : CatalogueCache {
        var saved: CachedCatalogue? = null
        var writes: Int = 0
            private set

        override fun read(): CachedCatalogue? = saved

        override fun write(channelsJson: String, fixturesJson: String?) {
            writes++
            saved = CachedCatalogue(channelsJson, fixturesJson, savedAt = 1)
        }
    }

    private class FakeSeed(private val body: String? = null) : SeedProvider {
        override fun channelsJson(): String? = body
    }

    @Test
    fun `an answer that is not a guide leaves the saved copy alone`() = runBlocking {
        val cache = FakeCache()
        val repository = CatalogueRepository(
            source = FakeSource("""{ "success": false, "error": "Catalogue source unavailable" }"""),
            cache = cache,
            seed = FakeSeed(liveCatalogue),
        )

        val snapshot = repository.load()

        assertEquals("the bad answer was written over the saved copy", 0, cache.writes)
        assertEquals(CatalogueOrigin.SEED, snapshot.origin)
    }

    @Test
    fun `a guide with no channels in it leaves the saved copy alone`() = runBlocking {
        val cache = FakeCache()
        val repository = CatalogueRepository(
            source = FakeSource("""{ "success": true, "count": 0, "channels": [] }"""),
            cache = cache,
            seed = FakeSeed(liveCatalogue),
        )

        repository.load()

        assertEquals("the empty answer was written over the saved copy", 0, cache.writes)
    }

    @Test
    fun `a good answer is written over the saved copy`() = runBlocking {
        val cache = FakeCache()
        val repository = CatalogueRepository(
            source = FakeSource(liveCatalogue),
            cache = cache,
            seed = FakeSeed(),
        )

        repository.load()

        assertEquals(1, cache.writes)
    }

    @Test
    fun `the saved copy is written in one piece`() {
        val directory = folder.newFolder("catalogue")
        val cache = FileCatalogueCache(directory)

        cache.write(liveCatalogue, liveFixtures)

        val saved = File(directory, FileCatalogueCache.CHANNELS_FILE).readText()
        assertEquals(liveCatalogue, saved)
        assertTrue(
            "a half-written copy was left behind",
            File(directory, "${FileCatalogueCache.CHANNELS_FILE}.new").exists().not(),
        )
    }

    @Test
    fun `the copy on the device is the one that is read back`() {
        val directory = folder.newFolder("catalogue")
        FileCatalogueCache(directory).write(liveCatalogue, liveFixtures)

        val saved = FileCatalogueCache(directory).read()

        assertEquals(liveCatalogue, saved?.channelsJson)
        assertEquals(liveFixtures, saved?.fixturesJson)
    }

    @Test
    fun `the backup address answers the board when the primary does not`() = runBlocking {
        val transport = FakeTransport(
            mapOf("https://backup.test/api/fixtures" to liveFixtures),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test", "https://backup.test"),
            transport = transport,
        )

        val body = client.fixturesJson()

        assertEquals(liveFixtures, body)
        assertEquals(
            listOf("https://primary.test/api/fixtures", "https://backup.test/api/fixtures"),
            transport.requested,
        )
    }

    @Test
    fun `the backup address answers the channel when the primary does not`() = runBlocking {
        val transport = FakeTransport(
            mapOf("https://backup.test/api/channels/a1" to channelDetail),
        )
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test", "https://backup.test"),
            transport = transport,
        )

        val body = client.channelJson("a1")

        assertEquals(channelDetail, body)
        assertEquals(
            listOf("https://primary.test/api/channels/a1", "https://backup.test/api/channels/a1"),
            transport.requested,
        )
    }

    @Test
    fun `no address answering is a failure and not an empty guide`() = runBlocking {
        val client = CatalogueClient(
            baseUrls = listOf("https://primary.test", "https://backup.test"),
            transport = FakeTransport(emptyMap()),
        )

        val failure = runCatching { client.channelJson("a1") }.exceptionOrNull()

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
