package com.soluplay.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.favourites.Favourite
import com.soluplay.core.favourites.FavouritesSnapshot
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.LogoPlate
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel

/**
 * The viewer's own list of channels.
 *
 * The list lives on the device and is written the moment a channel is marked,
 * so a viewer who marks a channel on a train with no signal does not lose it.
 * Nothing else is on this page: it is the list, and the list is what the
 * viewer came for.
 */
@Composable
fun PhoneFavouritesScreen(
    snapshot: FavouritesSnapshot,
    onRemove: (Favourite) -> Unit,
    onOpen: (Favourite) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "title") {
            Text(
                text = Copy.favouritesTitle(),
                color = Accent,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 10.dp),
            )
        }

        if (snapshot.items.isEmpty()) {
            item(key = "empty") {
                Text(
                    text = Copy.noFavourites(),
                    color = Muted,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 30.dp),
                )
            }
        } else {
            items(snapshot.items, key = { it.channelId }) { favourite ->
                FavouriteRow(
                    favourite = favourite,
                    onRemove = { onRemove(favourite) },
                    onOpen = { onOpen(favourite) },
                )
            }
        }
    }
}

@Composable
private fun FavouriteRow(
    favourite: Favourite,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .clickable { onOpen() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            LogoPlate(
                url = favourite.logo,
                fallbackText = favourite.name,
                size = 40.dp,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = favourite.name,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (favourite.category.isNotBlank()) {
                Text(
                    text = favourite.category,
                    color = Muted,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = Copy.removeChannel(),
            color = Muted,
            fontSize = 12.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable { onRemove() }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
