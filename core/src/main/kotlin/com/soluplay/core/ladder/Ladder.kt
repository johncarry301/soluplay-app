package com.soluplay.core.ladder

import com.soluplay.core.model.StreamLink

/**
 * The ladder: the server's mirror order, walked one mirror at a time.
 *
 * The list arrives already ranked. This type deliberately offers no way to
 * sort it — the only operation is "give me the next mirror" — so no surface
 * can quietly re-rank a channel and send a viewer to a worse mirror than the
 * one the site chose.
 */
class Ladder(streams: List<StreamLink>) {

    private val ordered: List<StreamLink> = streams.toList()
    private var index = 0

    /** True once every published mirror has been tried. */
    val exhausted: Boolean get() = index >= ordered.size

    /** How many mirrors have been handed out so far. */
    val position: Int get() = index

    /** The mirrors that have not been handed out yet, in published order. */
    fun remaining(): List<StreamLink> = ordered.drop(index)

    /** The next mirror in published order, or null when the ladder is spent. */
    fun next(): StreamLink? {
        val link = ordered.getOrNull(index) ?: return null
        index += 1
        return link
    }

    /** Start again from the first mirror. */
    fun reset() {
        index = 0
    }
}
