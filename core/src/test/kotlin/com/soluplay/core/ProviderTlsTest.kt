package com.soluplay.core

import com.soluplay.core.tls.ProviderTls
import com.soluplay.core.tls.TlsRefusal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.net.Socket
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate
import java.util.Collections
import java.util.Date
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLPeerUnverifiedException
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509ExtendedTrustManager
import javax.net.ssl.X509TrustManager

/**
 * The repair for a television whose CA bundle was never updated.
 *
 * The proof is the same bytes twice: a chain a 2018 box cannot finish, and the
 * same chain once the certificates this app carries are put back into it. The
 * first is refused, the second is accepted, and nothing is turned off to make
 * that happen — a certificate that only claims the hostname is still turned
 * away, and a certificate that has run out is still refused.
 */
class ProviderTlsTest {

    private val bundle: List<X509Certificate> by lazy {
        ProviderTls.readIssuers(repoFile("app/src/main/res/raw/provider_roots.pem").readText())
    }

    private val aynaottChain: List<X509Certificate> by lazy {
        listOf(
            certificate("aynaott-leaf.pem"),
            certificate("aynaott-intermediate.pem"),
            certificate("aynaott-root.pem"),
        )
    }

    private val sofastChain: List<X509Certificate> by lazy {
        listOf(certificate("sofast-leaf.pem"), certificate("sofast-intermediate.pem"))
    }

    private val alixbdChain: List<X509Certificate> by lazy {
        listOf(certificate("alixbd-leaf.pem"))
    }

    @Test
    fun `the bundle carries the three roots it is for`() {
        assertEquals(3, bundle.size)
        assertTrue(named(bundle, "Sectigo Public Server Authentication CA DV R36"))
        assertTrue(named(bundle, "ISRG Root X1"))
        assertTrue(named(bundle, "DigiCert Global Root G3"))
    }

    @Test
    fun `every certificate in the bundle is a certificate authority`() {
        bundle.forEach { certificate ->
            assertTrue("a bundled certificate is not a CA", certificate.basicConstraints >= 0)
        }
    }

    @Test
    fun `a television from 2018 cannot finish the chain the site sends`() {
        val oldBox = oldBoxManager()
        assumeTrue("this machine's store does not show a 2018 box", oldBox != null)

        assertTrue("the box accepted the chain as sent", refuses(oldBox!!, aynaottChain))
        assertTrue("the box accepted the chain as sent", refuses(oldBox, sofastChain))
        assertTrue("the box accepted the chain as sent", refuses(oldBox, alixbdChain))
    }

    @Test
    fun `the repair accepts the chain the television could not finish`() {
        val oldBox = oldBoxManager()
        assumeTrue("this machine's store does not show a 2018 box", oldBox != null)

        val repair = ProviderTls.repairingManager(oldBox!!, bundle) { pinnedDate }

        repair.checkServerTrusted(aynaottChain.toTypedArray(), "EC")

        assertTrue("the aynaott chain was refused", repair.lastRepaired)
        assertNull("a refusal was recorded although the chain was accepted", repair.lastRefusal)
    }

    @Test
    fun `the repair accepts the chain the site cut short`() {
        val oldBox = oldBoxManager()
        assumeTrue("this machine's store does not show a 2018 box", oldBox != null)

        val repair = ProviderTls.repairingManager(oldBox!!, bundle) { pinnedDate }

        repair.checkServerTrusted(alixbdChain.toTypedArray(), "RSA")

        assertTrue("the alixbd chain was refused", repair.lastRepaired)
    }

    @Test
    fun `the repair accepts a chain that ends at a root the box has never seen`() {
        val repair = ProviderTls.repairingManager(refusing(), bundle) { pinnedDate }

        repair.checkServerTrusted(sofastChain.toTypedArray(), "EC")

        assertTrue("the sofast chain was refused", repair.lastRepaired)
    }

    @Test
    fun `a certificate that only claims the name is still turned away`() {
        val repair = ProviderTls.repairingManager(refusing(), bundle) { pinnedDate }

        val refused = runCatching {
            repair.checkServerTrusted(arrayOf(certificate("fabricated.pem")), "RSA")
        }.exceptionOrNull()

        assertNotNull("a fabricated certificate was accepted", refused)
        assertTrue(
            "the failure was not a certificate failure",
            ProviderTls.isCertificateFailure(refused),
        )
    }

    @Test
    fun `the same bytes are accepted while the certificate is still good`() {
        val repair = ProviderTls.repairingManager(refusing(), bundle) { pinnedDate }

        repair.checkServerTrusted(aynaottChain.toTypedArray(), "EC")

        assertTrue("the chain was refused while it was still good", repair.lastRepaired)
    }

    @Test
    fun `a certificate that has run out is not rescued`() {
        // A box that can finish the chain is asked first, so the reason it
        // gives is the real one: the leaf was good until 3 November 2026.
        val repair = ProviderTls.repairingManager(storeWith(bundle), bundle) { Date() }

        val refused = runCatching {
            repair.checkServerTrusted(aynaottChain.toTypedArray(), "EC")
        }.exceptionOrNull()

        assertNotNull("an expired certificate was accepted", refused)
        assertEquals(TlsRefusal.EXPIRED, repair.lastRefusal)
    }

    @Test
    fun `the platform is asked first and nothing else runs when it says yes`() {
        val platform = CountingTrustManager(accepting())
        val repair = ProviderTls.repairingManager(platform, bundle) { pinnedDate }

        repair.checkServerTrusted(aynaottChain.toTypedArray(), "EC")

        assertTrue("the platform was not asked", platform.calls > 0)
        assertFalse("the repair ran although the platform said yes", repair.lastRepaired)
    }

    @Test
    fun `the repair only runs in the platform's refusal path`() {
        val platform = CountingTrustManager(refusing())
        val repair = ProviderTls.repairingManager(platform, bundle) { pinnedDate }

        repair.checkServerTrusted(aynaottChain.toTypedArray(), "EC")

        assertTrue("the platform was not asked", platform.calls > 0)
        assertTrue("the repair did not run", repair.lastRepaired)
    }

    @Test
    fun `the repair adds no issuer the platform does not already have`() {
        val platform = storeWith(bundle)
        val repair = ProviderTls.repairingManager(platform, bundle) { pinnedDate }

        assertEquals(platform.acceptedIssuers.size, repair.acceptedIssuers.size)
    }

    @Test
    fun `a chain the platform already accepts needs no repair`() {
        val repair = ProviderTls.repairingManager(accepting(), bundle) { pinnedDate }

        repair.checkServerTrusted(aynaottChain.toTypedArray(), "EC")

        assertNull("a refusal was recorded although the chain was accepted", repair.lastRefusal)
    }

    @Test
    fun `the four reasons are told apart`() {
        assertEquals(
            TlsRefusal.EXPIRED,
            ProviderTls.reasonOf(CertificateExpiredException("NotAfter: Mon Nov 03 10:58:12 GMT 2026")),
        )
        assertEquals(
            TlsRefusal.CLOCK,
            ProviderTls.reasonOf(
                CertificateNotYetValidException("NotBefore: Mon Nov 03 10:58:12 GMT 2026"),
            ),
        )
        assertEquals(
            TlsRefusal.ADDRESS,
            ProviderTls.reasonOf(SSLPeerUnverifiedException("No name matching alixbd.com found")),
        )
        assertEquals(
            TlsRefusal.ISSUER,
            ProviderTls.reasonOf(javax.net.ssl.SSLHandshakeException("Chain validation failed")),
        )
    }

    @Test
    fun `a certificate failure is told apart from a bad moment`() {
        assertTrue(ProviderTls.isCertificateFailure(CertificateExpiredException("gone")))
        assertTrue(
            ProviderTls.isCertificateFailure(
                IllegalStateException("wrapped", SSLPeerUnverifiedException("no name matching")),
            ),
        )
        assertFalse(ProviderTls.isCertificateFailure(java.io.IOException("no route to host")))
        assertFalse(ProviderTls.isCertificateFailure(null))
    }

    // --- the helpers -------------------------------------------------------------

    /** The date every chain in this file was good on. */
    private val pinnedDate = Date(1_792_454_400_000L)

    private fun named(bundle: List<X509Certificate>, commonName: String): Boolean =
        bundle.any { it.subjectX500Principal.name.contains("CN=$commonName") }

    private fun certificate(name: String): X509Certificate =
        ProviderTls.readIssuers(repoFile("core/src/test/resources/tls/$name").readText()).first()

    private fun refuses(manager: X509TrustManager, chain: List<X509Certificate>): Boolean =
        runCatching {
            manager.checkServerTrusted(chain.toTypedArray(), chain.first().publicKey.algorithm)
        }.exceptionOrNull() != null

    /** A box with no root to end at, which is the case the repair exists for. */
    private fun refusing(): X509ExtendedTrustManager = object : X509ExtendedTrustManager() {
        private fun refuse(): Nothing =
            throw CertificateException("PKIX path building failed: unable to find valid certification path")

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = refuse()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = refuse()
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) = refuse()
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** A box that says yes to everything, so only the platform's word counts. */
    private fun accepting(): X509ExtendedTrustManager = object : X509ExtendedTrustManager() {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) = Unit
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) = Unit
        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) = Unit
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
    }

    /** A platform manager that is watched, so the ordering can be proved. */
    private class CountingTrustManager(private val delegate: X509ExtendedTrustManager) :
        X509ExtendedTrustManager() {

        var calls: Int = 0
            private set

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
            delegate.checkClientTrusted(chain, authType)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            calls++
            delegate.checkServerTrusted(chain, authType)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
            delegate.checkClientTrusted(chain, authType, socket)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, socket: Socket?) {
            calls++
            delegate.checkServerTrusted(chain, authType, socket)
        }

        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            delegate.checkClientTrusted(chain, authType, engine)
        }

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String, engine: SSLEngine?) {
            calls++
            delegate.checkServerTrusted(chain, authType, engine)
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = delegate.acceptedIssuers
    }

    /** A store that holds exactly the certificates it is given. */
    private fun storeWith(certificates: List<X509Certificate>): X509TrustManager {
        val store = KeyStore.getInstance("JKS")
        store.load(null, null)
        certificates.forEachIndexed { index, certificate ->
            store.setCertificateEntry("bundled-$index", certificate)
        }
        return managerFor(store)
    }

    /**
     * The store a television that stopped getting CA updates has.
     *
     * It is this machine's own store with the roots those boxes never saw taken
     * out — the 2019 and 2021 generations, which is exactly what a box from
     * 2018 is missing.
     */
    private fun oldBoxManager(): X509TrustManager? {
        val current = loadCacerts() ?: return null
        val older = KeyStore.getInstance("JKS")
        older.load(null, null)

        var dropped = 0
        for (alias in Collections.list(current.aliases())) {
            val certificate = current.getCertificate(alias) as? X509Certificate ?: continue
            if (RECENT_ROOTS.containsMatchIn(certificate.subjectX500Principal.name)) {
                dropped++
                continue
            }
            older.setCertificateEntry(alias, certificate)
        }
        if (dropped == 0) return null

        return managerFor(older)
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

    private fun managerFor(store: KeyStore): X509TrustManager {
        val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        factory.init(store)
        return factory.trustManagers.filterIsInstance<X509TrustManager>().first()
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

    private companion object {
        /** The roots a 2018 television never received. */
        val RECENT_ROOTS = Regex(
            "ISRG Root X|Public Server Authentication|DigiCert Global Root G3|GTS Root R",
            RegexOption.IGNORE_CASE,
        )
    }
}
