package com.soluplay.core.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * The screen stays on while a channel is being watched.
 *
 * A screen that sleeps in the middle of a match takes the picture and the
 * sound with it, so the screen is held awake for as long as the feed is
 * running — and is let go of again when the feed is held or the screen is
 * left.
 */
@Composable
fun KeepScreenOn(enabled: Boolean) {
    val context = LocalContext.current

    DisposableEffect(enabled) {
        val window = context.activity()?.window
        if (enabled) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        onDispose {
            // Leaving the screen takes the flag with it. A change of the flag
            // itself is handled by the effect above.
            if (enabled) window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}

/** The screen this composable is on. */
fun Context.activity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}