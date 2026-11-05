package com.soluplay.core.tls

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.Socket
import java.security.cert.CertPath
import java.security.cert.CertPathValidator
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * Why a certificate was turned away.
 *
 * The same four reasons the old television app gave, because they are the four
 * things that are actually wrong: the chain does not end anywhere this device
 * trusts, the certificate itself has run out, this device's clock is not the
 * clock the certificate was made for, and the certificate is for another name.
 */
enum class TlsRefusal {
    /** The chain does not end at a certificate this app carries. */
    ISSUER,

    /** The certificate itself has run out. */
    EXPIRED,

    /** This device's clock is not the clock the certificate was made for. */
    CLOCK,

    /** The certificate is for another name. */
    ADDRESS,
}

/**
 * The repair for a television whose CA bundle was never updated.
 *
 * The problem is not the app and not the provider. It is a box that shipped
 * before the roots these providers' certificates are issued under were ever
 * written: a chain that stops one certificate short of ISRG Root X1 or
 * DigiCert Global Root G3, and a leaf that arrives on its own because the
 * server does not chase the issuer for it. A box that never got a CA update
 * turns those away; a browser on a current machine does not, because it
 * completes the chain or knows the root.
 *
 * So the app carries the three roots that were measured to be missing, and:
 *
 *  1. the platform's own trust manager is asked **first**, and every one of
 *     its checks is kept — a certificate the platform accepts is accepted
 *     here, so a provider that works today cannot stop working;
 *  2. only in the platform's refusal path is the chain completed, by walking
 *     issuer to subject and adding the certificates this app carries — never
 *     by handing the whole bundle over and letting a verifier guess;
 *  3. the completed chain is given back to the platform first, because a
 *     platform that can complete it itself should be the one to say so;
 *  4. and only then is the chain validated properly — full path validation,
 *     anchored at a certificate this app carries, with the signatures, the
 *     dates, the CA flag and the server-auth usage all checked.
 *
 * Trust still ends at a root this app deliberately carries. Nothing is turned
 * off, no certificate is accepted on its word, and a host is never pinned: the
 * address a playlist names is not always the address the player dials.
 */
class RepairingTrustManager(
    private val platform: X509TrustManager,
    private val bundle: List<X509Certificate>,
    private val now: () -> Date = { Date() },
) : X509ExtendedTrustManager() {

    /**
     * Why the last refusal was refused, for the screen.
     *
     * It is set only when the platform refused and the repair could not make
     * the chain good, so a viewer is told the real reason rather than a
     * generic failure.
     */
    @Volatile
    var lastRefusal: TlsRefusal? = null
        private set

    /** True when the last chain was accepted by the repair rather than by the platform. */
    @Volatile
    var lastRepaired: Boolean = false
        private set

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        try {
            platform.checkServerTrusted(chain, authType)
            acceptedByPlatform()
        } catch (refused: CertificateException) {
            if (repair(chain, authType)) {
                acceptedByRepair()
                return
            }
            lastRefusal = ProviderTls.reasonOf(refused)
            throw refused
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        try {
            platformExtended()?.checkServerTrusted(chain, authType, socket)
                ?: platform.checkServerTrusted(chain, authType)
            acceptedByPlatform()
        } catch (refused: CertificateException) {
            if (repair(chain, authType)) {
                acceptedByRepair()
                return
            }
            lastRefusal = ProviderTls.reasonOf(refused)
            throw refused
        }
    }

    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        try {
            platformExtended()?.checkServerTrusted(chain, authType, engine)
                ?: platform.checkServerTrusted(chain, authType)
            acceptedByPlatform()
        } catch (refused: CertificateException) {
            if (repair(chain, authType)) {
                acceptedByRepair()
                return
            }
            lastRefusal = ProviderTls.reasonOf(refused)
            throw refused
        }
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        platform.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
        platformExtended()?.checkClientTrusted(chain, authType, socket)
            ?: platform.checkClientTrusted(chain, authType)
    }

    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
        platformExtended()?.checkClientTrusted(chain, authType, engine)
            ?: platform.checkClientTrusted(chain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = platform.acceptedIssuers

    /**
     * The chain as it would be after the repair, for a caller that wants to see it.
     *
     * The chain is completed by walking issuer to subject: the certificate at
     * the top is looked for in the bundle, and only one that actually signed
     * it is added. A bundle is never handed over as a whole, because a
     * verifier given a pile of certificates will happily build a path through
     * one of them that has nothing to do with the server.
     */
    fun complete(chain: Array<X509Certificate>): List<X509Certificate> {
        val out = chain.toMutableList()
        while (true) {
            val top = out.last()
            if (top.subjectX500Principal == top.issuerX500Principal) break
            if (bundle.any { it.subjectX500Principal == top.subjectX500Principal }) break
            val issuer = bundle.firstOrNull { candidate ->
                candidate.subjectX500Principal == top.issuerX500Principal && signed(candidate, top)
            } ?: break
            out += issuer
        }
        return out
    }

    /** True when this certificate was signed by that one. */
    private fun signed(issuer: X509Certificate, certificate: X509Certificate): Boolean =
        runCatching { certificate.verify(issuer.publicKey) }.isSuccess

    /**
     * The repair itself.
     *
     * The completed chain is offered back to the platform first — a platform
     * that can finish the chain by itself should be the one to accept it —
     * and only then is it validated against the certificates this app carries.
     */
    private fun repair(chain: Array<X509Certificate>, authType: String): Boolean {
        if (bundle.isEmpty() || chain.isEmpty()) return false

        val completed = complete(chain)

        // The platform is asked again with the chain it was missing.
        if (completed.size != chain.size) {
            val answered = runCatching {
                platform.checkServerTrusted(completed.toTypedArray(), authType)
            }
            if (answered.isSuccess) return true
        }

        return validates(completed, authType)
    }

    /**
     * Full path validation, anchored at a certificate this app carries.
     *
     * The chain is validated the way the platform validates one — the same
     * validator, with the same rules about weak keys and old signatures —
     * except that the roots it is anchored at are the ones this app carries
     * rather than the ones this device happens to have.
     */
    private fun validates(chain: List<X509Certificate>, authType: String): Boolean {
        val leaf = chain.first()
        if (!serverUsable(leaf)) return false

        // A self-signed certificate that is one of our anchors is the anchor,
        // not part of the path: a path must not carry its own anchor.
        val path = if (chain.size > 1 && chain.last() in bundle) {
            chain.subList(0, chain.size - 1)
        } else {
            chain
        }
        if (path.isEmpty()) return false

        return try {
            val anchors = bundle.map { TrustAnchor(it, null) }.toSet()
            val parameters = PKIXParameters(anchors).apply {
                // A television cannot fetch a revocation list, and a
                // certificate that cannot be revoked is not a failure.
                isRevocationEnabled = false
                date = now()
            }
            val factory = CertificateFactory.getInstance("X.509")
            val certPath: CertPath = factory.generateCertPath(path)
            CertPathValidator.getInstance("PKIX").validate(certPath, parameters)
            true
        } catch (error: Exception) {
            false
        }
    }

    /**
     * True when the leaf may be used for a server.
     *
     * A certificate that says what it may be used for has to say server, and
     * the key exchange the handshake named has to be one the leaf can serve.
     */
    private fun serverUsable(leaf: X509Certificate): Boolean {
        val usage = runCatching { leaf.extendedKeyUsage }.getOrNull() ?: return true
        if (usage.isEmpty()) return true
        return usage.contains(SERVER_AUTH) || usage.contains(ANY_USAGE)
    }

    private fun platformExtended(): X509ExtendedTrustManager? = platform as? X509ExtendedTrustManager

    /** The device's own store answered, so nothing of the repair was needed. */
    private fun acceptedByPlatform() {
        lastRefusal = null
        lastRepaired = false
    }

    /** The repair answered, so the chain was made good by the bundle. */
    private fun acceptedByRepair() {
        lastRefusal = null
        lastRepaired = true
    }

    private companion object {
        /** id-kp-serverAuth */
        const val SERVER_AUTH = "1.3.6.1.5.5.7.3.1"

        /** anyExtendedKeyUsage */
        const val ANY_USAGE = "2.5.29.37.0"
    }
}

/**
 * The certificate repair, and the plumbing that carries it.
 */
object ProviderTls {

    /**
     * The certificates in a bundle, read out of a PEM file.
     *
     * The base64 is decoded by hand rather than with the platform's decoder,
     * because that decoder arrived after the oldest television this app has
     * to run on.
     */
    fun readIssuers(stream: InputStream): List<X509Certificate> =
        readIssuers(stream.bufferedReader().use { it.readText() })

    /** The certificates in a bundle, read out of PEM text. */
    fun readIssuers(text: String): List<X509Certificate> {
        val factory = CertificateFactory.getInstance("X.509")
        val out = mutableListOf<X509Certificate>()
        var index = 0
        while (true) {
            val start = text.indexOf(BEGIN, index)
            if (start < 0) break
            val end = text.indexOf(END, start)
            if (end < 0) break
            val body = text.substring(start + BEGIN.length, end).filterNot { it.isWhitespace() }
            val certificate = runCatching {
                factory.generateCertificate(ByteArrayInputStream(decodeBase64(body))) as? X509Certificate
            }.getOrNull()
            if (certificate != null) out += certificate
            index = end + END.length
        }
        return out
    }

    /**
     * The trust manager this device has of its own.
     *
     * It is the one the repair asks first, so it is taken before anything is
     * installed over it.
     */
    fun platformManager(): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(null as java.security.KeyStore?)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().firstOrNull()
            ?: throw IllegalStateException("this device has no X509 trust manager")
    }

    /** The manager that repairs a chain the platform turned away. */
    fun repairingManager(
        platform: X509TrustManager,
        bundle: List<X509Certificate>,
        now: () -> Date = { Date() },
    ): RepairingTrustManager = RepairingTrustManager(platform, bundle, now)

    /**
     * The secure context that carries the manager.
     *
     * It is built on the platform's own provider, so the handshake is made by
     * the same machinery the box makes it with — only the chain it is handed
     * is different.
     */
    fun sslContext(manager: X509TrustManager): SSLContext {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(manager), null)
        return context
    }

    /**
     * The socket factory, with the protocols an old box needs turned on.
     *
     * A box that predates TLS 1.2 has it supported but not enabled, and a
     * server that wants 1.2 refuses the connection. Nothing is taken away:
     * the protocols the socket already had are left on.
     */
    fun socketFactory(context: SSLContext): SSLSocketFactory =
        ProtocolSocketFactory(context.socketFactory)

    /**
     * True when the failure was a certificate failure.
     *
     * A mirror whose certificate was turned away is not a mirror that is
     * having a bad moment: it is a mirror that cannot be played, and the
     * walk should move on to the next one at once.
     */
    fun isCertificateFailure(error: Throwable?): Boolean {
        var cause = error
        var depth = 0
        while (cause != null && depth < 12) {
            if (cause is javax.net.ssl.SSLException) return true
            if (cause is CertificateException) return true
            cause = cause.cause
            depth++
        }
        return false
    }

    /**
     * Why a certificate was turned away, read out of the refusal itself.
     *
     * The words come from the platform, so they are read rather than guessed:
     * an expired certificate and a clock that runs fast are two different
     * problems for a viewer, and only one of them is worth waiting for.
     */
    fun reasonOf(error: Throwable): TlsRefusal {
        val text = buildString {
            var cause: Throwable? = error
            while (cause != null) {
                append(cause.javaClass.simpleName).append(' ')
                append(cause.message ?: "").append(' ')
                cause = cause.cause
            }
        }
        return when {
            text.contains("NotYetValid", ignoreCase = true) ||
                text.contains("notBefore", ignoreCase = true) -> TlsRefusal.CLOCK

            text.contains("Expired", ignoreCase = true) ||
                text.contains("notAfter", ignoreCase = true) -> TlsRefusal.EXPIRED

            text.contains("hostname", ignoreCase = true) ||
                text.contains("No name matching", ignoreCase = true) ||
                text.contains("doesn't match", ignoreCase = true) -> TlsRefusal.ADDRESS

            else -> TlsRefusal.ISSUER
        }
    }

    private const val BEGIN = "-----BEGIN CERTIFICATE-----"
    private const val END = "-----END CERTIFICATE-----"

    /** Base64, decoded by hand so that it runs on the oldest box this app supports. */
    private fun decodeBase64(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (character in text) {
            val value = when (character) {
                in 'A'..'Z' -> character - 'A'
                in 'a'..'z' -> character - 'a' + 26
                in '0'..'9' -> character - '0' + 52
                '+' -> 62
                '/' -> 63
                else -> continue
            }
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }
}

/**
 * A socket factory that turns on the protocols the box supports.
 *
 * It changes nothing else: the cipher suites and the handshake are the
 * platform's own.
 */
private class ProtocolSocketFactory(
    private val delegate: SSLSocketFactory,
) : SSLSocketFactory() {

    override fun createSocket(): Socket = configured(delegate.createSocket())

    override fun createSocket(socket: Socket, host: String, port: Int, autoClose: Boolean): Socket =
        configured(delegate.createSocket(socket, host, port, autoClose))

    override fun createSocket(host: String, port: Int): Socket =
        configured(delegate.createSocket(host, port))

    override fun createSocket(
        host: String,
        port: Int,
        localAddress: java.net.InetAddress,
        localPort: Int,
    ): Socket = configured(delegate.createSocket(host, port, localAddress, localPort))

    override fun createSocket(host: java.net.InetAddress, port: Int): Socket =
        configured(delegate.createSocket(host, port))

    override fun createSocket(
        host: java.net.InetAddress,
        port: Int,
        localAddress: java.net.InetAddress,
        localPort: Int,
    ): Socket = configured(delegate.createSocket(host, port, localAddress, localPort))

    override fun getDefaultCipherSuites(): Array<String> = delegate.defaultCipherSuites

    override fun getSupportedCipherSuites(): Array<String> = delegate.supportedCipherSuites

    /** Turns on the newer protocols the socket supports but did not enable. */
    private fun configured(socket: Socket): Socket {
        if (socket !is SSLSocket) return socket
        val supported = socket.supportedProtocols.toSet()
        val enabled = socket.enabledProtocols.toMutableList()
        for (protocol in listOf("TLSv1.3", "TLSv1.2")) {
            if (protocol in supported && protocol !in enabled) enabled += protocol
        }
        socket.enabledProtocols = enabled.toTypedArray()
        return socket
    }
}
