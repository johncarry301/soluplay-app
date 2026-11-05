package com.soluplay.core

import com.soluplay.core.api.HttpStatusException
import com.soluplay.core.api.HttpTransport
import com.soluplay.core.update.UpdateManifestClient
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where the app finds out that there is a newer build.
 *
 * The site answers first and the mirror answers second: a site that is down
 * must not be able to stop an update from being seen. A body that cannot be
 * read as a manifest is that address failing, not a manifest.
 */
class UpdateManifestClientTest {

    private val manifest = """
        {
          "schemaVersion": 1,
          "appId": "com.soluplay.app",
          "versionCode": 2,
          "versionName": "0.2.0",
          "apkUrl": "https://example.com/soluplay-0.2.0.apk",
          "sha256": "${"a".repeat(64)}",
          "variants": {
            "phone": { "url": "https://example.com/phone.apk", "sha256": "${"b".repeat(64)}" },
            "tv": { "url": "https://example.com/tv.apk", "sha256": "${"c".repeat(64)}" }
          }
        }
    """.trimIndent()

    private class FakeTransport(
        private val bodies: Map<String, String>,
    ) : HttpTransport {
        val requested = mutableListOf<String>()

        override suspend fun get(url: String): String {
            requested += url
            return bodies[url] ?: throw HttpStatusException(503, "not answering")
        }
    }

    @Test
    fun `the site is asked first and the mirror is not asked at all`() = runBlocking {
        val transport = FakeTransport(
            mapOf("https://site.test/api/app/version" to manifest),
        )
        val client = UpdateManifestClient(
            baseUrls = listOf("https://site.test"),
            mirrorUrl = "https://mirror.test/latest.json",
            transport = transport,
        )

        val read = client.manifest()

        assertNotNull(read)
        assertEquals(2, read?.versionCode)
        assertEquals(listOf("https://site.test/api/app/version"), transport.requested)
    }

    @Test
    fun `a site that does not answer leaves the mirror to answer`() = runBlocking {
        val transport = FakeTransport(
            mapOf("https://mirror.test/latest.json" to manifest),
        )
        val client = UpdateManifestClient(
            baseUrls = listOf("https://site.test"),
            mirrorUrl = "https://mirror.test/latest.json",
            transport = transport,
        )

        val read = client.manifest()

        assertEquals(2, read?.versionCode)
        assertEquals(
            listOf("https://site.test/api/app/version", "https://mirror.test/latest.json"),
            transport.requested,
        )
    }

    @Test
    fun `a body that is not a manifest is the address failing`() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://site.test/api/app/version" to "<html>not a manifest</html>",
                "https://mirror.test/latest.json" to manifest,
            ),
        )
        val client = UpdateManifestClient(
            baseUrls = listOf("https://site.test"),
            mirrorUrl = "https://mirror.test/latest.json",
            transport = transport,
        )

        val read = client.manifest()

        assertEquals(2, read?.versionCode)
        assertEquals(
            listOf("https://site.test/api/app/version", "https://mirror.test/latest.json"),
            transport.requested,
        )
    }

    @Test
    fun `a manifest with no build in it is the address failing`() = runBlocking {
        val transport = FakeTransport(
            mapOf(
                "https://site.test/api/app/version" to """{ "versionCode": 0 }""",
                "https://mirror.test/latest.json" to manifest,
            ),
        )
        val client = UpdateManifestClient(
            baseUrls = listOf("https://site.test"),
            mirrorUrl = "https://mirror.test/latest.json",
            transport = transport,
        )

        val read = client.manifest()

        assertEquals(2, read?.versionCode)
    }

    @Test
    fun `no address answering is no update, and not a failure`() = runBlocking {
        val client = UpdateManifestClient(
            baseUrls = listOf("https://site.test"),
            mirrorUrl = "https://mirror.test/latest.json",
            transport = FakeTransport(emptyMap()),
        )

        assertNull(client.manifest())
    }

    @Test
    fun `the offer is the build for the surface the device is`() = runBlocking {
        val transport = FakeTransport(
            mapOf("https://site.test/api/app/version" to manifest),
        )
        val client = UpdateManifestClient(
            baseUrls = listOf("https://site.test"),
            mirrorUrl = "https://mirror.test/latest.json",
            transport = transport,
        )

        val phone = client.offer("phone")
        val tv = client.offer("tv")

        assertEquals("https://example.com/phone.apk", phone?.url)
        assertEquals("https://example.com/tv.apk", tv?.url)
        assertEquals("b".repeat(64), phone?.sha256)
    }
}
