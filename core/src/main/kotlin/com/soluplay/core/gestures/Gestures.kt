package com.soluplay.core.gestures

/**
 * What a gesture on the watch screen means.
 *
 * The names are the viewer's: a viewer does not "dispatch a horizontal drag",
 * a viewer swipes sideways to get another mirror.
 */
enum class GestureIntent {
    /** One tap: the words come in and go out, and the picture is never paused. */
    REVEAL_CONTROLS,

    /** Two taps: the picture takes the screen. */
    FULLSCREEN,

    /** Two fingers: the feed is held. */
    PAUSE,

    /** A swipe up: the next channel. */
    NEXT_CHANNEL,

    /** A swipe down: the channel before this one. */
    PREVIOUS_CHANNEL,

    /** A swipe sideways: the next mirror of this channel. */
    NEXT_MIRROR,
}

/**
 * What a finger did, described without a finger.
 *
 * The gesture is turned into words here and not in the surface, so the rules
 * can be read and tested on their own: the screen knows how a finger moves,
 * and this knows what the movement means.
 */
data class GestureEvent(
    /** How many taps the gesture was. */
    val tapCount: Int = 0,
    /** How many fingers were on the screen. */
    val pointerCount: Int = 1,
    /** How far the finger went sideways, in density-independent pixels. */
    val dragX: Float = 0f,
    /** How far the finger went up or down, in density-independent pixels. */
    val dragY: Float = 0f,
)

/**
 * The gestures of the watch screen.
 *
 * The rules are the ones the plan names, and they are ordered so that a
 * movement is never read as a tap: a finger that travelled is a swipe, and a
 * finger that stayed is a tap.
 */
object Gestures {

    /**
     * How far a finger has to travel before the movement is a swipe.
     *
     * A smaller movement than this is a tap that moved, and the picture is not
     * changed by a tap that moved.
     */
    const val SWIPE_THRESHOLD_DP = 48f

    /**
     * What the gesture means, or null when it means nothing.
     *
     * A swipe sideways is a request for another mirror, and a swipe up or down
     * is a request for another channel. A tap brings the words in and out, two
     * taps take the picture, and two fingers hold the feed.
     */
    fun intentOf(event: GestureEvent): GestureIntent? {
        val sideways = kotlin.math.abs(event.dragX)
        val vertical = kotlin.math.abs(event.dragY)

        if (maxOf(sideways, vertical) >= SWIPE_THRESHOLD_DP) {
            // A finger that went up or down changes the channel; one that went
            // sideways stays on this channel and asks for another mirror.
            if (vertical > sideways) {
                return if (event.dragY < 0f) GestureIntent.NEXT_CHANNEL else GestureIntent.PREVIOUS_CHANNEL
            }
            return GestureIntent.NEXT_MIRROR
        }

        if (event.pointerCount >= 2) return GestureIntent.PAUSE
        if (event.tapCount >= 2) return GestureIntent.FULLSCREEN
        if (event.tapCount == 1) return GestureIntent.REVEAL_CONTROLS
        return null
    }
}
