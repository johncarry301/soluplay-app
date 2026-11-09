package com.soluplay.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.browse.CATEGORY_FILTERS
import com.soluplay.core.browse.CategoryFilter

/**
 * The categories as a row of pills.
 *
 * The pills are the site's own: the same seven names, in the same order, with
 * the one that is on filled with the site's green.
 *
 * Every pill is as wide as its own name needs, and the row is moved sideways
 * when the names do not fit: a pill squeezed until its name cannot be read is
 * worse than a row the viewer has to move.
 */
@Composable
fun CategoryPillRow(
    selected: CategoryFilter?,
    onPick: (CategoryFilter?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val filters: List<CategoryFilter?> = listOf(null) + CATEGORY_FILTERS
    val labels = listOf(Copy.allChannels()) + CATEGORY_FILTERS.map { it.name }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        labels.forEachIndexed { index, label ->
            CategoryPill(
                label = label,
                active = selected == filters[index],
                onPick = { onPick(filters[index]) },
            )
        }
    }
}

/** One of the category pills. */
@Composable
private fun CategoryPill(
    label: String,
    active: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Text(
        text = label,
        color = if (active) Slate950 else Slate400,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        modifier = modifier
            .clip(shape)
            .background(if (active) Accent else PanelMid)
            .then(if (active) Modifier else Modifier.border(1.dp, Slate800, shape))
            .clickable { onPick() }
            .padding(horizontal = 8.dp, vertical = 5.dp),
    )
}
