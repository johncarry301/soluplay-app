package com.soluplay.core.player

import com.soluplay.core.tls.TlsRefusal

/**
 * What the player is doing right now.
 *
 * The names are the viewer's, not the engine's: a viewer is not "tuning", a
 * viewer is waiting for a picture and the app has to say so plainly.
 */
enum class PlaybackPhase {
    /** Nothing has been asked for. */
    IDLE,

    /** The ladder is being fetched from the site. */
    LOADING,

    /** A mirror is being tuned and has not proved a picture yet. */
    TUNING,

    /** A picture is on screen and the feed is running. */
    ON_AIR,

    /** The picture stopped and the same mirror is being given one more chance. */
    RECOVERING,

    /** Every published mirror has been tried. */
    EXHAUSTED,

    /** The ladder could not be fetched at all. */
    FAILED,
}

/**
 * Why a mirror was stepped over.
 *
 * The viewer is told the reason in words, so the reason has to be one of a
 * small number of true things — a raw player error is not one of them.
 */
enum class FailoverReason {
    /** The mirror never sent a picture in the time it was given. */
    NO_PICTURE,

    /** The picture stopped arriving and did not come back. */
    FROZEN,

    /** The player could not play this mirror at all. */
    PLAYER_ERROR,

    /** The channel published no mirror to play. */
    NO_MIRROR,

    /** The ladder could not be read from the site. */
    LADDER_UNREADABLE,
}

/**
 * Why the player could not play a mirror.
 *
 * The web player treats the two differently: a failure of the network is worth
 * another try on the same mirror, and anything else means the mirror itself is
 * broken and the walk moves on at once.
 */
enum class PlaybackFault {
    /** The network let the mirror down; the mirror may still be good. */
    TRANSIENT,

    /** The mirror itself is broken; another mirror is the answer. */
    FATAL,
}

/** What the player is doing, as the screen needs to say it. */
data class PlaybackState(
    val phase: PlaybackPhase = PlaybackPhase.IDLE,
    val channelId: String = "",
    val channelName: String = "",
    val logoUrl: String = "",
    /** The category the site filed this channel under. */
    val category: String = "",
    /** Which mirror is being tried, counting from one. */
    val mirror: Int = 0,
    /** How many mirrors the site published for this channel. */
    val mirrorCount: Int = 0,
    /** How many mirrors are being tried at this moment. */
    val racing: Int = 0,
    /** Why the last mirror was stepped over, if one was. */
    val failure: FailoverReason? = null,
    /**
     * The player's own reason for that failure, when it gave one.
     *
     * The reason the viewer is given is in words; this is the short code the
     * player gave underneath it — the number YouTube refused a video with, for
     * instance — so that a failure the copy does not cover can still be named.
     */
    val detail: String = "",
) {
    /** True when there is a picture on screen. */
    val onAir: Boolean get() = phase == PlaybackPhase.ON_AIR
}

/**
 * The reasons a player gave for a failure.
 *
 * They are codes and not sentences: the words the viewer is given live in the
 * chrome of the app, and the code is what the app falls back to when the copy
 * does not cover the failure.
 */
object PlaybackDetails {

    /** The YouTube player never put a picture on the screen. */
    const val YOUTUBE_NO_PICTURE = "youtube.no.picture"

    /** The channel's current video could not be found. */
    const val YOUTUBE_RESOLVE = "youtube.resolve.failed"

    /** The YouTube page could not be loaded. */
    const val YOUTUBE_PAGE = "youtube.page.failed"

    /** The chain does not end at a certificate this app carries. */
    const val TLS_ISSUER = "tls-issuer"

    /** The certificate itself has run out. */
    const val TLS_EXPIRED = "tls-expired"

    /** This device's clock is not the clock the certificate was made for. */
    const val TLS_CLOCK = "tls-clock"

    /** The certificate is for another name. */
    const val TLS_ADDRESS = "tls-address"

    private const val YOUTUBE_ERROR = "youtube.error."

    /** The number YouTube refused a video with. */
    fun youTubeError(code: Int): String = "$YOUTUBE_ERROR$code"

    /** True when the code is a refusal by YouTube. */
    fun isYouTubeError(detail: String): Boolean = detail.startsWith(YOUTUBE_ERROR)

    /** The number a refusal by YouTube carries. */
    fun youTubeErrorCode(detail: String): String = detail.removePrefix(YOUTUBE_ERROR)

    /** The reason a certificate was turned away, as the app names it. */
    fun tlsDetail(reason: TlsRefusal): String = when (reason) {
        TlsRefusal.ISSUER -> TLS_ISSUER
        TlsRefusal.EXPIRED -> TLS_EXPIRED
        TlsRefusal.CLOCK -> TLS_CLOCK
        TlsRefusal.ADDRESS -> TLS_ADDRESS
    }
}

/**
 * One thing the player has to be told to do.
 *
 * The machine never touches a player: it says what should happen and the
 * surface that owns the player does it.
 */
sealed interface PlaybackCommand {
    /**
     * Tune this address and play it.
     *
     * The attempt is the number of this try at playing. Every event the player
     * reports back carries the attempt it belongs to, so a picture, a stall or
     * an error from a mirror that has already been given up cannot be mistaken
     * for the one being waited for.
     *
     * More than one of these can be outstanding at the same time: that is the
     * race. Each one is played by a player of its own.
     *
     * The kind says how the address is to be played. The site publishes no
     * kind of its own — only an address — so the kind is read out of the
     * address and carried here, so the surface builds the right player for it
     * instead of guessing.
     */
    data class Prepare(
        val url: String,
        val attempt: Long,
        val kind: StreamKind = StreamKind.HLS,
    ) : PlaybackCommand

    /**
     * Give up this try at playing and let go of its player.
     *
     * The player is released, not paused: a mirror that lost the race must
     * not keep a decoder, a buffer or a surface of its own.
     */
    data class Cancel(val attempt: Long) : PlaybackCommand

    /**
     * This try at playing is the one on the screen.
     *
     * The others are cancelled, and this one keeps the picture.
     */
    data class Select(val attempt: Long) : PlaybackCommand

    /** Stop playing and clear the picture. */
    data object Stop : PlaybackCommand
}

/**
 * The numbers the machine keeps, and the ladder it walks.
 *
 * The two deadlines are the web player's own, so the app behaves the way the
 * site does: a mirror that has not sent a picture in ten seconds is stepped
 * over, and a picture that stops is given a moment to come back before the
 * mirror is given up.
 */
object PlaybackRules {
    /**
     * How long a mirror has to prove a picture.
     *
     * The same deadline the web player uses. A live feed that has not put a
     * frame on the screen in ten seconds is not going to.
     */
    const val FIRST_FRAME_DEADLINE_MS = 10_000L

    /**
     * How long a frozen picture is held before the feed is called stalled.
     *
     * Live feeds jitter for a few hundred milliseconds constantly, so a stall
     * has to outlast this before the picture is written off.
     */
    const val STALL_GRACE_MS = 1_200L

    /**
     * How many times a mirror is given a second chance after a network failure.
     *
     * The web player's own number: it re-reads the manifest of the same
     * mirror twice before it gives that mirror up, so a healthy mirror is not
     * retired over a hiccup.
     */
    const val TRANSIENT_RETRIES = 2

    /**
     * How long the last try at a failing mirror is given.
     *
     * The web player's own recovery window: three seconds, and then the walk
     * moves on to the next published mirror.
     */
    const val TRANSIENT_RECOVERY_MS = 3_000L

    /**
     * How many mirrors are tried at the same time.
     *
     * The race: the first published mirrors are tuned together, and the first
     * of them to send a picture keeps it. The number is small on purpose —
     * every mirror in the race is a player of its own, with a decoder and a
     * connection of its own, and a device that is asked for too many at once
     * answers none of them.
     */
    const val RACE_SIZE = 3

    /**
     * How long a YouTube mirror has to prove a picture.
     *
     * A YouTube stream is played by YouTube's own player inside a web view,
     * and that player has to fetch its page, its player API and its own
     * advertising before anything is on the screen — so it is given longer
     * than a feed the app fetches itself. Without this a working YouTube
     * mirror would be given up while it was still starting, and the picture
     * that was already moving would be taken away with it.
     */
    const val YOUTUBE_FIRST_FRAME_DEADLINE_MS = 40_000L
}
