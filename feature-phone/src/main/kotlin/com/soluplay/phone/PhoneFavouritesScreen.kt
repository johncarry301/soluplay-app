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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.favourites.Favourite
import com.soluplay.core.favourites.FavouritesSnapshot
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.LogoPlate
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel

/**
 * The viewer's own list of channels.
 *
 * The list lives on the device and is written the moment a channel is marked,
 * so a viewer who marks a channel on a train with no signal does not lose it.
 * The site keeps a copy under a key the viewer carries: the key *is* the
 * identity, because the app has no accounts and never will.
 */
@Composable
fun PhoneFavouritesScreen(
    snapshot: FavouritesSnapshot,
    onRemove: (Favourite) -> Unit,
    onOpen: (Favourite) -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onAdopt: (String) -> Boolean,
) {
    var code by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }

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

        item(key = "key") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Panel)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(text = Copy.yourCode(), color = Muted, fontSize = 12.sp)
                Text(
                    text = snapshot.key,
                    color = Accent,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        item(key = "sync") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SyncButton(label = Copy.giveToServer(), onPick = onPush)
                SyncButton(label = Copy.takeFromServer(), onPick = onPull)
            }
        }

        if (snapshot.attempted) {
            item(key = "sync-state") {
                Text(
                    text = if (snapshot.synced) Copy.syncedNow() else Copy.syncFailed(),
                    color = if (snapshot.synced) Accent else Muted,
                    fontSize = 12.sp,
                )
            }
        }

        item(key = "other") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Panel)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(text = Copy.codeOfAnotherDevice(), color = Muted, fontSize = 12.sp)
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        code = it.uppercase()
                        refused = false
                    },
                    placeholder = { Text(text = "••••••••", color = Muted, fontSize = 14.sp) },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = Background,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        cursorColor = Accent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = if (refused) Copy.codeNotValid() else "",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Text(
                    text = Copy.useThisCode(),
                    color = Accent,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Background)
                        .clickable {
                            if (onAdopt(code)) {
                                code = ""
                                refused = false
                                // The other device's list is on the site; this
                                // device has to be given it.
                                onPull()
                            } else {
                                refused = true
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
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
private fun SyncButton(label: String, onPick: () -> Unit) {
    Text(
        text = label,
        color = Accent,
        fontSize = 12.sp,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Panel)
            .clickable { onPick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
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
