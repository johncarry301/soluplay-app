package com.soluplay.app

import android.content.Context
import android.content.res.AssetManager
import com.soluplay.app.net.TlsSetup
import com.soluplay.core.api.CatalogueClient
import com.soluplay.core.api.UrlConnectionTransport
import com.soluplay.core.browse.CatalogueRepository
import com.soluplay.core.browse.SeedCatalogue
import com.soluplay.core.browse.SeedProvider
import com.soluplay.core.cache.FileCatalogueCache
import com.soluplay.core.ui.LogoLoader
import java.io.File
import javax.net.ssl.SSLSocketFactory

/**
 * What the app is built from.
 *
 * One repository, built once per surface, out of the live site, the device's
 * own saved copy and the catalogue baked into the APK.
 */
object AppGraph {

    /** The copy baked into the APK, made by scripts/make-seed.js. */
    const val SEED_FILE = "catalogue-channels.json"

    /** Where the last good copy is kept on the device. */
    const val CACHE_DIRECTORY = "catalogue"

    /** Where the copies of the channel logos are kept. */
    const val LOGO_CACHE_DIRECTORY = "logos"

    /** The secure context the repair is carried by, once it is installed. */
    @Volatile
    private var socketFactory: SSLSocketFactory? = null

    fun repository(context: Context): CatalogueRepository = CatalogueRepository(
        source = CatalogueClient(transport = transport()),
        cache = FileCatalogueCache(File(context.filesDir, CACHE_DIRECTORY)),
        seed = AssetsSeedProvider(context.assets, SEED_FILE),
    )

    /** The transport the app's own traffic goes through. */
    fun transport(): UrlConnectionTransport = UrlConnectionTransport(sslSocketFactory = socketFactory)

    /**
     * The things that have to be set up once per run.
     *
     * The logos are the only thing the app fetches for every channel it shows,
     * so their copies are kept on the device: the second run of the app does
     * not ask the network for them again.
     *
     * The certificate repair is installed here too, before anything reaches
     * the network: a television whose CA bundle was never updated turns away
     * the mirrors of several providers, and the repair is what lets them play.
     */
    fun prepare(context: Context) {
        LogoLoader.useDiskCache(File(context.cacheDir, LOGO_CACHE_DIRECTORY))
        socketFactory = TlsSetup.install(context)
    }
}

/** The catalogue that was baked into the APK. */
class AssetsSeedProvider(
    private val assets: AssetManager,
    private val fileName: String,
) : SeedProvider {

    override fun channelsJson(): String? {
        val body = runCatching {
            assets.open(fileName).use { it.readBytes().toString(Charsets.UTF_8) }
        }.getOrNull() ?: return null

        return SeedCatalogue.unwrap(body)
    }
}
