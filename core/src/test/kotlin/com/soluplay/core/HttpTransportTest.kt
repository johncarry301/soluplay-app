package com.soluplay.core

import com.soluplay.core.api.ApiConfig
import com.soluplay.core.api.HttpStatusException
import com.soluplay.core.api.UrlConnectionTransport
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import java.security.cert.Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory

class HttpTransportTest {
    private class Fake(url: URL, val status: Int, val location: String? = null) : HttpsURLConnection(url) {
        var disconnected = false
        var inputClosed = false
        var expectedFactory: SSLSocketFactory? = null
        val verifier = hostnameVerifier
        val output = ByteArrayOutputStream()
        val input = object : ByteArrayInputStream("ok".toByteArray()) {
            override fun close() { inputClosed = true; super.close() }
        }
        override fun connect() = Unit
        override fun disconnect() { disconnected = true }
        override fun usingProxy() = false
        override fun getCipherSuite() = "TLS_FAKE"
        override fun getLocalCertificates(): Array<Certificate>? = null
        override fun getServerCertificates(): Array<Certificate> = emptyArray()
        override fun getResponseCode(): Int {
            assertFalse(instanceFollowRedirects)
            assertSame(verifier, hostnameVerifier)
            expectedFactory?.let { assertSame(it, sslSocketFactory) }
            return status
        }
        override fun getHeaderField(name: String): String? = if (name == "Location") location else null
        override fun getInputStream() = input
        override fun getErrorStream() = input
        override fun getOutputStream(): ByteArrayOutputStream {
            assertSame(expectedFactory, sslSocketFactory)
            assertSame(verifier, hostnameVerifier)
            assertFalse(instanceFollowRedirects)
            return output
        }
    }

    private fun refused(block: () -> Unit) {
        try { block(); fail("Expected IOException") } catch (_: IOException) { }
    }

    @Test fun `invalid initial URLs never open a connection`() = runBlocking {
        val transport = UrlConnectionTransport(connectionFactory = { error("Must not connect") })
        for (url in listOf("http://example.com/a", "https://user:pass@example.com/a", "https://@example.com/a", "file:///tmp/a")) {
            refused { runBlocking { transport.get(url) } }
            refused { runBlocking { transport.postJson(url, "secret") } }
            refused { transport.openStream(url) }
        }
    }

    @Test fun `GET follows relative same origin redirects and closes every response`() = runBlocking {
        for (status in listOf(301, 302, 303, 307, 308)) {
            val opened = mutableListOf<Fake>()
            val transport = UrlConnectionTransport(connectionFactory = { url ->
                Fake(url, if (opened.isEmpty()) status else 200, "/next").also { opened += it }
            })
            assertEquals("ok", transport.get("https://example.com/start"))
            assertEquals("/next", opened.last().url.path)
            assertTrue(opened.all { it.inputClosed && it.disconnected && it.requestMethod == "GET" })
        }
    }

    @Test fun `API rejects origin changes downgrade and userinfo before next connection`() = runBlocking {
        for (target in listOf("https://cdn.example/a", "https://example.com:444/a", "http://example.com/a", "https://u:p@example.com/a")) {
            for (post in listOf(false, true)) {
                val first = Fake(URL("https://example.com/a"), 307, target)
                val factory = SSLContext.getDefault().socketFactory
                first.expectedFactory = factory
                var calls = 0
                val transport = UrlConnectionTransport(sslSocketFactory = factory, connectionFactory = { calls++; first })
                refused { runBlocking { if (post) transport.postJson(first.url.toString(), "secret") else transport.get(first.url.toString()) } }
                assertEquals(1, calls)
                assertTrue(first.disconnected && first.inputClosed)
            }
        }
    }

    @Test fun `POST preserves body only for 307 and 308 with TLS before output`() = runBlocking {
        for (status in listOf(301, 302, 303, 307, 308)) {
            val factory = SSLContext.getDefault().socketFactory
            val opened = mutableListOf<Fake>()
            val transport = UrlConnectionTransport(sslSocketFactory = factory, connectionFactory = { url ->
                Fake(url, if (opened.isEmpty()) status else 200, "https://EXAMPLE.com:443/next").also {
                    it.expectedFactory = factory
                    opened += it
                }
            })
            if (status == 307 || status == 308) assertEquals("ok", transport.postJson("https://example.com/a", "secret"))
            else refused { runBlocking { transport.postJson("https://example.com/a", "secret") } }
            assertEquals(if (status >= 307) 2 else 1, opened.size)
            assertTrue(opened.all { it.requestMethod == "POST" && it.output.toString("UTF-8") == "secret" && it.disconnected })
        }
    }

    @Test fun `downloads allow HTTPS CDN hops and disconnect on close`() {
        val opened = mutableListOf<Fake>()
        val transport = UrlConnectionTransport(connectionFactory = { url ->
            Fake(url, if (opened.isEmpty()) 302 else 200, "https://cdn.example/file?signature=abc").also { opened += it }
        })
        val stream = transport.openStream("https://example.com/a")
        assertEquals("cdn.example", opened.last().url.host)
        assertTrue(opened.first().disconnected && opened.first().inputClosed)
        assertFalse(opened.last().disconnected)
        stream.close()
        assertTrue(opened.last().disconnected && opened.last().inputClosed)
    }

    @Test fun `download rejects insecure CDN targets and bounded loops`() {
        for (target in listOf("http://cdn.example/a", "https://user@cdn.example/a", "/loop")) {
            val opened = mutableListOf<Fake>()
            val transport = UrlConnectionTransport(connectionFactory = { url -> Fake(url, 302, target).also { opened += it } })
            refused { transport.openStream("https://example.com/a") }
            assertEquals(if (target == "/loop") UrlConnectionTransport.MAX_REDIRECTS + 1 else 1, opened.size)
            assertTrue(opened.all { it.disconnected && it.inputClosed })
        }
    }

    @Test fun `API redirect loops are bounded and missing locations fail`() = runBlocking {
        for (location in listOf("/loop", null)) {
            val opened = mutableListOf<Fake>()
            val transport = UrlConnectionTransport(connectionFactory = { url -> Fake(url, 302, location).also { opened += it } })
            refused { runBlocking { transport.get("https://example.com/a") } }
            assertEquals(if (location == null) 1 else UrlConnectionTransport.MAX_REDIRECTS + 1, opened.size)
            assertTrue(opened.all { it.disconnected && it.inputClosed })
        }
    }

    @Test fun `error response closes and disconnects`() {
        val connection = Fake(URL("https://example.com/a"), 403)
        val transport = UrlConnectionTransport(connectionFactory = { connection })
        try { transport.openStream(connection.url.toString()); fail("Expected status failure") }
        catch (error: HttpStatusException) { assertEquals(403, error.status) }
        assertTrue(connection.inputClosed && connection.disconnected)
    }

    @Test fun `catalogue uses explicit app routes`() {
        assertEquals("/api/app/channels", ApiConfig.CHANNELS_PATH)
        assertEquals("/api/app/channels/abc", ApiConfig.channelUrl("abc"))
        assertTrue(ApiConfig.PRIMARY_BASE_URL.startsWith("https://"))
    }
}
