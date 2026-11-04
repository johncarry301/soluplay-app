package com.soluplay.app.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When a failed request takes the YouTube picture away.
 *
 * The rule is what stopped a playing video from stopping on its own: a
 * request that failed is not a reason to take a picture that is already
 * moving away, and a request that was called off is not a failure at all.
 */
class YouTubeSurfaceTest {

    @Test
    fun `a request that was called off is not a failure`() {
        assertFalse(YouTubeSurface.shouldGiveUp(YouTubeSurface.ERROR_ABORTED, started = false))
    }

    @Test
    fun `a request in an unknown scheme is not a failure of the player`() {
        assertFalse(
            YouTubeSurface.shouldGiveUp(YouTubeSurface.ERROR_UNSUPPORTED_SCHEME, started = false),
        )
    }

    @Test
    fun `a player that is already playing is left alone`() {
        assertFalse(YouTubeSurface.shouldGiveUp(errorCode = -6, started = true))
    }

    @Test
    fun `a player that never said anything is given up`() {
        assertTrue(YouTubeSurface.shouldGiveUp(errorCode = -6, started = false))
    }
}
