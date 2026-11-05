package com.soluplay.app

import android.content.Context
import android.content.res.AssetManager
import android.os.Build
import com.soluplay.app.net.TlsSetup
import com.soluplay.app.update.ApkInstaller
import com.soluplay.app.update.UpdateStore
import com.soluplay.app.update.UpdateViewModelFactory
import com.soluplay.core.api.CatalogueClient
import com.soluplay.core.api.UrlConnectionTransport
import com.soluplay.core.browse.CatalogueRepository
import com.soluplay.core.browse.SeedCatalogue
import com.soluplay.core.browse.SeedProvider
import com.soluplay.core.cache.FileCatalogueCache
import com.soluplay.core.favourites.FileFavouriteStore
import com.soluplay.core.favourites.FavouritesRepository
import com.soluplay.core.favourites.FavouritesViewModelFactory
import com.soluplay.core.favourites.HttpFavouriteSync
import com.soluplay.core.ui.LogoLoader
import com.soluplay.core.ui.UiLanguageStore
import com.soluplay.core.update.ApkDownloader
import com.soluplay.core.update.UpdateManifestClient
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

    /** Where a downloaded build is kept until it is installed. */
    const val UPDATE_DIRECTORY = "updates"

    /** Where the viewer's own list of favourites is kept. */
    const val FAVOURITES_DIRECTORY = "favourites"

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

    /**
     * The updater, built out of the app's own parts.
     *
     * The manifest is asked for and the build is fetched over the same
     * transport as the rest of the app's traffic, so both go through the
     * certificate repair on a television whose trust store was never updated.
     */
    fun updater(context: Context): UpdateViewModelFactory {
        val transport = transport()
        return UpdateViewModelFactory(
            manifestClient = UpdateManifestClient(transport = transport),
            downloader = ApkDownloader(open = transport::openStream),
            installer = ApkInstaller(context),
            store = UpdateStore(context),
            downloadDirectory = File(context.filesDir, UPDATE_DIRECTORY),
            surface = surfaceOf(context),
            currentVersionCode = versionCodeOf(context),
        )
    }

    /**
     * Which build of the app this is.
     *
     * The surface is the package, not the launcher: the shipped APK carries
     * the television launcher as well, and it is the same file either way.
     */
    fun surfaceOf(context: Context): String =
        if (context.packageName.endsWith(".tv")) "tv" else "phone"

    /**
     * The favourites list, built out of the app's own parts.
     *
     * The list is kept on the device and copied to the site under a key the
     * viewer carries, so the app needs no account and asks for no credential.
     * The site is asked over the same transport as everything else, so a
     * television whose trust store was never updated still reaches it.
     */
    fun favourites(context: Context): FavouritesViewModelFactory {
        val transport = transport()
        return FavouritesViewModelFactory(
            FavouritesRepository(
                store = FileFavouriteStore(File(context.filesDir, FAVOURITES_DIRECTORY)),
                sync = HttpFavouriteSync(reader = transport, poster = transport),
            ),
        )
    }

    /** The language the chrome speaks, and the choice that is remembered. */
    fun language(context: Context): UiLanguageStore = UiLanguageStore(context)

    /** The build number of the app that is running. */
    private fun versionCodeOf(context: Context): Int {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
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
