package com.soluplay.phone

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Muted
import com.soluplay.core.ui.Panel

/**
 * The phone home surface.
 *
 * Phase 0 is the skeleton only: the catalogue, the match board and the rails
 * arrive in Phase 1, and playback in Phase 2.
 */
@Composable
fun PhoneHomeScreen() {
    Surface(color = Background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "SoluPlay",
                color = Accent,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Panel)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "Phase 0 skeleton",
                    color = Background,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "The live catalogue, the match board and playback land in the next phases.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
