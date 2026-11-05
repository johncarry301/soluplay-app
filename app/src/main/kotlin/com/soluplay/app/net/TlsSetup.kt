package com.soluplay.app.net

import android.content.Context
import com.soluplay.app.R
import com.soluplay.core.tls.ProviderTls
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocketFactory

/**
 * The certificate repair, installed once per run.
 *
 * The app talks to the site and to mirror hosts that no one controls, and a
 * television that never updated its CA bundle turns some of those away. The
 * repair is put in place here, before anything reaches the network, and it is
 * put in place for the whole process: the player reaches its mirrors over the
 * platform's own HTTP stack, which takes its secure context from the default
 * one, and there is no other way in.
 */
object TlsSetup {

    /**
     * The certificates the app carries.
     *
     * They are the three roots that were measured to be missing on the boxes
     * this app has to run on: the two self-signed roots the chains of one
     * family of providers stop short of, and the intermediate one family
     * leaves out of its chains altogether.
     */
    fun bundle(context: Context): List<X509Certificate> = runCatching {
        context.resources.openRawResource(R.raw.provider_roots).use {
            ProviderTls.readIssuers(it)
        }
    }.getOrNull().orEmpty()

    /**
     * Installs the repair and returns the socket factory that carries it.
     *
     * The platform's own trust manager is taken first and is asked first, so
     * a device that trusts a provider already keeps trusting it.
     */
    fun install(context: Context): SSLSocketFactory? {
        val issuers = bundle(context)
        if (issuers.isEmpty()) return null

        val platform = runCatching { ProviderTls.platformManager() }.getOrNull() ?: return null
        val manager = ProviderTls.repairingManager(platform, issuers)
        val factory = ProviderTls.socketFactory(ProviderTls.sslContext(manager))

        // The whole process, because the player reaches its mirrors through
        // the platform's HTTP stack and that stack takes its secure context
        // from the default one.
        HttpsURLConnection.setDefaultSSLSocketFactory(factory)
        return factory
    }
}
