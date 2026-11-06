package com.soluplay.core.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.model.Channel

/**
 * The logo of a channel.
 *
 * Every logo stands on the same plate — the same white background, the same
 * shape, the same room inside it — so a grid of channels looks like a grid and
 * not a collection of different shapes. The plate is drawn behind the logo and
 * never over it, so a wide wordmark is not cut off by a shape that was meant
 * for a square one.
 *
 * A channel whose logo cannot be fetched is still a channel, so the first
 * letter of its name stands in.
 *
 * The site draws the plate as a circle on its phone grid, so the phone asks
 * for that shape and the television keeps the rounded square it has always
 * had.
 */
@Composable
fun ChannelLogo(
    channel: Channel,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    border: BorderStroke? = null,
    shape: Shape = RoundedCornerShape(percent = PLATE_CORNER_PERCENT),
) {
    LogoPlate(
        url = channel.logo,
        fallbackText = channel.name,
        modifier = modifier.size(size),
        size = size,
        border = border,
        shape = shape,
    )
}

/**
 * The plate a logo stands on.
 *
 * The background is white for every channel — some logos are published with a
 * white background of their own and some are published transparent, and the
 * plate is what makes the two look the same. The logo is fitted inside it
 * rather than cropped to it: a logo keeps the proportions it was published
 * with, and the plate only says "here is where the logo is".
 */
@Composable
fun LogoPlate(
    url: String,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    border: BorderStroke? = null,
    shape: Shape = RoundedCornerShape(percent = PLATE_CORNER_PERCENT),
) {
    Box(
        modifier = modifier
            .background(LogoPlateBackground, shape)
            .then(
                if (border == null) {
                    Modifier
                } else {
                    Modifier.border(border, shape)
                }
            )
            .padding(size * PLATE_INSET),
        contentAlignment = Alignment.Center,
    ) {
        LogoImage(
            url = url,
            fallbackText = fallbackText,
            size = size,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * A logo from the network, with a letter standing in until it arrives.
 *
 * The logo is fitted, not cropped: what was published is shown whole, in the
 * shape it was published in.
 */
@Composable
fun LogoImage(
    url: String,
    fallbackText: String,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = url) {
        value = LogoLoader.load(url)
    }

    val bitmap = image
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = LogoSupport.firstLetter(fallbackText),
                color = OnLogoPlate,
                fontSize = (size.value / 2.6f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * The dot that says a channel is on the air.
 *
 * The site puts one on every tile of its grid, and the app puts one where the
 * site puts it: on the top-right corner of the plate, in the site's emerald,
 * pulsing slowly enough to be looked at and not stared at.
 */
@Composable
fun SiteLiveDot(
    modifier: Modifier = Modifier,
    size: Dp = 10.dp,
) {
    val pulse by rememberInfiniteTransition(label = "live").animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(LiveDot.copy(alpha = pulse))
            .border(2.dp, Panel, CircleShape),
    )
}

/** The room the logo is given inside the plate. */
private const val PLATE_INSET = 0.08f

/** The corner of the plate, as a share of its short side — the same for every size. */
private const val PLATE_CORNER_PERCENT = 24
