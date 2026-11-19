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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap

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

/** A speaker with two waves: the picture has a sound and it can be silenced. */
@Composable
fun VolumeGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        drawSpeaker(tint, unit)
        val stroke = 2f * unit
        drawArc(
            color = tint,
            startAngle = -42f,
            sweepAngle = 84f,
            useCenter = false,
            topLeft = Offset(12f * unit - 5.5f * unit, 12f * unit - 5.5f * unit),
            size = Size(11f * unit, 11f * unit),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawArc(
            color = tint,
            startAngle = -42f,
            sweepAngle = 84f,
            useCenter = false,
            topLeft = Offset(12f * unit - 8.5f * unit, 12f * unit - 8.5f * unit),
            size = Size(17f * unit, 17f * unit),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
    }
}

/** A speaker with a cross: the picture has been silenced. */
@Composable
fun MutedGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        drawSpeaker(tint, unit)
        val stroke = Stroke(width = 2f * unit, cap = StrokeCap.Round)
        drawLine(tint, Offset(16f * unit, 9f * unit), Offset(22f * unit, 15f * unit), stroke.width, stroke.cap)
        drawLine(tint, Offset(22f * unit, 9f * unit), Offset(16f * unit, 15f * unit), stroke.width, stroke.cap)
    }
}

/** The speaker both sound marks share, in a 24-unit space. */
private fun DrawScope.drawSpeaker(tint: Color, unit: Float) {
    val path = Path().apply {
        moveTo(3f * unit, 9f * unit)
        lineTo(7f * unit, 9f * unit)
        lineTo(12f * unit, 4.5f * unit)
        lineTo(12f * unit, 19.5f * unit)
        lineTo(7f * unit, 15f * unit)
        lineTo(3f * unit, 15f * unit)
        close()
    }
    drawPath(path, tint)
}

/** A dial with a needle: the size of the picture can be chosen. */
@Composable
fun GaugeGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        val stroke = 2f * unit
        drawArc(
            color = tint,
            startAngle = 150f,
            sweepAngle = 240f,
            useCenter = false,
            topLeft = Offset(4f * unit, 6f * unit),
            size = Size(16f * unit, 16f * unit),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(
            color = tint,
            start = Offset(12f * unit, 14f * unit),
            end = Offset(16.5f * unit, 9.5f * unit),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawCircle(tint, radius = 1.6f * unit, center = Offset(12f * unit, 14f * unit))
    }
}

/** A circular arrow: the next mirror can be asked for. */
@Composable
fun RetryGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        val stroke = 2.2f * unit
        drawArc(
            color = tint,
            startAngle = 150f,
            sweepAngle = 300f,
            useCenter = false,
            topLeft = Offset(4f * unit, 4f * unit),
            size = Size(16f * unit, 16f * unit),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        // The arrowhead at the open end of the arc, pointing back the way
        // the arrow came.
        val head = Path().apply {
            moveTo(2.4f * unit, 6.4f * unit)
            lineTo(5.4f * unit, 10.2f * unit)
            lineTo(9.2f * unit, 7.2f * unit)
            close()
        }
        drawPath(head, tint)
    }
}

/** A television: the guide's own mark for a grid with nothing on it. */
@Composable
fun TvGlyph(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val unit = size.minDimension / 24f
        val stroke = 2f * unit
        drawRoundRect(
            color = tint,
            topLeft = Offset(2f * unit, 7f * unit),
            size = Size(20f * unit, 14f * unit),
            cornerRadius = CornerRadius(2f * unit),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(tint, Offset(7f * unit, 2.5f * unit), Offset(12f * unit, 7f * unit), stroke, StrokeCap.Round)
        drawLine(tint, Offset(17f * unit, 2.5f * unit), Offset(12f * unit, 7f * unit), stroke, StrokeCap.Round)
    }
}
