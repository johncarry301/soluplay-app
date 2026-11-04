package com.soluplay.app.player

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import com.soluplay.core.api.UrlConnectionTransport
import com.soluplay.core.api.YouTubeResolver
import com.soluplay.core.player.PlaybackDetails
import com.soluplay.core.player.PlaybackFault
import com.soluplay.core.player.PlaybackHost
import com.soluplay.core.player.PlaybackListener
import com.soluplay.core.player.StreamKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The player.
 *
 * The machine tells this class which address to tune and this class tells the
 * machine what the player saw. The two facts that matter are "a picture was
 * put on the screen" and "the clock stopped", and both of them are read from
 * the player itself rather than guessed.
 *
 * The race needs more than one player: every mirror in it is played by a
 * player of its own, with a surface of its own, so the first of them to send
 * a picture can be chosen while the others are still trying. The players that
 * lose are released at once — decoder, buffer and surface together — so
 * nothing of them can reach the machine afterwards.
 *
 * Every try at playing gets its own listener, and the listener of the try that
 * is over is taken off the player before the next one is put on. A picture, a
 * stall or an error that belongs to a mirror the walk has already left cannot
 * therefore reach the machine, and the attempt number on every answer says so
 * a second time.
 *
 * The players are made when they are first needed and released when the app is
 * done with them, so a watch screen that is opened and closed does not leave
 * a decoder running behind it.
 *
 * A mirror is played by the player its kind calls for: an HLS playlist by
 * Media3's HLS source, a raw transport stream by Media3's TS extractor, and a
 * YouTube address by YouTube's own player inside a web view. The kind is
 * read out of the address by the core, so nothing here has to guess.
 */
class Media3PlaybackHost(
    private val context: Context,
    private val resolver: YouTubeResolver = YouTubeResolver(poster = UrlConnectionTransport()),
) : PlaybackHost {

    /** One mirror in the race, with the player that is trying it. */
    private abstract class Racer(val attempt: Long) {

        /** The view this mirror is played into, when it has one. */
        var view: View? = null

        /** True once this mirror has put a picture on the screen. */
        var pictureShown = false

        abstract fun release()

        abstract fun currentPositionMs(): Long

        abstract fun isBuffering(): Boolean
    }

    /** A mirror played by Media3. */
    private inner class MediaRacer(attempt: Long) : Racer(attempt) {

        val player: ExoPlayer = ExoPlayer.Builder(context).build()
        private val observer = Observer(attempt)
        private val surface = VideoSurface(context)

        init {
            player.addListener(observer)
            player.setVideoTextureView(surface.textureView)
            view = surface
        }

        /** Tunes the address with the source its kind calls for. */
        fun play(url: String, kind: StreamKind) {
            player.setMediaSource(sourceFor(url, kind))
            player.prepare()
            player.playWhenReady = true
        }

        override fun release() {
            player.removeListener(observer)
            player.stop()
            player.clearMediaItems()
            player.release()
        }

        override fun currentPositionMs(): Long = player.currentPosition

        override fun isBuffering(): Boolean =
            player.playbackState == Player.STATE_BUFFERING
    }

    /** A mirror played by YouTube's own player. */
    private inner class YouTubeRacer(attempt: Long) : Racer(attempt) {

        val surface = YouTubeSurface(context, onEvent = { event -> onYouTubeEvent(attempt, event) })

        init {
            view = surface.view
        }

        override fun release() {
            surface.release()
        }

        override fun currentPositionMs(): Long = surface.currentPositionMs()

        override fun isBuffering(): Boolean = surface.isBuffering()
    }

    /** The players in flight, one for each try at playing. */
    private val racers = LinkedHashMap<Long, Racer>()

    /** Where the pictures are shown. */
    private var stage: ViewGroup? = null

    /** The lookups the players do, off the main thread. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override var listener: PlaybackListener? = null

    /**
     * The place the pictures are shown.
     *
     * The host puts a view of its own for every mirror in the race into this
     * stage, and takes it away again when that mirror is let go of. The view
     * that was put in first is drawn last, so the mirror the site published
     * first is the one on the screen while the race is being decided.
     */
    fun attach(stage: ViewGroup) {
        if (this.stage === stage) return
        this.stage?.let { old ->
            racers.values.forEach { racer -> racer.view?.let { old.removeView(it) } }
        }
        this.stage = stage
        racers.values.forEach { racer ->
            val view = racer.view ?: return@forEach
            stage.addView(view, 0, matchParent())
        }
    }

    override fun prepare(url: String, attempt: Long, kind: StreamKind) {
        // One try at playing is played by one player only.
        cancel(attempt)

        val racer: Racer = when (kind) {
            StreamKind.YOUTUBE -> YouTubeRacer(attempt)
            StreamKind.HLS, StreamKind.MPEG_TS -> MediaRacer(attempt)
        }
        racers[attempt] = racer
        racer.view?.let { view -> stage?.addView(view, 0, matchParent()) }

        when (racer) {
            is MediaRacer -> racer.play(url, kind)
            is YouTubeRacer -> startYouTube(racer, url)
        }
    }

    override fun cancel(attempt: Long) {
        val racer = racers.remove(attempt) ?: return

        // The listener that was put on is the one that comes off, and nothing
        // this player says afterwards can reach the machine.
        racer.view?.let { view ->
            stage?.removeView(view)
        }
        racer.release()
    }

    override fun select(attempt: Long) {
        val racer = racers[attempt] ?: return
        // The mirror that won the race keeps the picture and is put in front
        // of the ones that are still trying.
        racer.view?.let { view ->
            stage?.bringChildToFront(view)
            view.visibility = View.VISIBLE
        }
    }

    override fun stop() {
        racers.keys.toList().forEach { cancel(it) }
    }

    override fun release() {
        stop()
        stage = null
    }

    override fun currentPositionMs(attempt: Long): Long =
        racers[attempt]?.currentPositionMs() ?: 0L

    override fun isBuffering(attempt: Long): Boolean =
        racers[attempt]?.isBuffering() ?: false

    /**
     * Looks up which video a YouTube channel is showing, and starts it.
     *
     * The lookup happens off the main thread, so a channel that cannot be
     * answered does not hold the screen up. A try that has been given up
     * while the lookup was in flight is not started at all.
     */
    private fun startYouTube(racer: YouTubeRacer, url: String) {
        scope.launch {
            val videoId = resolver.videoId(url)
            if (racers[racer.attempt] !== racer) return@launch
            if (videoId.isNullOrBlank()) {
                listener?.onError(PlaybackFault.FATAL, racer.attempt, PlaybackDetails.YOUTUBE_RESOLVE)
                return@launch
            }
            racer.surface.play(videoId)
        }
    }

    /**
     * What the YouTube player said, turned into the machine's language.
     */
    private fun onYouTubeEvent(attempt: Long, event: YouTubeEvent) {
        val racer = racers[attempt] as? YouTubeRacer ?: return
        when (event) {
            YouTubeEvent.Ready -> Unit

            is YouTubeEvent.State -> when (event.state) {
                YouTubeSurface.STATE_PLAYING -> {
                    if (!racer.pictureShown) {
                        racer.pictureShown = true
                        listener?.onFirstFrame(attempt)
                    } else {
                        listener?.onResumed(attempt)
                    }
                }

                YouTubeSurface.STATE_BUFFERING, YouTubeSurface.STATE_PAUSED ->
                    if (racer.pictureShown) listener?.onStall(attempt)
            }

            is YouTubeEvent.Error ->
                listener?.onError(PlaybackFault.FATAL, attempt, PlaybackDetails.youTubeError(event.code))

            YouTubeEvent.PageFailed ->
                listener?.onError(PlaybackFault.FATAL, attempt, PlaybackDetails.YOUTUBE_PAGE)
        }
    }

    /**
     * The source an address is played with.
     *
     * The kind decides it, and it is never left to the file name: Media3
     * reads the extension of the path and knows `m3u8`, `mpd` and `ism` and
     * nothing else — so a mirror published as `stream.php?id=…&e=.m3u8` would
     * be played as a plain file and never as a playlist. The web player reads
     * what an address returns rather than what it is called, and this does
     * the same.
     */
    private fun sourceFor(url: String, kind: StreamKind): MediaSource {
        val dataSource = DefaultHttpDataSource.Factory()
            .setUserAgent(MEDIA_USER_AGENT)
            .setConnectTimeoutMs(CONNECT_TIMEOUT_MS)
            .setReadTimeoutMs(READ_TIMEOUT_MS)
            .setAllowCrossProtocolRedirects(true)

        val item = MediaItem.fromUri(url)
        return when (kind) {
            // A raw transport stream is read by the extractor for transport
            // streams, which the default set already carries.
            StreamKind.MPEG_TS -> ProgressiveMediaSource.Factory(dataSource, DefaultExtractorsFactory())
                .createMediaSource(item)

            StreamKind.HLS, StreamKind.YOUTUBE -> HlsMediaSource.Factory(dataSource)
                .createMediaSource(item)
        }
    }

    private fun matchParent(): ViewGroup.LayoutParams = FrameLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT,
    )

    /**
     * What kind of failure the player saw.
     *
     * Only a failure of the network itself is treated as temporary: anything
     * else means the mirror is broken, and the walk moves on to the next
     * published mirror at once.
     */
    private fun faultOf(error: PlaybackException): PlaybackFault = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT,
        -> PlaybackFault.TRANSIENT

        else -> PlaybackFault.FATAL
    }

    /** What the player saw, turned into the machine's language. */
    private inner class Observer(private val attempt: Long) : Player.Listener {

        override fun onRenderedFirstFrame() {
            val racer = racers[attempt] as? MediaRacer ?: return
            racer.pictureShown = true
            listener?.onFirstFrame(attempt)
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val racer = racers[attempt] as? MediaRacer ?: return
            when (playbackState) {
                // A stall before any picture was shown is not a stall: it is a
                // feed still being tuned, and the deadline already watches it.
                Player.STATE_BUFFERING -> if (racer.pictureShown) listener?.onStall(attempt)
                Player.STATE_READY -> if (racer.pictureShown) listener?.onResumed(attempt)
            }
        }

        override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) {
            val racer = racers[attempt] as? MediaRacer ?: return
            (racer.view as? VideoSurface)?.onVideoSize(videoSize)
        }

        override fun onPlayerError(error: PlaybackException) {
            listener?.onError(faultOf(error), attempt)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 8_000
        const val READ_TIMEOUT_MS = 12_000

        /**
         * The name the streams are asked with.
         *
         * A mirror is a web resource, and some of them answer only to a
         * browser — the same way the web player, which is a browser, is
         * answered. No credential and no provider header is attached.
         */
        const val MEDIA_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/120.0.0.0 Mobile Safari/537.36 SoluPlayApp/0.1.0"
    }
}
