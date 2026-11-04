package com.soluplay.core.ui

/**
 * The parts of a logo that need no device.
 *
 * They are kept apart from the fetching and the decoding so they can be
 * checked without a phone: how big a picture has to be sampled down to, and
 * which letter stands in for a logo that could not be fetched.
 */
object LogoSupport {

    /**
     * The power of two a picture is divided by.
     *
     * A logo published at a thousand pixels is drawn in forty-six points, so
     * keeping the whole picture costs memory for nothing. The result is the
     * largest halving that still leaves the picture no bigger than the edge
     * it is drawn at.
     */
    fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        if (width <= 0 || height <= 0 || maxEdge <= 0) return 1

        var longest = maxOf(width, height)
        var sample = 1
        while (longest / 2 >= maxEdge) {
            longest /= 2
            sample *= 2
        }
        return sample
    }

    /**
     * The letter that stands in for a logo that could not be fetched.
     *
     * The first letter of the name is taken whole: a name whose first letter
     * is written in two pieces is not cut in half, because a half a letter is
     * a broken letter and not a name.
     */
    fun firstLetter(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return "?"

        val end = trimmed.offsetByCodePoints(0, 1)
        return trimmed.substring(0, end).uppercase()
    }
}
