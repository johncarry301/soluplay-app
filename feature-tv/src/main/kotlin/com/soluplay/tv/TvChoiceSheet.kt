package com.soluplay.tv

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.fixtures.playableChoices
import com.soluplay.core.model.Fixture
import com.soluplay.core.model.FixtureChoice
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.LogoPlate
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel

/**
 * The channels a match card can be watched on.
 *
 * A card with one channel opens it, a card with several asks, and a card with
 * none says so instead of opening nothing.
 */
@Composable
fun TvChoiceSheet(
    fixture: Fixture,
    onPick: (FixtureChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val choices = remember(fixture) { playableChoices(fixture) }
    val requesters = remember(choices) { choices.map { FocusRequester() } }
    val scope = rememberCoroutineScope()

    // The sheet answers for itself: the first channel is where the remote
    // lands when the sheet opens.
    LaunchedEffect(fixture) {
        requesters.firstOrNull()?.askForFocus(scope)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f)),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 120.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Panel)
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = fixture.title.ifBlank { fixture.competition },
                color = Accent,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (choices.isEmpty()) {
                Text(text = Copy.nothingToOpen(), color = Muted, fontSize = 20.sp)
            } else {
                Text(text = Copy.chooseChannel(), color = Muted, fontSize = 18.sp)
                choices.forEachIndexed { index, choice ->
                    TvChoiceRow(
                        choice = choice,
                        requester = requesters.getOrNull(index),
                        onPick = { onPick(choice) },
                    )
                }
            }

            Text(
                text = Copy.close(),
                color = Accent,
                fontSize = 20.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Background)
                    .padding(horizontal = 20.dp, vertical = 10.dp),
            )
        }
    }
}

/** One channel on the choice sheet, walked with the remote. */
@Composable
private fun TvChoiceRow(
    choice: FixtureChoice,
    requester: FocusRequester?,
    onPick: () -> Unit,
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (requester == null) Modifier else Modifier.focusRequester(requester))
            .focusable()
            .onFocusChanged { focused = it.isFocused }
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.Enter, Key.DirectionCenter, Key.NumPadEnter, Key.Spacebar -> {
                        onPick()
                        true
                    }
                    else -> false
                }
            }
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, if (focused) Accent else Color.Transparent, RoundedCornerShape(12.dp))
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            LogoPlate(
                url = choice.logoUrl,
                fallbackText = choice.name,
                size = 56.dp,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = choice.name,
                color = Color.White,
                fontSize = 22.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = choice.category,
                color = Muted,
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
