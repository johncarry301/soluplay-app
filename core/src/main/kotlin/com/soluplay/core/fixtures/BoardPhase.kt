package com.soluplay.core.fixtures

/**
 * The phase of a match, as the board publishes it.
 *
 * The site decides when a match is on and when it is over — from the kick-off
 * and the length it was given — and publishes one of three names. The app
 * reads that name rather than working it out again from the clock.
 */
enum class BoardPhase {
    /** Not started yet. */
    UPCOMING,

    /** On now. */
    LIVE,

    /** Over. */
    FINISHED,

    /** The board published something the app does not know. */
    UNKNOWN;

    companion object {
        fun of(raw: String): BoardPhase = when (raw.trim().lowercase()) {
            "upcoming" -> UPCOMING
            "live" -> LIVE
            "finished" -> FINISHED
            else -> UNKNOWN
        }
    }
}
