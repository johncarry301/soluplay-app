package com.soluplay.phone

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.LiveDot
import com.soluplay.core.ui.Slate800
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The frame that says a mirror is being tried.
 *
 * A mirror that has not sent a picture yet leaves the screen black, and a
 * black screen says nothing at all — so the place where the picture is going
 * to be is drawn instead: an outline whose colour travels around the edge of
 * the frame.
 *
 * The frame is the shape of the picture and no bigger: it sits around the
 * area the video will take, and never around the whole screen.
 */
@Composable
fun PlayerLoadingFrame(modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "phase").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier) {
        val stroke = 3.dp.toPx()
        val corner = 12.dp.toPx()

        // The frame of the picture: as wide as the screen allows and in the
        // proportions a channel is published in.
        val frameWidth = min(size.width, size.height * 16f / 9f)
        val frameHeight = frameWidth * 9f / 16f
        val left = (size.width - frameWidth) / 2f
        val top = (size.height - frameHeight) / 2f

        val points = perimeter(left, top, frameWidth, frameHeight, corner, EDGE_POINTS)

        // The frame itself, quietly there, so the place of the picture is
        // known even when the colour is elsewhere.
        val outline = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
            close()
        }
        drawPath(outline, Slate800, style = Stroke(width = stroke))

        // The colour that travels around the edge: a band of colour moving
        // along the frame, fading away at both of its ends.
        val band = points.size
        for (index in points.indices) {
            val u = ((index.toFloat() / band) + phase) % 1f
            if (u > BAND) continue

            val t = u / BAND
            val colour = ramp(t)
            val alpha = min(1f, min(t / 0.18f, (1f - t) / 0.18f))
            drawLine(
                color = colour.copy(alpha = alpha),
                start = points[index],
                end = points[(index + 1) % band],
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * The points around a rounded rectangle, in order, spread evenly along it.
 *
 * The points are spread by the length of each side rather than by counting
 * them, so the colour moves at the same speed everywhere around the frame.
 */
private fun perimeter(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    radius: Float,
    count: Int,
): List<Offset> {
    val right = left + width
    val bottom = top + height
    val r = min(radius, min(width, height) / 2f)

    val pieces = listOf(
        Piece(width - 2f * r) { t -> Offset(left + r + t * (width - 2f * r), top) },
        Piece(r * PI.toFloat() / 2f) { t ->
            arcPoint(right - r, top + r, r, -90f + t * 90f)
        },
        Piece(height - 2f * r) { t -> Offset(right, top + r + t * (height - 2f * r)) },
        Piece(r * PI.toFloat() / 2f) { t ->
            arcPoint(right - r, bottom - r, r, t * 90f)
        },
        Piece(width - 2f * r) { t -> Offset(right - r - t * (width - 2f * r), bottom) },
        Piece(r * PI.toFloat() / 2f) { t ->
            arcPoint(left + r, bottom - r, r, 90f + t * 90f)
        },
        Piece(height - 2f * r) { t -> Offset(left, bottom - r - t * (height - 2f * r)) },
        Piece(r * PI.toFloat() / 2f) { t ->
            arcPoint(left + r, top + r, r, 180f + t * 90f)
        },
    )

    val total = pieces.sumOf { it.length.toDouble() }.toFloat()
    val points = ArrayList<Offset>(count)
    pieces.forEach { piece ->
        val share = max(2, (count * piece.length / total).roundToInt())
        for (step in 0 until share) {
            points += piece.at(step.toFloat() / share)
        }
    }
    return points
}

/** One straight or curved part of the frame. */
private class Piece(val length: Float, val at: (Float) -> Offset)

/** A point on a circle, at the given angle in degrees. */
private fun arcPoint(centerX: Float, centerY: Float, radius: Float, degrees: Float): Offset {
    val radians = degrees * PI.toFloat() / 180f
    return Offset(
        centerX + radius * cos(radians),
        centerY + radius * sin(radians),
    )
}

/** The colour of the band at the given place along it. */
private fun ramp(t: Float): Color {
    val scaled = t * (BAND_COLOURS.size - 1)
    val index = scaled.toInt().coerceAtMost(BAND_COLOURS.size - 2)
    return lerp(BAND_COLOURS[index], BAND_COLOURS[index + 1], scaled - index)
}

/** How much of the frame the colour covers at any one moment. */
private const val BAND = 0.32f

/** How many points the frame is drawn with. */
private const val EDGE_POINTS = 96

/** The colours the band is made of. */
private val BAND_COLOURS = listOf(
    Accent,
    LiveDot,
    Color(0xFF38BDF8),
    Color(0xFFA78BFA),
    Color(0xFFFBBF24),
)
