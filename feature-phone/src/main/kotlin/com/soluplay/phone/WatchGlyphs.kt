package com.soluplay.phone

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope

/**
 * The marks the watch screen carries.
 *
 * They are drawn rather than taken from an icon set: the app ships with the
 * small icon set only, and a picture that cannot be drawn is a picture that
 * cannot be shown.
 */

/** Two bars: the feed is running and can be held. */
@Composable
fun PauseGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val bar = size.width * 0.2f
        val gap = size.width * 0.16f
        val height = size.height * 0.66f
        val top = (size.height - height) / 2f
        val left = (size.width - bar * 2 - gap) / 2f
        val radius = CornerRadius(bar / 2f)
        drawRoundRect(tint, Offset(left, top), Size(bar, height), radius)
        drawRoundRect(tint, Offset(left + bar + gap, top), Size(bar, height), radius)
    }
}

/** A triangle: the feed is held and can be let run again. */
@Composable
fun PlayGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val height = size.height * 0.66f
        val top = (size.height - height) / 2f
        val path = Path().apply {
            moveTo(size.width * 0.3f, top)
            lineTo(size.width * 0.76f, size.height / 2f)
            lineTo(size.width * 0.3f, top + height)
            close()
        }
        drawPath(path, tint)
    }
}

/** Four corners: the picture can take the whole screen. */
@Composable
fun FullscreenGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { drawCorners(tint, 0f) }
}

/** Four corners drawn inwards: the picture can give the screen back. */
@Composable
fun LeaveFullscreenGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) { drawCorners(tint, size.minDimension * 0.2f) }
}

/** An arrow up and an arrow down: the channel is changed by swiping. */
@Composable
fun SwipeGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val width = size.width
        val height = size.height
        val stroke = width * 0.11f
        val head = width * 0.32f

        drawRect(
            color = tint,
            topLeft = Offset(width / 2f - stroke / 2f, height * 0.2f),
            size = Size(stroke, height * 0.6f),
        )

        val up = Path().apply {
            moveTo(width / 2f, height * 0.04f)
            lineTo(width / 2f + head / 2f, height * 0.24f)
            lineTo(width / 2f - head / 2f, height * 0.24f)
            close()
        }
        drawPath(up, tint)

        val down = Path().apply {
            moveTo(width / 2f, height * 0.96f)
            lineTo(width / 2f + head / 2f, height * 0.76f)
            lineTo(width / 2f - head / 2f, height * 0.76f)
            close()
        }
        drawPath(down, tint)
    }
}

/** The four corners of the screen, drawn at the given distance from them. */
private fun DrawScope.drawCorners(tint: Color, inset: Float) {
    val arm = size.minDimension * 0.32f
    val thick = size.minDimension * 0.1f
    val left = inset
    val top = inset
    val right = size.width - inset
    val bottom = size.height - inset

    drawRect(tint, Offset(left, top), Size(arm, thick))
    drawRect(tint, Offset(left, top), Size(thick, arm))
    drawRect(tint, Offset(right - arm, top), Size(arm, thick))
    drawRect(tint, Offset(right - thick, top), Size(thick, arm))
    drawRect(tint, Offset(left, bottom - thick), Size(arm, thick))
    drawRect(tint, Offset(left, bottom - arm), Size(thick, arm))
    drawRect(tint, Offset(right - arm, bottom - thick), Size(arm, thick))
    drawRect(tint, Offset(right - thick, bottom - arm), Size(thick, arm))
}
