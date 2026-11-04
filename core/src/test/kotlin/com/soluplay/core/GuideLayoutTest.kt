package com.soluplay.core

import com.soluplay.core.browse.GuideItem
import com.soluplay.core.browse.Rail
import com.soluplay.core.browse.guideItemsOf
import com.soluplay.core.browse.railHeadingsOf
import com.soluplay.core.model.Channel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The guide's list, drawn a row at a time.
 *
 * The guide used to be drawn a whole rail at a time, which meant every channel
 * of a rail — and every logo of a rail — was composed the moment the rail
 * came on the screen. The list is flat now: a heading, then one row of
 * channels at a time.
 */
class GuideLayoutTest {

    private fun channel(id: String, category: String = "Sports", priority: Int = 1) =
        Channel(id = id, name = "Channel $id", category = category, priorityOrder = priority)

    @Test
    fun `a rail is a heading and one row at a time`() {
        val rails = listOf(Rail("Sports", (1..9).map { channel("$it") }))

        val items = guideItemsOf(rails, columns = 4)

        assertEquals(
            listOf(
                "heading:Sports",
                "row:Sports:0",
                "row:Sports:1",
                "row:Sports:2",
            ),
            items.map { it.key },
        )
    }

    @Test
    fun `a row holds the channels that fit in it`() {
        val rails = listOf(Rail("Sports", (1..5).map { channel("$it") }))

        val rows = guideItemsOf(rails, columns = 4).filterIsInstance<GuideItem.Row>()

        assertEquals(2, rows.size)
        assertEquals(listOf("1", "2", "3", "4"), rows[0].channels.map { it.id })
        assertEquals(listOf("5"), rows[1].channels.map { it.id })
    }

    @Test
    fun `the last row of a rail is marked as the last`() {
        val rails = listOf(Rail("Sports", (1..5).map { channel("$it") }))

        val rows = guideItemsOf(rails, columns = 4).filterIsInstance<GuideItem.Row>()

        assertTrue(!rows[0].last)
        assertTrue(rows[1].last)
    }

    @Test
    fun `each rail has its own heading`() {
        val rails = listOf(
            Rail("Sports", (1..3).map { channel("$it", category = "Sports") }),
            Rail("Bangla", (4..5).map { channel("$it", category = "Bangla") }),
        )

        val items = guideItemsOf(rails, columns = 4)

        assertEquals(
            listOf("heading:Sports", "row:Sports:0", "heading:Bangla", "row:Bangla:0"),
            items.map { it.key },
        )
    }

    @Test
    fun `the heading of a rail says how many channels it holds`() {
        val rails = listOf(Rail("Sports", (1..7).map { channel("$it") }))

        val heading = guideItemsOf(rails, columns = 4).filterIsInstance<GuideItem.Heading>().single()

        assertEquals("Sports", heading.name)
        assertEquals(7, heading.count)
    }

    @Test
    fun `a rail can be jumped to by name`() {
        val rails = listOf(
            Rail("Sports", (1..9).map { channel("$it", category = "Sports") }),
            Rail("Bangla", (10..11).map { channel("$it", category = "Bangla") }),
        )

        val headings = railHeadingsOf(rails, columns = 4)

        assertEquals(listOf(0, 4), headings)
        assertEquals("heading:Sports", guideItemsOf(rails, columns = 4)[headings[0]].key)
        assertEquals("heading:Bangla", guideItemsOf(rails, columns = 4)[headings[1]].key)
    }

    @Test
    fun `a guide with no rails is empty`() {
        assertTrue(guideItemsOf(emptyList(), columns = 4).isEmpty())
        assertTrue(railHeadingsOf(emptyList(), columns = 4).isEmpty())
    }
}
