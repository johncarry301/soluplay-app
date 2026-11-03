package com.soluplay.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.soluplay.core.ui.Accent
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.Panel

private val SoluPlayColors = darkColorScheme(
    primary = Accent,
    background = Background,
    surface = Panel,
)

@Composable
fun SoluPlayTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = SoluPlayColors, content = content)
}
