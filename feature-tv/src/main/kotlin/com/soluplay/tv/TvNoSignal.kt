package com.soluplay.tv

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.Slate200
import com.soluplay.core.ui.Slate400
import com.soluplay.core.ui.Slate700

/**
 * The screen a television shows when no mirror is sending a picture.
 *
 * A dish with its signal snapped: the picture is gone for a reason, and the
 * reason is drawn rather than left as moving noise. The dish says where the
 * picture should have come from, and the crack says the link is broken.
 */
@Composable
fun TvNoSignal(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            Canvas(modifier = Modifier.size(300.dp)) { drawBrokenDish() }
            Text(
                text = Copy.noSignal(),
                color = Slate400,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** The dish, the wave it should be hearing, and the crack through it. */
private fun DrawScope.drawBrokenDish() {
    val unit = size.minDimension / 100f
    fun at(x: Float, y: Float) = Offset(x * unit, y * unit)

    // The dish, opening towards the upper right.
    drawArc(
        color = Slate700,
        startAngle = 30f,
        sweepAngle = 270f,
        useCenter = false,
        topLeft = at(16f, 18f),
        size = Size(56f * unit, 56f * unit),
        style = Stroke(width = 3.4f * unit, cap = StrokeCap.Round),
    )

    // The arm from the dish to the receiver at its end.
    drawLine(
        color = Slate700,
        start = at(50f, 36f),
        end = at(70f, 30f),
        strokeWidth = 2.6f * unit,
        cap = StrokeCap.Round,
    )
    drawCircle(color = Slate200, radius = 3f * unit, center = at(70f, 30f))

    // The stand.
    drawLine(
        color = Slate700,
        start = at(44f, 74f),
        end = at(44f, 88f),
        strokeWidth = 2.6f * unit,
        cap = StrokeCap.Round,
    )
    drawLine(
        color = Slate700,
        start = at(32f, 88f),
        end = at(56f, 88f),
        strokeWidth = 2.6f * unit,
        cap = StrokeCap.Round,
    )

    // The wave the dish should be hearing. The outer ripple stops halfway,
    // because the link it belongs to is broken.
    val radii = listOf(8f, 14f, 20f)
    radii.forEachIndexed { index, radius ->
        drawArc(
            color = Slate400.copy(alpha = 1f - index * 0.24f),
            startAngle = -105f,
            sweepAngle = if (index == radii.lastIndex) 44f else 105f,
            useCenter = false,
            topLeft = at(70f - radius, 30f - radius),
            size = Size(radius * 2f * unit, radius * 2f * unit),
            style = Stroke(width = 2.4f * unit, cap = StrokeCap.Round),
        )
    }

    // The crack: the one thing on the screen that is not the colour of quiet.
    val crack = Path().apply {
        moveTo(58f * unit, 58f * unit)
        lineTo(67f * unit, 45f * unit)
        lineTo(61f * unit, 37f * unit)
        lineTo(70f * unit, 22f * unit)
        lineTo(65f * unit, 13f * unit)
    }
    drawPath(
        path = crack,
        color = BrokenSignal,
        style = Stroke(width = 2.6f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

/** The colour of a signal that is not arriving. */
private val BrokenSignal = Color(0xFFEF6A6A)
