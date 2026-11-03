package com.soluplay.core

import com.soluplay.core.api.CatalogueClient
import com.soluplay.core.api.SoluPlayJson
import com.soluplay.core.browse.CatalogueOrigin
import com.soluplay.core.browse.CatalogueRepository
import com.soluplay.core.browse.SeedCatalogue
import com.soluplay.core.browse.SeedProvider
import com.soluplay.core.cache.FileCatalogueCache
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * The live site, read by the app's own code.
 *
 * This one reaches the network, so it is skipped unless it is asked for:
 *
 *   ./gradlew test -Dsoluplay.live=1
 *
 * It is the only proof here that the request the app makes is the request the
 * site answers.
 */
class LiveCatalogueHarnessTest {

    @get:Rule
    val folder = TemporaryFolder()

    private fun liveHarnessEnabled(): Boolean = System.getProperty("soluplay.live") == "1"

    @Test
    fun `the site answers the app's own request with the catalogue`() = runBlocking {
        assumeTrue(liveHarnessEnabled())

        val client = CatalogueClient()
        val body = client.channelsJson()

        val envelope = SoluPlayJson.json.decodeFromString(
            com.soluplay.core.api.ChannelsEnvelope.serializer(),
            body,
        )

        assertTrue("the site reported a failure", envelope.success)
        assertTrue("the site sent no channels", envelope.channels.isNotEmpty())
        assertEquals(envelope.channels.size, envelope.count)
        println("live catalogue: ${envelope.channels.size} channels")
    }

    @Test
    fun `the app reads the live catalogue through its own repository`() = runBlocking {
        assumeTrue(liveHarnessEnabled())

        val repository = CatalogueRepository(
            source = CatalogueClient(),
            cache = FileCatalogueCache(folder.newFolder("catalogue")),
            seed = object : SeedProvider {
                override fun channelsJson(): String = SeedCatalogue.unwrap(seedFile().readText())
            },
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.LIVE, snapshot.origin)
        assertTrue("the app came back with no channels", snapshot.channels.isNotEmpty())
        println(
            "live snapshot: origin=${snapshot.origin} channels=${snapshot.channels.size} "
                + "fixtures=${snapshot.fixtures.size} outage=${snapshot.outage}",
        )
    }

    @Test
    fun `a site that cannot be reached leaves the baked catalogue on screen`() = runBlocking {
        assumeTrue(liveHarnessEnabled())

        val repository = CatalogueRepository(
            source = CatalogueClient(baseUrls = listOf("https://soluplay.invalid")),
            cache = FileCatalogueCache(folder.newFolder("catalogue")),
            seed = object : SeedProvider {
                override fun channelsJson(): String = SeedCatalogue.unwrap(seedFile().readText())
            },
        )

        val snapshot = repository.load()

        assertEquals(CatalogueOrigin.SEED, snapshot.origin)
        assertTrue("the app came back with no channels", snapshot.channels.isNotEmpty())
        assertTrue("the screen is not told the site is down", snapshot.outage)
        println("site-down snapshot: origin=${snapshot.origin} channels=${snapshot.channels.size}")
    }

    private fun seedFile(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".")
        while (dir != null) {
            val candidate = File(dir, "app/src/main/assets/catalogue-channels.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError("could not find the baked seed above ${System.getProperty("user.dir")}")
    }
}
