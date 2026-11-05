package com.soluplay.core.player

/**
 * The player the machine talks to.
 *
 * The interface carries no player type on purpose: the machine's rules are
 * about ladders and deadlines, and they must be testable without a decoder,
 * a surface or a device.
 */
interface PlaybackHost {

    /** Where the machine's answers are delivered. */
    var listener: PlaybackListener?

    /**
     * Tunes this address and plays it.
     *
     * The attempt is the number of this try at playing. The host puts it on
     * everything it reports back, so the machine can tell an event belonging
     * to this try from one belonging to a mirror that is already gone.
     *
     * Every try at playing gets a player of its own, so several of them can
     * be in flight at the same time.
     *
     * The kind says how the address is to be played. The site publishes no
     * kind of its own — only an address — so the kind has been read out of
     * the address, and the host builds the player that matches it instead of
     * guessing from the file name.
     */
    fun prepare(url: String, attempt: Long, kind: StreamKind = StreamKind.HLS)

    /**
     * Gives up this try at playing.
     *
     * The player of that try is let go of, so it keeps neither a decoder nor
     * a surface, and nothing it says afterwards can reach the machine.
     */
    fun cancel(attempt: Long)

    /** Puts the player of this try at playing on the screen. */
    fun select(attempt: Long)

    /**
     * Holds the picture of this try at playing.
     *
     * The picture stays on the screen: the feed is held, not taken away.
     */
    fun pause(attempt: Long)

    /** Lets the picture of this try at playing run again. */
    fun resume(attempt: Long)

    /** Stops playing and clears the picture. */
    fun stop()

    /** Gives the player up for good. */
    fun release()

    /** The clock of the player of that try, in milliseconds, or zero. */
    fun currentPositionMs(attempt: Long): Long

    /** True while that try is waiting for data it does not have yet. */
    fun isBuffering(attempt: Long): Boolean
}

/** What the player tells the machine. Every answer carries the attempt it belongs to. */
interface PlaybackListener {
    /** A picture has been put on the screen. */
    fun onFirstFrame(attempt: Long)

    /** The player is waiting for data while a picture was up. */
    fun onStall(attempt: Long)

    /** The player is running again. */
    fun onResumed(attempt: Long)

    /** The player could not play this mirror. */
    fun onError(fault: PlaybackFault, attempt: Long, detail: String = "")
}
