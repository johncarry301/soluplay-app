package com.soluplay.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.FilterInputStream
import java.net.URI
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * The one way the app reaches the network.
 *
 * It is an interface so the browse layer can be exercised without a server:
 * the tests hand it a fixed body or a failure.
 */
interface HttpTransport {
    /** Returns the body, or throws. A status outside 2xx is a failure, never an empty body. */
    suspend fun get(url: String): String
}

/**
 * The site answered, but what it said cannot be used.
 *
 * This is not an outage: the site is up and has nothing to give. The screen
 * says which one it was, because "the site is down" and "the site has no
 * guide today" are different things to a viewer.
 */
open class SiteAnsweredException(message: String) : IOException(message)

/** A status the server answered with. The body is kept because the site puts its reason in it. */
class HttpStatusException(val status: Int, val body: String) :
    SiteAnsweredException("HTTP $status")

/** The site answered with a body the app cannot read as a guide. */
class UnusableAnswerException(message: String) : SiteAnsweredException(message)

/**
 * The real transport.
 *
 * The site is the only source of truth for the guide, so the request is plain
 * HTTPS with no credentials. A non-2xx answer raises rather than returning an
 * empty string, because an outage must never be mistaken for an empty
 * catalogue.
 *
 * The secure context it uses can be handed to it, so the app's own traffic
 * goes through the certificate repair even if the default one was replaced
 * by something else in the meantime.
 */
class UrlConnectionTransport(
    private val connectTimeoutMs: Int = 8_000,
    private val readTimeoutMs: Int = 12_000,
    private val sslSocketFactory: SSLSocketFactory? = null,
    private val connectionFactory: (URL) -> HttpsURLConnection = {
        it.openConnection() as HttpsURLConnection
    },
) : HttpTransport, JsonPoster {

    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        request(url).bufferedReader().use { it.readText() }
    }

    override suspend fun postJson(url: String, body: String): String = withContext(Dispatchers.IO) {
        request(url, body).bufferedReader().use { it.readText() }
    }

    /** Downloads may follow HTTPS CDN redirects; API requests remain same-origin. */
    fun openStream(url: String): InputStream = request(url, download = true)

    private fun request(url: String, body: String? = null, download: Boolean = false): InputStream {
        val origin = requireSecureUrl(url)
        var target = origin
        var redirects = 0
        while (true) {
            val connection = connectionFactory(target)
            var handedOff = false
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMs
                connection.readTimeout = readTimeoutMs
                // Configure trust before any operation that can connect, especially outputStream.
                sslSocketFactory?.let { connection.sslSocketFactory = it }
                // Keep the platform hostname verifier intact.
                connection.requestMethod = if (body == null) "GET" else "POST"
                connection.setRequestProperty("Accept", if (download) "*/*" else "application/json")
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (body != null) {
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                if (status in REDIRECT_STATUSES) {
                    // Close the redirect response even when its target or semantics are refused.
                    connection.inputStream.close()
                    if (body != null && status != 307 && status != 308) {
                        throw IOException("Unsafe POST redirect")
                    }
                    if (redirects >= MAX_REDIRECTS) throw IOException("Too many redirects")
                    val location = connection.getHeaderField("Location")
                        ?.takeIf { it.isNotBlank() } ?: throw IOException("Missing redirect location")
                    val next = requireSecureUrl(URL(target, location).toExternalForm())
                    if (!download && !sameOrigin(origin, next)) throw IOException("Cross-origin API redirect")
                    target = next
                    redirects++
                    continue
                }
                if (status !in 200..299) {
                    val text = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                    throw HttpStatusException(status, text)
                }
                val input = connection.inputStream
                val result = object : FilterInputStream(input) {
                    override fun close() {
                        try {
                            super.close()
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
                handedOff = true
                return result
            } finally {
                if (!handedOff) connection.disconnect()
            }
        }
    }

    private fun sameOrigin(first: URL, second: URL): Boolean =
        first.protocol.equals(second.protocol, ignoreCase = true) &&
            first.host.equals(second.host, ignoreCase = true) &&
            (if (first.port == -1) first.defaultPort else first.port) ==
            (if (second.port == -1) second.defaultPort else second.port)

    companion object {
        internal const val MAX_REDIRECTS = 5
        private val REDIRECT_STATUSES = setOf(301, 302, 303, 307, 308)

        internal fun requireSecureUrl(value: String): URL {
            val uri = try {
                URI(value)
            } catch (error: Exception) {
                throw IOException("Invalid HTTPS URL", error)
            }
            if (!uri.scheme.equals("https", ignoreCase = true) ||
                uri.host.isNullOrBlank() || uri.rawUserInfo != null ||
                uri.port < -1 || uri.port > 65535
            ) throw IOException("A credential-free HTTPS URL is required")
            return uri.toURL()
        }

        /**
         * The site's own contract hides the download banner from this client,
         * so the app never sees a promo for itself.
         */
        const val USER_AGENT = "SoluPlayApp/0.1.0 (Android)"
    }
}
