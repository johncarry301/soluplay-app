package com.soluplay.core

import com.soluplay.core.gestures.GestureEvent
import com.soluplay.core.gestures.GestureIntent
import com.soluplay.core.gestures.Gestures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The gestures of the watch screen.
 *
 * The rules are plain: a finger that travelled is a swipe, and a finger that
 * stayed is a tap. Which is which is decided here and not on the surface, so
 * the rules can be read on their own.
 */
class GesturesTest {

    @Test
    fun `one tap brings the words in`() {
        val intent = Gestures.intentOf(GestureEvent(tapCount = 1))

        assertEquals(GestureIntent.REVEAL_CONTROLS, intent)
    }

    @Test
    fun `two taps take the picture`() {
        val intent = Gestures.intentOf(GestureEvent(tapCount = 2))

        assertEquals(GestureIntent.FULLSCREEN, intent)
    }

    @Test
    fun `two fingers hold the feed`() {
        val intent = Gestures.intentOf(GestureEvent(tapCount = 1, pointerCount = 2))

        assertEquals(GestureIntent.PAUSE, intent)
    }

    @Test
    fun `a swipe up asks for the next channel`() {
        val intent = Gestures.intentOf(GestureEvent(dragY = -120f))

        assertEquals(GestureIntent.NEXT_CHANNEL, intent)
    }

    @Test
    fun `a swipe down asks for the channel before this one`() {
        val intent = Gestures.intentOf(GestureEvent(dragY = 120f))

        assertEquals(GestureIntent.PREVIOUS_CHANNEL, intent)
    }

    @Test
    fun `a swipe sideways asks for another mirror`() {
        val intent = Gestures.intentOf(GestureEvent(dragX = 120f))

        assertEquals(GestureIntent.NEXT_MIRROR, intent)
    }

    @Test
    fun `a swipe that is too short is not a swipe`() {
        val intent = Gestures.intentOf(GestureEvent(dragY = -10f, tapCount = 1))

        assertEquals(GestureIntent.REVEAL_CONTROLS, intent)
    }

    @Test
    fun `a swipe wins over the tap it began as`() {
        val intent = Gestures.intentOf(GestureEvent(tapCount = 1, dragX = 200f))

        assertEquals(GestureIntent.NEXT_MIRROR, intent)
    }

    @Test
    fun `a swipe that is mostly down changes the channel`() {
        val intent = Gestures.intentOf(GestureEvent(dragX = 40f, dragY = -200f))

        assertEquals(GestureIntent.NEXT_CHANNEL, intent)
    }

    @Test
    fun `a swipe that is mostly sideways asks for another mirror`() {
        val intent = Gestures.intentOf(GestureEvent(dragX = 200f, dragY = 40f))

        assertEquals(GestureIntent.NEXT_MIRROR, intent)
    }

    @Test
    fun `a gesture that means nothing says nothing`() {
        assertNull(Gestures.intentOf(GestureEvent()))
    }

    @Test
    fun `the swipe distance is the one the plan names`() {
        assertEquals(48f, Gestures.SWIPE_THRESHOLD_DP, 0.01f)
    }
}
