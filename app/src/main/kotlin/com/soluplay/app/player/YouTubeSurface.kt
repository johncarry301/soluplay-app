package com.soluplay.app.player

import android.content.Context
import android.graphics.Color
import android.os.SystemClock
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.soluplay.core.api.ApiConfig

/** What the YouTube player says about itself. */
sealed interface YouTubeEvent {

    /** The player is up and can be given a video. */
    data object Ready : YouTubeEvent

    /** The player changed state. The number is YouTube's own. */
    data class State(val state: Int) : YouTubeEvent

    /** The player could not play the video. */
    data class Error(val code: Int) : YouTubeEvent

    /** The player could not be brought up at all. */
    data object PageFailed : YouTubeEvent
}

/**
 * A YouTube live stream, played by YouTube's own player.
 *
 * The site plays a YouTube mirror with YouTube's own embedded player, and
 * this is the same thing on a phone: a web view that loads YouTube's own
 * player with the same options and reports the same events. Nothing is pulled
 * out of YouTube and no manifest is fetched — the stream is played by
 * YouTube's player, with its advertising, exactly as the site plays it.
 *
 * The web view asks as a browser and not as a web view. A web view names
 * itself with a token that says it is a web view, and YouTube treats such a
 * player as an unverified one and refuses to play — which is why the same
 * address plays on the site and does not play here.
 *
 * The page is loaded from the site's own origin, because that is the origin
 * the embedded player is told to post its messages to; a page from any other
 * origin is a player whose messages go nowhere.
 *
 * The player's own state is asked for as well as waited for, so a change that
 * was never announced is still seen. And a request that fails does not take
 * the picture away: a player that is already playing is left alone, because a
 * moving video that stops on its own is worse than a slow one.
 *
 * The player is only told a video id. An address that names a video is
 * answered on the device; an address that names only a channel is asked about
 * through the site's own resolver, which is where the site already looks up
 * which video that channel is streaming.
 */
class YouTubeSurface(
    context: Context,
    private val onEvent: (YouTubeEvent) -> Unit,
    origin: String = ApiConfig.PRIMARY_BASE_URL,
) {
    /** The web view the player is shown in. */
    val view: WebView = WebView(context)

    /** YouTube's own player states. */
    private var state = STATE_UNSTARTED

    /** The clock of the picture, as far as the app can tell. */
    private var positionMs = 0L
    private var lastTickAt = SystemClock.elapsedRealtime()

    /** True once the page carrying the player has arrived. */
    private var pageLoaded = false

    /** True once the player has said anything at all about the video. */
    private var started = false

    /** The video that is to be played, and is waiting for the page. */
    private var wanted: String? = null

    init {
        view.setBackgroundColor(Color.BLACK)
        view.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )

        val settings: WebSettings = view.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        // The picture has to start on its own: the app never asks for a tap
        // before a channel is played, and the web player starts the same way.
        settings.mediaPlaybackRequiresUserGesture = false
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportMultipleWindows(false)
        // The name a browser answers to. A web view's own name says it is a
        // web view, and YouTube refuses to play for one.
        settings.userAgentString = WEB_USER_AGENT

        view.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                pageLoaded = true
                wanted?.let { videoId -> start(videoId) }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError,
            ) {
                if (!isThePlayer(request.url.toString())) return
                if (shouldGiveUp(error.errorCode, started)) {
                    onEvent(YouTubeEvent.PageFailed)
                }
            }
        }
        view.addJavascriptInterface(YouTubeBridge { event -> handle(event) }, BRIDGE_NAME)
        view.loadDataWithBaseURL(origin, YouTubePlayerPage.html(origin), "text/html", "utf-8", null)
    }

    /** True when the address is the embedded player itself. */
    private fun isThePlayer(url: String): Boolean =
        url.contains("youtube.com/embed/") || url.contains("youtube-nocookie.com/embed/")
    /** Keeps what the player said, and passes it on. */
    private fun handle(event: YouTubeEvent) {
        when (event) {
            is YouTubeEvent.State -> {
                state = event.state
                started = true
            }
            YouTubeEvent.Ready -> started = true
            else -> Unit
        }
        onEvent(event)
    }

    /**
     * Plays this video.
     *
     * The player is made when the video is known, so that the embed carries
     * the video from the start — a player given a video afterwards can come up
     * paused, because there is no tap behind the play.
     *
     * A video that is asked for before the page has arrived is remembered and
     * played as soon as it has.
     */
    fun play(videoId: String) {
        wanted = videoId
        if (pageLoaded) start(videoId)
    }

    /** Tells the page which video to play. */
    private fun start(videoId: String) {
        view.evaluateJavascript("start(${videoId.asJavaScriptString()});", null)
    }

    /**
     * Holds the picture.
     *
     * The player is told to hold, and the page stops insisting that it plays:
     * a player that is held and a player that came up paused are told apart
     * on the page, so the app's own request is not fought.
     */
    fun pause() {
        if (state == STATE_PAUSED) return
        state = STATE_PAUSED
        view.evaluateJavascript("hold();", null)
    }

    /** Lets the picture run again. */
    fun resume() {
        if (state != STATE_PAUSED) return
        state = STATE_PLAYING
        view.evaluateJavascript("release();", null)
    }

    /**
     * The clock of the picture.
     *
     * A web view has no clock of its own, so the app counts: the clock moves
     * while the player says it is playing, and stands still while it is
     * buffering. That is the same proof a decoder gives — a picture whose
     * clock has stopped is a dead carrier.
     */
    fun currentPositionMs(): Long {
        val moment = SystemClock.elapsedRealtime()
        if (state == STATE_PLAYING) positionMs += moment - lastTickAt
        lastTickAt = moment
        return positionMs
    }

    /** True while the player is waiting for something it does not have. */
    fun isBuffering(): Boolean = state == STATE_BUFFERING || state == STATE_PAUSED

    /** Lets the player go, and the web view with it. */
    fun release() {
        state = STATE_UNSTARTED
        wanted = null
        pageLoaded = false
        started = false
        view.stopLoading()
        view.removeJavascriptInterface(BRIDGE_NAME)
        view.destroy()
    }

    internal companion object {
        const val BRIDGE_NAME = "AndroidYouTube"

        const val STATE_UNSTARTED = 0
        const val STATE_PLAYING = 1
        const val STATE_PAUSED = 2
        const val STATE_BUFFERING = 3

        /**
         * The web view's own code for a request that was called off.
         *
         * The player pulls its own requests down whenever it is replaced, and
         * a request that was called off is an answer, not a failure.
         */
        const val ERROR_ABORTED = -3

        /**
         * The web view's own code for a request in a scheme it does not know.
         */
        const val ERROR_UNSUPPORTED_SCHEME = -15

        /**
         * True when a failed request means the player cannot be reached at all.
         *
         * A request that was called off, or one in a scheme the web view does
         * not know, is not a failure of the player. And a player that has
         * already said something about the video is left alone: one request
         * that failed is not a reason to take a moving picture away, which is
         * what made a playing video stop on its own.
         */
        fun shouldGiveUp(errorCode: Int, started: Boolean): Boolean = when {
            errorCode == ERROR_ABORTED -> false
            errorCode == ERROR_UNSUPPORTED_SCHEME -> false
            started -> false
            else -> true
        }

        /**
         * The name the player is asked with.
         *
         * A browser's name, and not a web view's: the web view's own name
         * carries a token that says the player is embedded in a web view, and
         * YouTube does not play for those.
         */
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36 SoluPlayApp/0.1.0"
    }
}

/**
 * The bridge the page calls into.
 *
 * The class and its methods are public on purpose: a web view reaches them
 * by reflection, and a method it cannot reach is a method that never fires.
 */
class YouTubeBridge(private val onEvent: (YouTubeEvent) -> Unit) {

    /** The player is up. */
    @JavascriptInterface
    fun onReady() {
        onEvent(YouTubeEvent.Ready)
    }

    /** The player's state changed. */
    @JavascriptInterface
    fun onState(state: Int) {
        onEvent(YouTubeEvent.State(state))
    }

    /** The player could not play the video. */
    @JavascriptInterface
    fun onError(code: Int) {
        onEvent(YouTubeEvent.Error(code))
    }
}

/** Puts a video id into the page as a JavaScript string. */
internal fun String.asJavaScriptString(): String = buildString {
    append('"')
    this@asJavaScriptString.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> if (character < ' ') {
                append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}
