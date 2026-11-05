package com.soluplay.core.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The colour of a star that is filled in. */
val StarColour = Color(0xFFFFC24B)

/**
 * The star that says whether a channel is on the viewer's list.
 *
 * A filled star means the channel is saved and a dim one means it is not, so
 * the list can be read without opening anything. The star is a button of its
 * own: marking a channel must not also open it.
 */
@Composable
fun FavouriteStar(
    marked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
) {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = if (marked) Copy.savedChannel() else Copy.saveChannel(),
        tint = if (marked) StarColour else Muted,
        modifier = modifier
            .size(size)
            .clickable { onToggle() },
    )
}
