package com.soluplay.core.browse

import com.soluplay.core.model.Channel

/**
 * The guide's list, drawn a row at a time.
 *
 * A rail is a heading and its rows of channels. The list is flat, so the
 * screen draws only the rows that are on it: a rail of forty channels is not
 * drawn — and none of its forty logos is fetched — the moment the rail comes
 * on the screen.
 *
 * The order is the server's: the rails as the server ranked them, and the
 * channels inside a rail in the order the server published them.
 */
sealed interface GuideItem {

    /** What tells one entry of the list from another. */
    val key: String

    /** The name of a rail and how many channels it holds. */
    data class Heading(val name: String, val count: Int) : GuideItem {
        override val key: String get() = "heading:$name"
    }

    /** One row of the channels of a rail. */
    data class Row(
        val name: String,
        val index: Int,
        val channels: List<Channel>,
        /** True for the last row of a rail, which is followed by the next rail. */
        val last: Boolean,
    ) : GuideItem {
        override val key: String get() = "row:$name:$index"
    }
}

/**
 * The guide's list, one row of channels at a time.
 */
fun guideItemsOf(rails: List<Rail>, columns: Int): List<GuideItem> {
    val items = mutableListOf<GuideItem>()
    rails.forEach { rail ->
        items += GuideItem.Heading(rail.name, rail.channels.size)
        val rows = rail.channels.chunked(columns)
        rows.forEachIndexed { index, row ->
            items += GuideItem.Row(
                name = rail.name,
                index = index,
                channels = row,
                last = index == rows.lastIndex,
            )
        }
    }
    return items
}

/**
 * Where the heading of each rail sits in the guide's list.
 *
 * A rail can be jumped to by name, and this is where the name is.
 */
fun railHeadingsOf(rails: List<Rail>, columns: Int): List<Int> {
    val headings = mutableListOf<Int>()
    var index = 0
    rails.forEach { rail ->
        headings += index
        index += 1 + (rail.channels.size + columns - 1) / columns
    }
    return headings
}
