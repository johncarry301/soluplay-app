package com.soluplay.core.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
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
) : HttpTransport, JsonPoster {

    override suspend fun get(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            secureContext(connection)

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) throw HttpStatusException(status, body)
            body
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The one body the app posts to the site.
     *
     * The site's resolver is asked which video a channel is showing; nothing
     * else is posted, and no credential is ever attached.
     */
    override suspend fun postJson(url: String, body: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = connectTimeoutMs
            connection.readTimeout = readTimeoutMs
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            secureContext(connection)

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (status !in 200..299) throw HttpStatusException(status, text)
            text
        } finally {
            connection.disconnect()
        }
    }

    /**
     * The secure context this connection is made with.
     *
     * A plain HTTP connection has none, and saying so is cheaper than asking.
     */
    private fun secureContext(connection: HttpURLConnection) {
        val factory = sslSocketFactory ?: return
        if (connection is HttpsURLConnection) connection.sslSocketFactory = factory
    }

    companion object {
        /**
         * The site's own contract hides the download banner from this client,
         * so the app never sees a promo for itself.
         */
        const val USER_AGENT = "SoluPlayApp/0.1.0 (Android)"
    }
}
