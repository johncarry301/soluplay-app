package com.soluplay.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
 * The viewer's own list on the television.
 *
 * The list lives on the device and is written the moment a channel is marked,
 * so a viewer who marks a channel with no signal does not lose it. The site
 * keeps a copy under a key the viewer carries: the key *is* the identity,
 * because the app has no accounts and never will.
 *
 * The list is walked with the remote: the channel opens it, and the button
 * beside it takes it off the list.
 */
@Composable
fun TvFavouritesScreen(
    snapshot: FavouritesSnapshot,
    chrome: TvChrome,
    onOpen: (Favourite) -> Unit,
    onRemove: (Favourite) -> Unit,
    onPull: () -> Unit,
    onPush: () -> Unit,
    onAdopt: (String) -> Boolean,
) {
    var asking by remember { mutableStateOf(false) }

    val focus = remember { TvFocus() }
    val scope = rememberCoroutineScope()

    val syncRequesters = remember { List(SYNC_ITEMS) { FocusRequester() } }
    val itemRequesters = remember(snapshot.items) {
        snapshot.items.map { listOf(FocusRequester(), FocusRequester()) }
    }

    val rows = remember(itemRequesters, syncRequesters, chrome) {
        listOf(chrome.requesters, syncRequesters) + itemRequesters
    }

    /**
     * Puts the focus back where the viewer left it.
     *
     * A list that has changed takes its requesters with it, so the focus is
     * asked for again — on the row it was on, or on the last row there is.
     */
    LaunchedEffect(snapshot.items) {
        val row = focus.row.coerceIn(0, rows.lastIndex)
        val column = focus.column.coerceIn(0, (rows.getOrNull(row)?.lastIndex ?: 0))
        focus.place(row, column, rows, scope)
    }

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    // The code sheet answers for itself while it is open.
                    if (asking) return@onPreviewKeyEvent false
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> {
                            focus.move(rows, 0, 1, scope)
                            true
                        }
                        Key.DirectionLeft -> {
                            focus.move(rows, 0, -1, scope)
                            true
                        }
                        Key.DirectionDown -> {
                            focus.move(rows, 1, 0, scope)
                            true
                        }
                        Key.DirectionUp -> {
                            focus.move(rows, -1, 0, scope)
                            true
                        }
                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar -> {
                            when (focus.row) {
                                0 -> {
                                    chrome.press(focus.column)
                                    true
                                }
                                1 -> {
                                    when (focus.column) {
                                        0 -> onPush()
                                        1 -> onPull()
                                        else -> asking = true
                                    }
                                    true
                                }
                                else -> {
                                    val favourite = snapshot.items.getOrNull(focus.row - 2)
                                    if (favourite != null) {
                                        if (focus.column == 0) onOpen(favourite) else onRemove(favourite)
                                    }
                                    true
                                }
                            }
                        }
                        else -> false
                    }
                },
        ) {
            TvTopBar(chrome)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 48.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Panel)
                            .padding(horizontal = 22.dp, vertical = 14.dp),
                    ) {
                        Text(text = Copy.yourCode(), color = Muted, fontSize = 17.sp)
                        Text(
                            text = snapshot.key,
                            color = Accent,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    TvSyncPill(
                        text = Copy.giveToServer(),
                        requester = syncRequesters.getOrNull(0),
                    )
                    TvSyncPill(
                        text = Copy.takeFromServer(),
                        requester = syncRequesters.getOrNull(1),
                    )
                    TvSyncPill(
                        text = Copy.useThisCode(),
                        requester = syncRequesters.getOrNull(2),
                    )
                }

                if (snapshot.attempted) {
                    Text(
                        text = if (snapshot.synced) Copy.syncedNow() else Copy.syncFailed(),
                        color = if (snapshot.synced) Accent else Muted,
                        fontSize = 18.sp,
                    )
                }

                if (snapshot.items.isEmpty()) {
                    Text(
                        text = Copy.noFavourites(),
                        color = Muted,
                        fontSize = 20.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 60.dp),
                    )
                } else {
                    snapshot.items.forEachIndexed { index, favourite ->
                        TvFavouriteRow(
                            favourite = favourite,
                            requesters = itemRequesters.getOrNull(index).orEmpty(),
                            onFocused = { column ->
                                focus.place(index + 2, column, rows, scope)
                            },
                        )
                    }
                }
            }
        }
    }

    if (asking) {
        BackHandler { asking = false }
        TvCodeSheet(
            onAdopt = { code ->
                if (onAdopt(code)) {
                    asking = false
                    true
                } else {
                    false
                }
            },
            onDismiss = { asking = false },
        )
    }
}

/** How many things sit in the row of the sync buttons. */
private const val SYNC_ITEMS = 3

/** One of the buttons in the row of the sync buttons. */
@Composable
private fun TvSyncPill(text: String, requester: FocusRequester?) {
    var focused by remember { mutableStateOf(false) }

    Text(
        text = text,
        color = if (focused) Background else Muted,
        fontSize = 18.sp,
        modifier = Modifier
            .then(if (requester == null) Modifier else Modifier.focusRequester(requester))
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Accent else Panel)
            .border(2.dp, if (focused) Color.White else Color.Transparent, RoundedCornerShape(50))
            .padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

/** One channel on the list: the channel itself, and the button that takes it off. */
@Composable
private fun TvFavouriteRow(
    favourite: Favourite,
    requesters: List<FocusRequester>,
    onFocused: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(Panel)
                .then(
                    requesters.getOrNull(0)
                        ?.let { Modifier.focusRequester(it) } ?: Modifier,
                )
                .focusable()
                .onFocusChanged { if (it.isFocused) onFocused(0) }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier.size(56.dp),
                contentAlignment = Alignment.Center,
            ) {
                LogoPlate(
                    url = favourite.logo,
                    fallbackText = favourite.name,
                    size = 56.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Text(
                text = favourite.name,
                color = Color.White,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (favourite.category.isNotBlank()) {
                Text(
                    text = favourite.category,
                    color = Muted,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Text(
            text = Copy.removeChannel(),
            color = Muted,
            fontSize = 18.sp,
            modifier = Modifier
                .then(
                    requesters.getOrNull(1)
                        ?.let { Modifier.focusRequester(it) } ?: Modifier,
                )
                .focusable()
                .onFocusChanged { if (it.isFocused) onFocused(1) }
                .clip(RoundedCornerShape(50))
                .background(Panel)
                .border(2.dp, Color.Transparent, RoundedCornerShape(50))
                .padding(horizontal = 20.dp, vertical = 10.dp),
        )
    }
}

/**
 * The sheet that takes another device's code.
 *
 * The code is eight characters the viewer reads off the other device, and
 * there is no keyboard on a television — so the letters are drawn and the
 * remote picks them.
 */
@Composable
private fun TvCodeSheet(
    onAdopt: (String) -> Boolean,
    onDismiss: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    var refused by remember { mutableStateOf(false) }

    val focus = remember { TvFocus() }
    val scope = rememberCoroutineScope()

    val keyRequesters = remember {
        KEYBOARD_ROWS.map { row -> row.map { FocusRequester() } }
    }
    val buttonRequesters = remember { List(2) { FocusRequester() } }

    val rows = remember(keyRequesters, buttonRequesters) {
        keyRequesters + listOf(buttonRequesters)
    }

    fun type(key: TvKey) {
        code = when (key) {
            is TvKey.Letter -> (code + key.value).uppercase()
            TvKey.Space -> code
            TvKey.Delete -> code.dropLast(1)
            TvKey.Clear -> ""
            TvKey.Search -> code
        }
        refused = false
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f)),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 160.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Panel)
                .padding(32.dp)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionRight -> {
                            focus.move(rows, 0, 1, scope)
                            true
                        }
                        Key.DirectionLeft -> {
                            focus.move(rows, 0, -1, scope)
                            true
                        }
                        Key.DirectionDown -> {
                            focus.move(rows, 1, 0, scope)
                            true
                        }
                        Key.DirectionUp -> {
                            focus.move(rows, -1, 0, scope)
                            true
                        }
                        else -> false
                    }
                },
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = Copy.codeOfAnotherDevice(),
                color = Accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )

            Text(
                text = code.ifBlank { Copy.typeHere() },
                color = if (code.isBlank()) Muted else Color.White,
                fontSize = 30.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            if (refused) {
                Text(
                    text = Copy.codeNotValid(),
                    color = Muted,
                    fontSize = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            TvKeyboard(
                requesters = keyRequesters,
                onKey = ::type,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvSheetButton(
                    text = Copy.close(),
                    requester = buttonRequesters.getOrNull(1),
                    onFocused = { focus.place(rows.lastIndex, 1, rows, scope) },
                    onPress = onDismiss,
                )
                TvSheetButton(
                    text = Copy.useThisCode(),
                    requester = buttonRequesters.getOrNull(0),
                    onFocused = { focus.place(rows.lastIndex, 0, rows, scope) },
                    onPress = {
                        if (!onAdopt(code)) refused = true
                    },
                )
            }
        }
    }
}

/** One of the two buttons at the foot of the code sheet. */
@Composable
private fun TvSheetButton(
    text: String,
    requester: FocusRequester?,
    onFocused: () -> Unit,
    onPress: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Text(
        text = text,
        color = if (focused) Background else Accent,
        fontSize = 20.sp,
        modifier = Modifier
            .then(if (requester == null) Modifier else Modifier.focusRequester(requester))
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar -> {
                        onPress()
                        true
                    }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(50))
            .background(if (focused) Accent else Background)
            .border(2.dp, Accent, RoundedCornerShape(50))
            .padding(horizontal = 24.dp, vertical = 12.dp),
    )
}
