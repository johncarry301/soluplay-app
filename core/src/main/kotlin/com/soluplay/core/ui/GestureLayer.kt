package com.soluplay.core.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import com.soluplay.core.gestures.GestureEvent
import com.soluplay.core.gestures.GestureIntent
import com.soluplay.core.gestures.Gestures
import kotlin.math.abs
import kotlin.math.max

/**
 * The watch screen's own gestures.
 *
 * The screen knows how a finger moves; this turns that movement into the words
 * the app acts on, so the rules themselves can be read and tested on their
 * own.
 *
 * A finger that travelled is a swipe and a finger that stayed is a tap, and
 * the movement decides which — never the other way round.
 */
fun Modifier.watchGestures(onIntent: (GestureIntent) -> Unit): Modifier =
    pointerInput(onIntent) {
        var lastTapAt = 0L

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)

            // A touch that something else has already taken is not a gesture
            // on the picture: it is a tap on a control that sits over it.
            if (down.isConsumed) return@awaitEachGesture

            val start = down.position

            var dragX = 0f
            var dragY = 0f
            var pointers = 1

            do {
                val event = awaitPointerEvent()
                val pressed = event.changes.filter { it.pressed }
                if (pressed.isNotEmpty()) {
                    pointers = max(pointers, pressed.size)
                    val change = pressed.first()
                    dragX = change.position.x - start.x
                    dragY = change.position.y - start.y
                    change.consume()
                }
            } while (event.changes.any { it.pressed })

            val dragXDp = dragX.toDp().value
            val dragYDp = dragY.toDp().value

            val travelled = max(abs(dragXDp), abs(dragYDp))
            val tapCount = when {
                travelled >= Gestures.SWIPE_THRESHOLD_DP -> 0
                else -> {
                    val moment = down.uptimeMillis
                    val count = if (moment - lastTapAt <= DOUBLE_TAP_MS) 2 else 1
                    lastTapAt = moment
                    count
                }
            }

            val intent = Gestures.intentOf(
                GestureEvent(
                    tapCount = tapCount,
                    pointerCount = pointers,
                    dragX = dragXDp,
                    dragY = dragYDp,
                ),
            )

            if (intent != null) onIntent(intent)
        }
    }

/** How close together two taps have to be to be one double tap. */
private const val DOUBLE_TAP_MS = 300L
