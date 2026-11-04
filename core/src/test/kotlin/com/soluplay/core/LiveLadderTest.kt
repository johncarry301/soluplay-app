package com.soluplay.core

import com.soluplay.core.api.CatalogueSource
import com.soluplay.core.browse.CatalogueRepository
import com.soluplay.core.browse.SeedProvider
import com.soluplay.core.cache.CachedCatalogue
import com.soluplay.core.cache.CatalogueCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException

private val CATALOGUE = """
    {
      "success": true,
      "count": 1,
      "channels": [
        { "_id": "c1", "name": "Channel One", "category": "Sports", "priorityOrder": 1, "activeStreamCount": 2 }
      ]
    }
""".trimIndent()

private val LADDER = """
    {
      "success": true,
      "playable": true,
      "channel": {
        "_id": "c1",
        "name": "Channel One",
        "category": "Sports",
        "priorityOrder": 1,
        "streams": [
          { "_id": "s1", "url": "https://a.test/1.m3u8", "priority": 1, "status": "active", "latency": 120 },
          { "_id": "s2", "url": "https://b.test/2.m3u8", "priority": 2, "status": "active", "latency": 300 }
        ]
      }
    }
""".trimIndent()

/**
 * Where the mirror list comes from when the app plays something.
 *
 * The rule is narrower than the rule for the guide: the guide may fall back to
 * a copy on the device or a copy baked into the APK, and the ladder may not.
 * A stale ladder would walk a viewer into a link that died days ago, and the
 * baked copy carries no stream addresses at all.
 */
class LiveLadderTest {

    private class FakeSource(
        private val channelDetail: String? = null,
    ) : CatalogueSource {
        var requested: String? = null

        override suspend fun channelsJson(): String = CATALOGUE

        override suspend fun fixturesJson(): String = """{ "success": true, "count": 0, "fixtures": [] }"""

        override suspend fun channelJson(id: String): String {
            requested = id
            return channelDetail ?: throw IOException("no ladder")
        }
    }

    private class FakeCache : CatalogueCache {
        override fun read(): CachedCatalogue? = CachedCatalogue(CATALOGUE, null, savedAt = 1)

        override fun write(channelsJson: String, fixturesJson: String?) = Unit
    }

    private class FakeSeed : SeedProvider {
        override fun channelsJson(): String = CATALOGUE
    }

    @Test
    fun `the ladder is read from the site, in the published order`() = runBlocking {
        val source = FakeSource(channelDetail = LADDER)
        val repository = CatalogueRepository(
            source = source,
            cache = FakeCache(),
            seed = FakeSeed(),
        )

        val detail = repository.liveChannelDetail("c1")

        assertEquals("c1", source.requested)
        assertEquals(2, detail?.streams?.size)
        assertEquals("https://a.test/1.m3u8", detail?.streams?.first()?.url)
        assertEquals(2, detail?.streams?.get(1)?.priority)
    }

    @Test
    fun `the ladder is never taken from the saved copy or the baked seed`() = runBlocking {
        val repository = CatalogueRepository(
            source = FakeSource(channelDetail = null),
            cache = FakeCache(),
            seed = FakeSeed(),
        )

        val detail = repository.liveChannelDetail("c1")

        assertNull("a ladder was invented from somewhere else", detail)
    }
}
