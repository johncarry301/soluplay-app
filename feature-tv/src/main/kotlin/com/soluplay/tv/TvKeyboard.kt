package com.soluplay.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel

/**
 * The keys of the television's keyboard.
 *
 * A television has no keyboard of its own, so the letters are drawn and the
 * remote picks them.
 */
sealed interface TvKey {

    /** A letter of the alphabet. */
    data class Letter(val value: String) : TvKey

    /** A space between two words. */
    data object Space : TvKey

    /** Takes the last letter away. */
    data object Delete : TvKey

    /** Takes the whole word away. */
    data object Clear : TvKey

    /** The search is finished. */
    data object Search : TvKey
}

/**
 * The keys, in the order they are drawn.
 *
 * The last row holds the keys that are not letters, so every row has ten
 * things in it and the remote walks a square grid.
 */
internal val KEYBOARD_ROWS: List<List<TvKey>> = listOf(
    ('A'..'J').map { TvKey.Letter(it.toString()) },
    ('K'..'T').map { TvKey.Letter(it.toString()) },
    listOf("U", "V", "W", "X", "Y", "Z").map { TvKey.Letter(it) } +
        listOf(TvKey.Space, TvKey.Delete, TvKey.Clear, TvKey.Search),
)

/**
 * The keyboard.
 *
 * The keys are drawn and the remote walks them; the key that is pressed is
 * told to whoever is typing. The keys handle their own Enter, so a screen
 * that uses the keyboard only has to say what a key means.
 */
@Composable
fun TvKeyboard(
    requesters: List<List<FocusRequester>>,
    onKey: (TvKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        KEYBOARD_ROWS.forEachIndexed { rowIndex, keys ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                keys.forEachIndexed { columnIndex, key ->
                    val requester = requesters.getOrNull(rowIndex)?.getOrNull(columnIndex)
                    TvKeyCap(
                        key = key,
                        requester = requester,
                        onPress = { onKey(key) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/** One key, with the letter or the word on it. */
@Composable
private fun TvKeyCap(
    key: TvKey,
    requester: FocusRequester?,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }

    val label = when (key) {
        is TvKey.Letter -> key.value
        TvKey.Space -> Copy.spaceKey()
        TvKey.Delete -> Copy.deleteKey()
        TvKey.Clear -> Copy.clearKey()
        TvKey.Search -> Copy.searchTab()
    }

    Box(
        modifier = modifier
            .then(if (requester == null) Modifier else Modifier.focusRequester(requester))
            .focusable()
            .onFocusChanged { focused = it.isFocused }
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
            .clip(RoundedCornerShape(10.dp))
            .background(if (focused) Accent else Panel)
            .border(
                width = 2.dp,
                color = if (focused) Color.White else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (focused) Background else Muted,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
