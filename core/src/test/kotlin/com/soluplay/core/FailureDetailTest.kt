package com.soluplay.core

import com.soluplay.core.player.PlaybackDetails
import com.soluplay.core.ui.failureDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The words a player's reason is put into.
 *
 * The reason a player gives is a code, and a code is not something a viewer
 * can act on — so the codes the app knows are turned into words, and a code it
 * does not know is shown as it is, because naming it is better than hiding it.
 */
class FailureDetailTest {

    @Test
    fun `no reason means no words`() {
        val words = failureDetail("")
        assertEquals("", words.bengali)
        assertEquals("", words.english)
    }

    @Test
    fun `a player that never started says so`() {
        val words = failureDetail(PlaybackDetails.YOUTUBE_NO_PICTURE)
        assertTrue(words.english.contains("did not start"))
        assertTrue(words.bengali.isNotBlank())
    }

    @Test
    fun `a video that could not be found says so`() {
        val words = failureDetail(PlaybackDetails.YOUTUBE_RESOLVE)
        assertTrue(words.english.contains("could not be found"))
        assertTrue(words.bengali.isNotBlank())
    }

    @Test
    fun `a page that could not be loaded says so`() {
        val words = failureDetail(PlaybackDetails.YOUTUBE_PAGE)
        assertTrue(words.english.contains("page"))
        assertTrue(words.bengali.isNotBlank())
    }

    @Test
    fun `a refusal by youtube carries its number`() {
        val words = failureDetail(PlaybackDetails.youTubeError(150))
        assertTrue(words.english.contains("150"))
        assertTrue(words.english.contains("YouTube"))
        assertTrue(words.bengali.contains("150"))
    }

    @Test
    fun `a reason nobody knows is still named`() {
        val words = failureDetail("hls.something.new")
        assertEquals("hls.something.new", words.english)
        assertEquals("hls.something.new", words.bengali)
    }
}
