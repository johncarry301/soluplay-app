package com.soluplay.core.browse

import com.soluplay.core.model.Channel
import com.soluplay.core.model.Fixture

/**
 * Which copy of the guide the screen is showing.
 *
 * The app states this on every surface: a stale guide that looks live is
 * worse than one that admits it is old.
 */
enum class CatalogueOrigin {
    /** Fetched from the site just now. */
    LIVE,

    /** The last good copy kept on the device. */
    CACHED,

    /** The copy baked into the APK when it was built. */
    SEED,

    /** Nothing to show. */
    NONE,
}

/** The guide as one screen sees it. */
data class BrowseSnapshot(
    val channels: List<Channel> = emptyList(),
    val fixtures: List<Fixture> = emptyList(),
    val origin: CatalogueOrigin = CatalogueOrigin.NONE,
    /** When the copy on screen was taken, in epoch milliseconds. */
    val savedAt: Long = 0L,
    /**
     * True when the site answered during this load.
     *
     * A site that answered and had nothing to give is not a site that is
     * down, and the screen says which one it was.
     */
    val siteReachable: Boolean = false,
    val loading: Boolean = false,
    val error: String = "",
) {
    /** The copy on screen is not the live one, so the screen has to say so. */
    val outage: Boolean get() = !loading && origin != CatalogueOrigin.LIVE
}

/** One category row of the guide. */
data class Rail(
    val name: String,
    val channels: List<Channel>,
)

/**
 * What the screen shows after a load.
 *
 * A load that brought nothing never blanks a guide that is already on the
 * screen. But the guide that is left is not the live one any more, so the
 * screen stops calling it live and says the site could not be reached.
 */
fun mergeSnapshot(current: BrowseSnapshot, next: BrowseSnapshot): BrowseSnapshot =
    if (next.channels.isEmpty() && current.channels.isNotEmpty()) {
        current.copy(
            origin = if (current.origin == CatalogueOrigin.LIVE) {
                CatalogueOrigin.CACHED
            } else {
                current.origin
            },
            siteReachable = next.siteReachable,
            error = next.error,
        )
    } else {
        next
    }

/**
 * The catalogue as rails: one row per category.
 *
 * The order comes from the server's own `priorityOrder`, never from a list
 * written here — a rail that the site has moved goes where the site put it.
 */
fun railsOf(channels: List<Channel>): List<Rail> {
    val grouped = channels.groupBy { it.category.ifBlank { OTHER_CATEGORY } }
    return grouped
        .map { (name, group) ->
            Rail(name, group.sortedBy { it.priorityOrder })
        }
        .sortedBy { rail -> rail.channels.minOf { it.priorityOrder } }
}

/**
 * The whole catalogue in the order the site published it.
 *
 * The server ranks a channel by whether it is pinned, then by the board order
 * an admin gave it, then by its name. The app sorts the same way, so the grid
 * on the phone and the grid on the site are the same list in the same order
 * even when the answer came from the cache or from the copy in the APK.
 */
fun allChannelsInOrder(channels: List<Channel>): List<Channel> =
    channels.sortedWith(
        compareByDescending<Channel> { it.isPinned }
            .thenBy { it.priorityOrder }
            .thenBy { it.name },
    )

private const val OTHER_CATEGORY = "Other"
