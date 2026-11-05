package com.soluplay.core

import com.soluplay.core.api.CatalogueBody
import com.soluplay.core.api.CatalogueClient
import com.soluplay.core.api.SoluPlayJson
import com.soluplay.core.api.ChannelsEnvelope
import com.soluplay.core.tls.ProviderTls
import com.soluplay.core.tls.TlsRefusal
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.Collections
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * The whole catalogue, host by host, against a television from 2018.
 *
 * This one reaches the network, so it is skipped unless it is asked for:
 *
 *   ./gradlew test -Dsoluplay.live=1
 *
 * It is the measurement the bundle was chosen by, repeated against the live
 * world: every host a player is led to is dialled, the chain it really sends
 * is put to this machine's store, to a box that stopped getting CA updates in
 * 2018, and to that same box through the app's own repair with the bundle the
 * APK ships.
 *
 * The property it checks is the one the phase is about: a host the 2018 box
 * turns away must be rescued by the bundle. A host turned away because its
 * certificate has run out is not the bundle's business — no root fixes that —
 * so it is reported and not counted against the bundle.
 *
 * Hostnames and certificate names only. Never a URL, never a query string.
 */
class LiveOldBoxHarnessTest {

    private val bundle: List<X509Certificate> by lazy {
        ProviderTls.readIssuers(repoFile("app/src/main/res/raw/provider_roots.pem").readText())
    }

    @Test
    fun `every host the app is led to is measured against a box from 2018`() = runBlocking {
        assumeTrue(System.getProperty("soluplay.live") == "1")

        val hosts = hostsTheAppIsLedTo()
        assertTrue("the catalogue gave no host to dial", hosts.isNotEmpty())
        println("hosts to dial: ${hosts.size}")

        val current = trustManager(null)
        val oldBox = oldBoxManager()
        assumeTrue("this machine's store does not show a 2018 box", oldBox != null)

        var rescued = 0
        var missed = 0
        var dated = 0
        for (host in hosts) {
            val chain = peerChain(host) ?: continue
            val onCurrent = refusal(current, chain)
            val onOldBox = refusal(oldBox!!, chain)
            val onRepair = refusal(ProviderTls.repairingManager(oldBox, bundle), chain)

            if (onOldBox == null) continue

            val reason = ProviderTls.reasonOf(onOldBox)
            if (reason == TlsRefusal.EXPIRED || reason == TlsRefusal.CLOCK) {
                dated++
                println("  $host: refused for a date, not for a missing root")
                continue
            }

            if (onRepair == null) {
                rescued++
                println("  $host: a 2018 box turns it away, the bundle rescues it")
            } else {
                missed++
                println("  $host: a 2018 box turns it away and the bundle does not - ${describe(onOldBox)}")
            }
            if (onCurrent != null) {
                println("    (this machine's own store turns it away too - ${describe(onCurrent)})")
            }
        }

        println("rescued by the bundle: $rescued, still refused: $missed, refused for a date: $dated")
        assertTrue("a host the bundle does not rescue: $missed", missed == 0)
    }

    // --- the hosts ---------------------------------------------------------------

    private suspend fun hostsTheAppIsLedTo(): List<String> {
        val client = CatalogueClient()
        val body = client.channelsJson()
        val envelope = SoluPlayJson.json.decodeFromString(ChannelsEnvelope.serializer(), body)
        assertTrue("the site reported a failure", envelope.success)

        val hosts = linkedSetOf<String>()
        envelope.channels.take(HOST_LIMIT).forEach { channel ->
            val detail = runCatching { client.channelJson(channel.id) }.getOrNull() ?: return@forEach
            val parsed = CatalogueBody.channel(detail) ?: return@forEach
            parsed.streams.forEach { stream ->
                hostOf(stream.url)?.let { hosts += it }
            }
        }
        return hosts.sorted()
    }

    private fun hostOf(url: String): String? {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
        val authority = trimmed.substringAfter("://").substringBefore('/').substringBefore('?')
        val host = authority.substringAfterLast('@').substringBefore(':')
        return host.lowercase().takeIf { it.isNotBlank() }
    }

    // --- the chains --------------------------------------------------------------

    /** The chain a host really sends, taken without letting any store decide. */
    private fun peerChain(host: String): Array<X509Certificate>? = runCatching {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf<TrustManager>(Unchecking()), java.security.SecureRandom())
        val raw = Socket()
        raw.connect(InetSocketAddress(host, 443), CONNECT_TIMEOUT_MS)
        raw.soTimeout = CONNECT_TIMEOUT_MS
        val socket = context.socketFactory.createSocket(raw, host, 443, true) as SSLSocket
        socket.soTimeout = CONNECT_TIMEOUT_MS
        socket.startHandshake()
        val chain = socket.session.peerCertificates.map { it as X509Certificate }.toTypedArray()
        socket.close()
        chain
    }.getOrNull()

    /** The refusal a store gave, or null when it took the chain. */
    private fun refusal(manager: X509TrustManager, chain: Array<X509Certificate>): Throwable? =
        runCatching { manager.checkServerTrusted(chain, authTypeOf(chain)) }.exceptionOrNull()

    /**
     * The key exchange the chain was served under.
     *
     * A store is asked about a chain with the name of the key exchange that
     * brought it, and a store that is asked with a name it does not know
     * turns the chain away before it looks at it. The key algorithm of the
     * certificate is not that name, so it is mapped to one.
     */
    private fun authTypeOf(chain: Array<X509Certificate>): String =
        when (chain.first().publicKey.algorithm.uppercase()) {
            "EC", "ECDSA" -> "ECDHE_ECDSA"
            "DSA" -> "DHE_DSS"
            else -> "RSA"
        }

    private fun describe(error: Throwable): String = buildString {
        var cause: Throwable? = error
        while (cause != null) {
            append(cause.javaClass.simpleName).append(": ")
            append(cause.message ?: "").append(' ')
            cause = cause.cause
        }
    }

    // --- the stores --------------------------------------------------------------

    private fun trustManager(store: KeyStore?): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(store)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    /**
     * The store a television that stopped getting CA updates has.
     *
     * It is this machine's own store with the roots the app now carries taken
     * out — those are the roots the box never had, which is the whole of the
     * repair's premise. Nothing else is touched: a root the box did have is
     * still there, so a host that only ever needed such a root is not counted
     * as a failure of the bundle.
     */
    private fun oldBoxManager(): X509TrustManager? {
        val current = loadCacerts() ?: return null
        val older = KeyStore.getInstance("JKS")
        older.load(null, null)

        val carried = bundle.map { it.subjectX500Principal.name }
        var dropped = 0
        for (alias in Collections.list(current.aliases())) {
            val certificate = current.getCertificate(alias) as? X509Certificate ?: continue
            if (carried.any { it.equals(certificate.subjectX500Principal.name, ignoreCase = true) }) {
                dropped++
                println("  the box never had: ${certificate.subjectX500Principal.name}")
                continue
            }
            older.setCertificateEntry(alias, certificate)
        }
        if (dropped == 0) return null

        return trustManager(older)
    }

    private fun loadCacerts(): KeyStore? {
        val file = File(System.getProperty("java.home") ?: "", "lib/security/cacerts")
        if (!file.isFile) return null

        for (type in listOf(KeyStore.getDefaultType(), "JKS", "PKCS12")) {
            val store = runCatching { KeyStore.getInstance(type) }.getOrNull() ?: continue
            val loaded = runCatching {
                file.inputStream().use { store.load(it, "changeit".toCharArray()) }
            }
            if (loaded.isSuccess) return store
        }
        return null
    }

    private fun repoFile(relative: String): File {
        var directory: File? = File(System.getProperty("user.dir") ?: ".")
        while (directory != null) {
            val candidate = File(directory, relative)
            if (candidate.isFile) return candidate
            directory = directory.parentFile
        }
        throw AssertionError("could not find $relative above ${System.getProperty("user.dir")}")
    }

    /** Harness-only: reads a chain so that the real stores can then be asked about it. */
    private class Unchecking : X509ExtendedTrustManager() {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = Unit
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: javax.net.ssl.SSLEngine?) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: javax.net.ssl.SSLEngine?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    private companion object {
        const val HOST_LIMIT = 60
        const val CONNECT_TIMEOUT_MS = 15_000
    }
}
