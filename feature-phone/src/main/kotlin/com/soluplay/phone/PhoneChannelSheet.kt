package com.soluplay.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.browse.CATEGORY_FILTERS
import com.soluplay.core.browse.CategoryFilter
import com.soluplay.core.browse.channelsInFilter
import com.soluplay.core.model.Channel
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Copy
import com.soluplay.core.ui.LiveDot
import com.soluplay.core.ui.Panel
import com.soluplay.core.ui.PanelDeep
import com.soluplay.core.ui.PanelMid
import com.soluplay.core.ui.SiteChannelTile
import com.soluplay.core.ui.Slate400
import com.soluplay.core.ui.Slate800
import com.soluplay.core.ui.Slate950

/** How many channels stand side by side in the sheet, the way the site stands them. */
private const val COLUMNS = 4

/**
 * The channels a viewer can switch to.
 *
 * It is the site's own panel: the name of the list, the categories as a row of
 * pills, and the channels as a grid of round plates. A tap takes the picture
 * to that channel.
 */
@Composable
fun PhoneChannelSheet(
    channels: List<Channel>,
    current: Channel,
    onPick: (Channel) -> Unit,
    onDismiss: () -> Unit,
) {
    var filter by remember { mutableStateOf<CategoryFilter?>(null) }
    val visible = remember(channels, filter) { channelsInFilter(channels, filter) }
    val rows = remember(visible) { visible.chunked(COLUMNS) }

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
                .fillMaxHeight(0.72f)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(Panel),
        ) {
            // The name of the list and how many channels it holds.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelMid)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = filter?.name ?: Copy.allChannels(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "${visible.size} ${Copy.live()}",
                    color = LiveDot,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PanelDeep)
                        .border(1.dp, LiveDot.copy(alpha = 0.3f), CircleShape)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }

            // The categories, as a row of pills.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PanelDeep)
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                SheetPill(label = Copy.allChannels(), active = filter == null) { filter = null }
                CATEGORY_FILTERS.forEach { category ->
                    SheetPill(label = category.name, active = filter == category) { filter = category }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Slate800.copy(alpha = 0.6f)),
            )

            // The channels themselves, four to a line.
            LazyColumn(
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f),
            ) {
                itemsIndexed(rows, key = { index, _ -> "row:$index" }) { _, row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        row.forEach { channel ->
                            SiteChannelTile(
                                channel = channel,
                                active = channel.id == current.id,
                                onPick = { onPick(channel) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(COLUMNS - row.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

/** One of the category pills in the sheet. */
@Composable
private fun SheetPill(label: String, active: Boolean, onPick: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        color = if (active) Slate950 else Slate400,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(if (active) Accent else PanelMid)
            .then(if (active) Modifier else Modifier.border(1.dp, Slate800, shape))
            .clickable { onPick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
