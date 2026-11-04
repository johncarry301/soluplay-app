package com.soluplay.core

import com.soluplay.core.ui.LogoSupport
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The parts of a logo that need no device.
 *
 * A logo is drawn in a small plate, so a picture published at full size is
 * kept whole and sampled down rather than cropped — a clipped logo is a
 * broken logo. The letter that stands in for a logo that could not be
 * fetched is taken whole too.
 */
class LogoSupportTest {

    @Test
    fun `a picture already at the size it is drawn at is not sampled down`() {
        assertEquals(1, LogoSupport.sampleSize(width = 120, height = 120, maxEdge = 120))
    }

    @Test
    fun `a picture larger than the plate is sampled down by halves`() {
        assertEquals(2, LogoSupport.sampleSize(width = 240, height = 120, maxEdge = 120))
        assertEquals(4, LogoSupport.sampleSize(width = 480, height = 480, maxEdge = 120))
        assertEquals(8, LogoSupport.sampleSize(width = 1000, height = 600, maxEdge = 120))
    }

    @Test
    fun `the longer edge is the one that decides`() {
        assertEquals(4, LogoSupport.sampleSize(width = 100, height = 480, maxEdge = 120))
    }

    @Test
    fun `a picture with no size is left alone`() {
        assertEquals(1, LogoSupport.sampleSize(width = 0, height = 0, maxEdge = 120))
        assertEquals(1, LogoSupport.sampleSize(width = 120, height = 120, maxEdge = 0))
    }

    @Test
    fun `the letter is the first letter of the name`() {
        assertEquals("S", LogoSupport.firstLetter("Sports One"))
        assertEquals("N", LogoSupport.firstLetter("  News  "))
    }

    @Test
    fun `a letter written in two pieces is taken whole`() {
        // The first letter is a character outside the basic plane, so it is
        // stored as two code units and must not be cut between them.
        val letter = LogoSupport.firstLetter("\uD83D\uDC00 Sports")

        assertEquals(2, letter.length)
        assertEquals(1, letter.codePointCount(0, letter.length))
    }

    @Test
    fun `a name with nothing in it gets a question mark`() {
        assertEquals("?", LogoSupport.firstLetter(""))
        assertEquals("?", LogoSupport.firstLetter("   "))
    }
}
