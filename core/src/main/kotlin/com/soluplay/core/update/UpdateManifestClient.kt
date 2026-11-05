package com.soluplay.core.update

import com.soluplay.core.api.ApiConfig
import com.soluplay.core.api.HttpTransport
import com.soluplay.core.api.SoluPlayJson
import com.soluplay.core.api.UrlConnectionTransport
import com.soluplay.core.model.UpdateManifest

/**
 * Where the app finds out whether there is a newer build.
 *
 * The site answers first, because the site is where a release is turned on and
 * turned off. The mirror answers second, because a site that is down must not
 * be able to stop an update from being seen — the mirror is the same document
 * on GitHub, and it costs nothing to keep.
 *
 * A body that cannot be read as a manifest is not a manifest: it is that
 * address failing, and the next one is tried.
 */
class UpdateManifestClient(
    baseUrls: List<String> = listOf(ApiConfig.PRIMARY_BASE_URL) + ApiConfig.BACKUP_BASE_URLS,
    private val mirrorUrl: String = ApiConfig.VERSION_MIRROR_URL,
    private val transport: HttpTransport = UrlConnectionTransport(),
) {

    private val addresses: List<String> = baseUrls.filter { it.isNotBlank() }

    /**
     * The manifest, or null when no address had one to give.
     *
     * It is not an error for there to be no manifest: an app that has never
     * been told about a newer build is simply up to date.
     */
    suspend fun manifest(): UpdateManifest? {
        for (address in addresses) {
            val body = runCatching {
                transport.get(address.trimEnd('/') + ApiConfig.VERSION_PATH)
            }.getOrNull() ?: continue
            val parsed = parse(body) ?: continue
            return parsed
        }

        val mirrored = runCatching { transport.get(mirrorUrl) }.getOrNull() ?: return null
        return parse(mirrored)
    }

    /**
     * The offer for this device, or null when there is nothing to offer.
     *
     * The offer is built from the manifest and the surface, and it says
     * nothing about whether it should be taken: that is the policy's job.
     */
    suspend fun offer(surface: String): UpdateOffer? {
        val manifest = manifest() ?: return null
        return UpdatePolicy.offerOf(manifest, surface)
    }

    private fun parse(body: String): UpdateManifest? {
        val manifest = runCatching {
            SoluPlayJson.json.decodeFromString(UpdateManifest.serializer(), body)
        }.getOrNull() ?: return null
        if (manifest.versionCode <= 0) return null
        return manifest
    }
}
