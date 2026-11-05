package com.soluplay.core.search

import com.soluplay.core.model.Channel

/**
 * The channels a search finds.
 *
 * The guide is the server's and the search never re-ranks it: a channel that
 * is found is shown in the order the site published it, and the search only
 * says which of those channels the query names.
 *
 * The ranking inside the answer is the one a viewer expects: the channel whose
 * name *begins* with what was typed comes before the one that merely contains
 * it, and a channel that matches only because of its category comes after
 * both.
 */
object ChannelSearch {

    /** How well a channel matched. Lower is better. */
    private const val NAME_STARTS_WITH = 0
    private const val NAME_CONTAINS = 1
    private const val CATEGORY_MATCHES = 2
    private const val TAG_MATCHES = 3

    /** No channel matched. */
    private const val NO_MATCH = 9

    /**
     * The channels the query names.
     *
     * An empty query names nothing: the screen shows its own words instead of
     * the whole guide, because a list of four hundred channels is not a
     * search result.
     */
    fun results(channels: List<Channel>, query: String): List<Channel> {
        val needle = query.trim().lowercase()
        if (needle.isEmpty()) return emptyList()

        return channels
            .map { channel -> channel to rankOf(channel, needle) }
            .filter { (_, rank) -> rank != NO_MATCH }
            .sortedWith(compareBy({ it.second }, { it.first.priorityOrder }, { it.first.name }))
            .map { (channel, _) -> channel }
    }

    /** How well this channel matched the query. */
    private fun rankOf(channel: Channel, needle: String): Int {
        val name = channel.name.lowercase()
        if (name.startsWith(needle)) return NAME_STARTS_WITH
        if (name.contains(needle)) return NAME_CONTAINS
        if (channel.category.lowercase().contains(needle)) return CATEGORY_MATCHES
        if (channel.tags.any { tag -> tag.lowercase().contains(needle) }) return TAG_MATCHES
        return NO_MATCH
    }
}
