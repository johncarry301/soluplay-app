package com.soluplay.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Muted

/**
 * The TV home surface.
 *
 * The D-pad is handled by Compose's own focus system rather than by a TV
 * specific library: a focusable surface takes the first D-pad press and the
 * arrows walk the focus chain from there. Phase 0 is the skeleton only — the
 * focus grid, the rails and the corner preview arrive in Phase 1 and Phase 5.
 */
@Composable
fun TvHomeScreen() {
    val firstTarget = FocusRequester()

    LaunchedEffect(Unit) {
        firstTarget.requestFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Text(
            text = "SoluPlay",
            style = MaterialTheme.typography.headlineLarge,
            color = Accent,
        )
        Text(
            text = "Android TV build — Phase 0 skeleton",
            style = MaterialTheme.typography.bodyLarge,
            color = Muted,
        )
        Box(
            modifier = Modifier
                .focusRequester(firstTarget)
                .focusable()
                .background(Accent)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(
                text = "Focus target",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Black,
            )
        }
    }
}
