package com.soluplay.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.fixtures.FixtureOption
import com.soluplay.core.fixtures.openableOptions
import com.soluplay.core.model.Fixture
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.LogoPlate
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel

/**
 * The things a match card can be watched on.
 *
 * A card with one opens it, a card with several asks, and a card with none
 * says so instead of opening nothing. The match's own broadcast link is one
 * more row beside its channels, last in the order the admin set.
 */
@Composable
fun PhoneChoiceSheet(
    fixture: Fixture,
    onPick: (FixtureOption) -> Unit,
    onDismiss: () -> Unit,
) {
    val options = openableOptions(fixture)

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .clickable { onDismiss() },
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Panel)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = fixture.title.ifBlank { fixture.competition },
                color = Accent,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (options.isEmpty()) {
                Text(
                    text = Copy.nothingToOpen(),
                    color = Muted,
                    fontSize = 13.sp,
                )
            } else {
                Text(
                    text = Copy.chooseChannel(),
                    color = Muted,
                    fontSize = 12.sp,
                )
                options.forEach { option ->
                    when (option) {
                        is FixtureOption.Channel -> ChoiceRow(
                            title = option.choice.name,
                            subtitle = option.choice.category,
                            logoUrl = option.choice.logoUrl,
                            fallbackText = option.choice.name,
                            onPick = { onPick(option) },
                        )

                        is FixtureOption.MatchLink -> ChoiceRow(
                            title = Copy.directBroadcast(),
                            subtitle = Copy.matchLink(),
                            logoUrl = null,
                            fallbackText = "",
                            onPick = { onPick(option) },
                        )
                    }
                }
            }

            Text(
                text = Copy.close(),
                color = Accent,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Background)
                    .clickable { onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String,
    logoUrl: String?,
    fallbackText: String,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onPick() }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(40.dp)
                .height(40.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (logoUrl != null) {
                LogoPlate(
                    url = logoUrl,
                    fallbackText = fallbackText,
                    size = 40.dp,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // The match link has no channel picture: the mark says it plays,
                // rather than standing in a logo-shaped hole.
                Text(
                    text = "▶",
                    color = Accent,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Black,
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
