package com.soluplay.app

import android.content.Context
import android.content.res.AssetManager
import com.soluplay.core.api.CatalogueClient
import com.soluplay.core.browse.CatalogueRepository
import com.soluplay.core.browse.SeedCatalogue
import com.soluplay.core.browse.SeedProvider
import com.soluplay.core.cache.FileCatalogueCache
import java.io.File

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

    fun repository(context: Context): CatalogueRepository = CatalogueRepository(
        source = CatalogueClient(),
        cache = FileCatalogueCache(File(context.filesDir, CACHE_DIRECTORY)),
        seed = AssetsSeedProvider(context.assets, SEED_FILE),
    )
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
