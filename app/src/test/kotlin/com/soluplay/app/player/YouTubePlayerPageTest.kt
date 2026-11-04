package com.soluplay.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The page that carries YouTube's own player.
 *
 * The page is the whole of the YouTube player in the app, so what it asks
 * for is what the viewer gets: the embedded player with the video in it, told
 * to play by itself, told which origin to post its messages to, and spoken
 * to first — because a player that is never spoken to plays and says nothing,
 * and the app then takes the picture away while it is still moving.
 */
class YouTubePlayerPageTest {

    private val page = YouTubePlayerPage.html("https://soluplay.vercel.app")

    @Test
    fun `the player is youtube's own embedded player`() {
        assertTrue(page.contains("https://www.youtube.com/embed/"))
    }

    @Test
    fun `the player plays by itself`() {
        assertTrue(page.contains("autoplay=1"))
        assertTrue(page.contains("playsinline=1"))
        assertTrue(page.contains("'allow', 'autoplay"))
    }

    @Test
    fun `the player can be told to play and reports what it does`() {
        assertTrue(page.contains("enablejsapi=1"))
        assertTrue(page.contains("'onStateChange'"))
        assertTrue(page.contains("'onError'"))
        assertTrue(page.contains("func: 'playVideo'"))
    }

    @Test
    fun `the player is told which origin to post to`() {
        assertTrue(page.contains("origin="))
        assertTrue(page.contains("var ORIGIN = \"https://soluplay.vercel.app\";"))
    }

    @Test
    fun `the player is spoken to before it answers`() {
        // The embedded player only answers the page that spoke to it first,
        // and this is the speaking: YouTube's own player API.
        assertTrue(page.contains("https://www.youtube.com/iframe_api"))
        assertTrue(page.contains("function onYouTubeIframeAPIReady()"))
        assertTrue(page.contains("new YT.Player('player'"))
    }

    @Test
    fun `the player is told the origin by the api as well`() {
        assertTrue(page.contains("origin: ORIGIN"))
    }

    @Test
    fun `the player's state is read and not only waited for`() {
        assertTrue(page.contains("player.getPlayerState()"))
        assertTrue(page.contains("function pollState()"))
    }

    @Test
    fun `the player is not put up while the api is on its way`() {
        // The player is made once. A player put up by hand and then made
        // again by the API is a picture that starts twice.
        assertTrue(page.contains("else if (apiFailed || apiWaitOver) { embed(videoId); }"))
        assertFalse(page.contains("else { embed(videoId); }"))
    }

    @Test
    fun `the words that start the player listening are said`() {
        assertTrue(page.contains("event: 'listening'"))
        assertTrue(page.contains("channel: 'widget'"))
    }

    @Test
    fun `the player is not built again for the same video`() {
        assertTrue(page.contains("currentId === videoId"))
    }

    @Test
    fun `a video id cannot break the page`() {
        assertEquals("\"a\\\"b\"", "a\"b".asJavaScriptString())
        assertEquals("\"a\\\\b\"", "a\\b".asJavaScriptString())
    }
}
