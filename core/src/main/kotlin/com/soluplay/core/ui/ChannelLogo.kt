package com.soluplay.core.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
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
 * rounded shape, the same room inside it — so a grid of channels looks like a
 * grid and not a collection of different shapes. The plate is drawn behind the
 * logo and never over it, so a wide wordmark is not cut off by a shape that
 * was meant for a square one.
 *
 * A channel whose logo cannot be fetched is still a channel, so the first
 * letter of its name stands in.
 *
 * There is no live dot on it: the site publishes how many mirrors a channel
 * has, not whether the channel is on air, and a dot that is always on says
 * nothing.
 */
@Composable
fun ChannelLogo(
    channel: Channel,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    border: BorderStroke? = null,
) {
    LogoPlate(
        url = channel.logo,
        fallbackText = channel.name,
        modifier = modifier.size(size),
        size = size,
        border = border,
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
) {
    Box(
        modifier = modifier
            .background(LogoPlateBackground, RoundedCornerShape(percent = PLATE_CORNER_PERCENT))
            .then(
                if (border == null) {
                    Modifier
                } else {
                    Modifier.border(border, RoundedCornerShape(percent = PLATE_CORNER_PERCENT))
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

/** The room the logo is given inside the plate. */
private const val PLATE_INSET = 0.08f

/** The corner of the plate, as a share of its short side — the same for every size. */
private const val PLATE_CORNER_PERCENT = 24
