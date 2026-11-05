package com.soluplay.core

import com.soluplay.core.model.Channel
import com.soluplay.core.search.ChannelSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The search.
 *
 * The guide is the site's and the search does not re-rank it: the search only
 * says which channels the query names, and it says so in the order a viewer
 * expects — the channel whose name begins with what was typed comes first.
 */
class ChannelSearchTest {

    private fun channel(
        id: String,
        name: String,
        category: String = "Sports",
        tags: List<String> = emptyList(),
        priority: Int = 99,
    ) = Channel(
        id = id,
        name = name,
        logo = "https://logos.test/$id.png",
        category = category,
        tags = tags,
        priorityOrder = priority,
    )

    @Test
    fun `an empty query names nothing`() {
        val channels = listOf(
            channel("c1", "T Sports"),
            channel("c2", "E Sports"),
        )

        assertTrue(ChannelSearch.results(channels, "").isEmpty())
        assertTrue(ChannelSearch.results(channels, "   ").isEmpty())
    }

    @Test
    fun `a query that names nothing finds nothing`() {
        val channels = listOf(channel("c1", "T Sports"))

        assertTrue(ChannelSearch.results(channels, "zzz").isEmpty())
    }

    @Test
    fun `the channel whose name begins with the query comes first`() {
        val channels = listOf(
            channel("c1", "Bangladesh Sports"),
            channel("c2", "Sports One"),
        )

        val found = ChannelSearch.results(channels, "sports")

        assertEquals(listOf("c2", "c1"), found.map { it.id })
    }

    @Test
    fun `a name that only contains the query comes after one that begins with it`() {
        val channels = listOf(
            channel("c1", "Sports Extra"),
            channel("c2", "Sports"),
        )

        val found = ChannelSearch.results(channels, "sports")

        assertEquals(listOf("c2", "c1"), found.map { it.id })
    }

    @Test
    fun `a channel that matches only by its category comes last`() {
        val channels = listOf(
            channel("c1", "Channel One", category = "Sports"),
            channel("c2", "Channel Two", category = "News"),
        )

        val found = ChannelSearch.results(channels, "news")

        assertEquals(listOf("c2"), found.map { it.id })
    }

    @Test
    fun `a tag matches when nothing else does`() {
        val channels = listOf(
            channel("c1", "Channel One", category = "News", tags = listOf("cricket")),
        )

        val found = ChannelSearch.results(channels, "cricket")

        assertEquals(listOf("c1"), found.map { it.id })
    }

    @Test
    fun `the query is read whichever case it is typed in`() {
        val channels = listOf(channel("c1", "T Sports"))

        assertEquals(listOf("c1"), ChannelSearch.results(channels, "SPORTS").map { it.id })
        assertEquals(listOf("c1"), ChannelSearch.results(channels, "t sp").map { it.id })
    }

    @Test
    fun `channels that match equally well keep the order the site published`() {
        val channels = listOf(
            channel("c1", "Sports One", priority = 5),
            channel("c2", "Sports Two", priority = 1),
            channel("c3", "Sports Three", priority = 9),
        )

        val found = ChannelSearch.results(channels, "sports")

        assertEquals(listOf("c2", "c1", "c3"), found.map { it.id })
    }

    @Test
    fun `a channel that is named only by a tag comes after one that is named by its name`() {
        val channels = listOf(
            channel("c1", "Channel One", category = "News", tags = listOf("sports")),
            channel("c2", "Sports One", category = "News"),
        )

        val found = ChannelSearch.results(channels, "sports")

        assertEquals(listOf("c2", "c1"), found.map { it.id })
    }
}
