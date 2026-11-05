package com.soluplay.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.model.Channel
import com.soluplay.core.search.ChannelSearch
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.ChannelLogo
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.StarColour

/**
 * The search on the television.
 *
 * The television has no keyboard of its own, so the letters are drawn on the
 * screen and the remote picks them. What is found is shown as it is typed.
 *
 * The guide is the server's and the search never re-ranks it: a channel that
 * is found is shown in the order the site published it, and the search only
 * says which of those channels the query names.
 */
@Composable
fun TvSearchScreen(
    channels: List<Channel>,
    chrome: TvChrome,
    marked: Set<String>,
    onOpen: (Channel) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(channels, query) { ChannelSearch.results(channels, query) }

    val focus = remember { TvFocus() }
    val scope = rememberCoroutineScope()

    val keyRequesters = remember {
        KEYBOARD_ROWS.map { row -> row.map { FocusRequester() } }
    }
    val resultRequesters = remember(results) { results.map { FocusRequester() } }

    val rows = remember(resultRequesters, keyRequesters, chrome) {
        listOf(chrome.requesters, resultRequesters) + keyRequesters
    }

    /** What a key on the keyboard means. */
    fun type(key: TvKey) {
        query = when (key) {
            is TvKey.Letter -> query + key.value
            TvKey.Space -> query + " "
            TvKey.Delete -> query.dropLast(1)
            TvKey.Clear -> ""
            TvKey.Search -> query
        }
    }

    LaunchedEffect(Unit) {
        chrome.requesters.firstOrNull()?.askForFocus(scope)
    }

    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
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
                        Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar -> {
                            when (focus.row) {
                                0 -> {
                                    chrome.press(focus.column)
                                    true
                                }
                                1 -> {
                                    val channel = results.getOrNull(focus.column)
                                    if (channel != null) onOpen(channel)
                                    true
                                }
                                // The keyboard's own keys answer for themselves.
                                else -> false
                            }
                        }
                        else -> false
                    }
                },
        ) {
            TvTopBar(chrome)

            Text(
                text = query.ifBlank { Copy.searchHint() },
                color = if (query.isBlank()) Muted else Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 48.dp, end = 48.dp, top = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (query.isNotBlank() && results.isEmpty()) {
                Text(
                    text = Copy.nothingFound(),
                    color = Muted,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(start = 48.dp, top = 8.dp),
                )
            }

            if (results.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 48.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    results.forEachIndexed { index, channel ->
                        val requester = resultRequesters.getOrNull(index) ?: return@forEachIndexed
                        TvResultTile(
                            channel = channel,
                            marked = channel.id in marked,
                            requester = requester,
                            onFocused = { focus.place(1, index, rows, scope) },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            TvKeyboard(
                requesters = keyRequesters,
                onKey = ::type,
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 16.dp),
            )
        }
    }
}

/** One channel the search found. */
@Composable
private fun TvResultTile(
    channel: Channel,
    marked: Boolean,
    requester: FocusRequester,
    onFocused: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .width(160.dp)
            .focusRequester(requester)
            .focusable()
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .clip(RoundedCornerShape(12.dp))
            .background(Panel)
            .border(3.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box {
            ChannelLogo(channel = channel, size = 88.dp)
            if (marked) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = Copy.savedChannel(),
                    tint = StarColour,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(22.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Background)
                        .padding(2.dp),
                )
            }
        }

        Text(
            text = channel.name,
            color = Color.White,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
