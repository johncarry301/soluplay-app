package com.soluplay.core.api

/**
 * Where the app talks to.
 *
 * The site is the source of truth. The backups exist so a domain that stops
 * answering cannot take the app down with it — the list is empty until the
 * owner names the addresses to try after the primary.
 */
object ApiConfig {
    const val PRIMARY_BASE_URL = "https://soluplay.vercel.app"

    /** Tried in order after the primary fails. */
    val BACKUP_BASE_URLS: List<String> = emptyList()

    const val CHANNELS_PATH = "/api/channels"
    const val FIXTURES_PATH = "/api/fixtures"
    const val YOUTUBE_RESOLVE_PATH = "/api/youtube/resolve"

    /** Added in Phase 4: one round trip for the catalogue, the board and the rails. */
    const val BOOTSTRAP_PATH = "/api/app/bootstrap"

    /** Added in Phase 4: the update manifest. */
    const val VERSION_PATH = "/api/app/version"

    /** Added in Phase 5: the viewer's favourites, under a key the viewer carries. */
    const val FAVOURITES_PATH = "/api/app/favourites"

    /** The manifest mirror, so a site outage cannot stop an update. */
    const val VERSION_MIRROR_URL =
        "https://raw.githubusercontent.com/johncarry301/soluplay-app/main/latest.json"

    fun channelUrl(id: String): String = "$CHANNELS_PATH/$id"
}
