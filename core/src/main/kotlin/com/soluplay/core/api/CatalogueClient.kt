package com.soluplay.core.api

import java.io.IOException

/**
 * The public routes the app reads, as raw JSON.
 *
 * Raw bodies keep the network out of the browse logic: the repository decides
 * what a body means, and a test can hand it one without a server.
 */
interface CatalogueSource {
    suspend fun channelsJson(): String
    suspend fun fixturesJson(): String

    /**
     * The channel and its mirror list, as the detail route publishes it.
     *
     * This is the only route that carries stream addresses, and the only one
     * the player may use: the saved copy and the baked seed have no ladder.
     */
    suspend fun channelJson(id: String): String

    /**
     * The guide and the board in one body, or null when the site has no such
     * route.
     *
     * The default is null, so a source that only knows the older routes keeps
     * working and the app falls back to them.
     */
    suspend fun bootstrapJson(): String? = null
}

/**
 * Reads the public catalogue from the site, trying each base address in turn.
 *
 * The primary address is the site itself; the backups exist so a domain that
 * stops answering cannot take the app down with it. A failure of one address
 * moves on to the next, and the last failure is the one that surfaces.
 *
 * An address that answers with a body the app cannot read is a failure of
 * that address, not of the app: the next one is tried, because a domain that
 * is up but serving nothing is exactly the case a backup exists for.
 */
class CatalogueClient(
    baseUrls: List<String> = listOf(ApiConfig.PRIMARY_BASE_URL) + ApiConfig.BACKUP_BASE_URLS,
    private val transport: HttpTransport = UrlConnectionTransport(),
) : CatalogueSource {

    private val addresses: List<String> = baseUrls.filter { it.isNotBlank() }

    override suspend fun channelsJson(): String =
        read(ApiConfig.CHANNELS_PATH) { CatalogueBody.channels(it) != null }

    override suspend fun fixturesJson(): String =
        read(ApiConfig.FIXTURES_PATH) { CatalogueBody.fixtures(it) != null }

    override suspend fun channelJson(id: String): String =
        read(ApiConfig.channelUrl(id)) { CatalogueBody.channel(it) != null }

    /**
     * The one-body route, or null when no address has it.
     *
     * A site without the route is not a failure: the guide is still there in
     * the two older routes, so the caller falls back to them.
     */
    override suspend fun bootstrapJson(): String? =
        attempt(ApiConfig.BOOTSTRAP_PATH) { CatalogueBody.bootstrap(it) != null }.body

    private suspend fun read(path: String, usable: (String) -> Boolean): String {
        if (addresses.isEmpty()) throw IOException("no base address is configured")

        val outcome = attempt(path, usable)
        outcome.body?.let { return it }

        val failure = outcome.failure ?: IOException("no base address answered")

        // The site was reached but had nothing to give — a different thing from
        // the site not answering at all, and the screen says which one it was.
        if (outcome.answered && failure !is SiteAnsweredException) {
            throw UnusableAnswerException("$path could not be read from any address")
        }
        throw failure
    }

    private suspend fun attempt(path: String, usable: (String) -> Boolean): Attempt {
        if (addresses.isEmpty()) return Attempt(body = null, failure = null, answered = false)

        var failure: IOException? = null
        var answered = false
        for (address in addresses) {
            try {
                val body = transport.get(address.trimEnd('/') + path)
                if (usable(body)) return Attempt(body = body, failure = null, answered = answered)
                answered = true
                failure = UnusableAnswerException("$path answered with a body that is not a guide")
            } catch (error: SiteAnsweredException) {
                answered = true
                failure = error
            } catch (error: IOException) {
                failure = error
            }
        }

        return Attempt(body = null, failure = failure, answered = answered)
    }
}

/** What one attempt at a route produced. */
private class Attempt(
    val body: String?,
    val failure: IOException?,
    val answered: Boolean,
)
