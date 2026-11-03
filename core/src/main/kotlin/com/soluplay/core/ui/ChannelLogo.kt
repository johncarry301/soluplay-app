package com.soluplay.core.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.model.Channel

/**
 * The circular logo of a channel.
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
) {
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(Panel),
            contentAlignment = Alignment.Center,
        ) {
            LogoImage(
                url = channel.logo,
                fallbackText = channel.name,
                size = size,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * A logo from the network, with a letter standing in until it arrives.
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
            contentScale = ContentScale.Crop,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = fallbackText.trim().take(1).uppercase(),
                color = Muted,
                fontSize = (size.value / 2.6f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
