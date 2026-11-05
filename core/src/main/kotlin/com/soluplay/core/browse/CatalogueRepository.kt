package com.soluplay.core.browse

import com.soluplay.core.api.CatalogueBody
import com.soluplay.core.api.CatalogueSource
import com.soluplay.core.api.SiteAnsweredException
import com.soluplay.core.cache.CatalogueCache
import com.soluplay.core.model.ChannelDetail

/** The copy of the guide that was baked into the APK when it was built. */
interface SeedProvider {
    /** The catalogue body, or null when the build carries none. */
    fun channelsJson(): String?
}

/**
 * The guide, from wherever it can be had.
 *
 * The order of the fallbacks is the order of trust: the live site first, then
 * the copy this device saved, then the copy baked into the APK. A failure at
 * one level is never rendered as an empty guide — the next level is used and
 * the screen says which copy it is showing.
 */
class CatalogueRepository(
    private val source: CatalogueSource,
    private val cache: CatalogueCache,
    private val seed: SeedProvider,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * The live mirror list of a channel, or null when the site did not give one.
     *
     * Playback reads the ladder from the site and from nowhere else. The saved
     * copy and the baked seed are a guide, not a ladder: a stale mirror list
     * would walk a viewer into a link that died days ago, and the seed carries
     * no stream addresses at all.
     */
    suspend fun liveChannelDetail(id: String): ChannelDetail? {
        val body = runCatching { source.channelJson(id) }.getOrNull() ?: return null
        return CatalogueBody.channel(body)
    }

    suspend fun load(): BrowseSnapshot {
        val live = loadLive()
        if (live.snapshot != null) return live.snapshot

        val cached = loadCached()
        if (cached != null) return cached.copy(siteReachable = live.siteReachable)

        val baked = loadSeed()
        if (baked != null) return baked.copy(siteReachable = live.siteReachable)

        return BrowseSnapshot(
            origin = CatalogueOrigin.NONE,
            siteReachable = live.siteReachable,
            error = if (live.siteReachable) {
                "The site answered but gave no guide, and this device has no saved copy."
            } else {
                "The site could not be reached and this device has no saved copy."
            },
        )
    }

    private suspend fun loadLive(): LiveAttempt {
        val bootstrap = loadBootstrap()
        if (bootstrap != null) return bootstrap

        val channelsBody = try {
            source.channelsJson()
        } catch (error: Exception) {
            // A site that answered is a different thing from a site that did
            // not, and the copy on the screen depends on which one it was.
            return LiveAttempt(null, siteReachable = error is SiteAnsweredException)
        }

        val channels = CatalogueBody.channels(channelsBody)
            ?: return LiveAttempt(null, siteReachable = true)

        // The board is a separate route and a separate failure: a missing
        // match list must not cost the viewer the guide.
        val fixturesBody = try {
            source.fixturesJson()
        } catch (error: Exception) {
            null
        }
        val fixtures = CatalogueBody.fixtures(fixturesBody).orEmpty()

        // Only a board that could be read is kept: a board from another day
        // left on the device would be shown as if it were today's.
        cache.write(channelsBody, fixturesBody?.takeIf { CatalogueBody.fixtures(it) != null })

        return LiveAttempt(
            snapshot = BrowseSnapshot(
                channels = channels,
                fixtures = fixtures,
                origin = CatalogueOrigin.LIVE,
                savedAt = now(),
                siteReachable = true,
            ),
            siteReachable = true,
        )
    }

    /**
     * The one-body route, when the site has one.
     *
     * A site that does not have the route is not a failure — the two older
     * routes carry the same guide, so the caller falls back to them.
     */
    private suspend fun loadBootstrap(): LiveAttempt? {
        val body = runCatching { source.bootstrapJson() }.getOrNull() ?: return null
        val guide = CatalogueBody.bootstrap(body) ?: return null

        cache.write(body, if (guide.boardAvailable) body else null)

        return LiveAttempt(
            snapshot = BrowseSnapshot(
                channels = guide.channels,
                fixtures = guide.fixtures,
                origin = CatalogueOrigin.LIVE,
                savedAt = now(),
                siteReachable = true,
            ),
            siteReachable = true,
        )
    }

    private fun loadCached(): BrowseSnapshot? {
        val saved = cache.read() ?: return null
        val channels = CatalogueBody.channels(saved.channelsJson) ?: return null
        return BrowseSnapshot(
            channels = channels,
            fixtures = CatalogueBody.fixtures(saved.fixturesJson).orEmpty(),
            origin = CatalogueOrigin.CACHED,
            savedAt = saved.savedAt,
        )
    }

    private fun loadSeed(): BrowseSnapshot? {
        val body = seed.channelsJson() ?: return null
        val channels = CatalogueBody.channels(body) ?: return null
        return BrowseSnapshot(
            channels = channels,
            fixtures = emptyList(),
            origin = CatalogueOrigin.SEED,
            savedAt = 0L,
        )
    }
}

/** What the live attempt produced: the guide, or the reason there is none. */
private class LiveAttempt(
    val snapshot: BrowseSnapshot?,
    val siteReachable: Boolean,
)
