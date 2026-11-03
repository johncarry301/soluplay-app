package com.soluplay.core

import com.soluplay.core.browse.allChannelsInOrder
import com.soluplay.core.browse.railsOf
import com.soluplay.core.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rails are the server's own grouping, in the server's own order.
 *
 * The app never re-ranks the guide, so a rail's place in the list is decided
 * by the order the server published and nothing else.
 */
class RailGroupingTest {

    private fun channel(
        id: String,
        category: String,
        priority: Int,
    ) = Channel(id = id, name = "Channel $id", category = category, priorityOrder = priority)

    @Test
    fun `the rails follow the order the server published`() {
        val channels = listOf(
            channel("1", "Sports", 1),
            channel("2", "Bangla", 2),
            channel("3", "Sports", 3),
            channel("4", "Indian", 4),
        )

        val rails = railsOf(channels)

        assertEquals(listOf("Sports", "Bangla", "Indian"), rails.map { it.name })
    }

    @Test
    fun `the channels inside a rail keep the published order`() {
        val channels = listOf(
            channel("1", "Sports", 1),
            channel("2", "Sports", 3),
            channel("3", "Sports", 2),
        )

        val rails = railsOf(channels)

        assertEquals(listOf("1", "3", "2"), rails.single().channels.map { it.id })
    }

    @Test
    fun `a channel with no category still has a rail`() {
        val channels = listOf(
            channel("1", "", 1),
            channel("2", "Sports", 2),
        )

        val rails = railsOf(channels)

        assertEquals(listOf("Other", "Sports"), rails.map { it.name })
        assertEquals(listOf("1"), rails.first().channels.map { it.id })
    }

    @Test
    fun `the whole guide keeps the published order`() {
        val channels = listOf(
            channel("1", "Sports", 3),
            channel("2", "Bangla", 1),
            channel("3", "Sports", 2),
        )

        assertEquals(listOf("2", "3", "1"), allChannelsInOrder(channels).map { it.id })
    }

    @Test
    fun `an empty catalogue has no rails`() {
        assertTrue(railsOf(emptyList()).isEmpty())
    }
}
