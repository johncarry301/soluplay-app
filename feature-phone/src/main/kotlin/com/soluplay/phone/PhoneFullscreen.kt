package com.soluplay.phone

import android.content.pm.ActivityInfo
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.soluplay.core.ui.activity

/**
 * The picture takes the whole screen.
 *
 * The device is turned on its side, the system bars are put away and the app
 * is drawn edge to edge, so the picture is the screen and nothing else is on
 * it. The screen is given back the way it was when the picture is not the
 * whole screen any more — and it is given back even if the watch screen is
 * left while it is full screen.
 */
@Composable
fun PhoneFullscreen(enabled: Boolean) {
    val context = LocalContext.current
    val view = LocalView.current
    val activity = context.activity()

    DisposableEffect(enabled, activity) {
        if (activity != null) {
            if (enabled) {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                WindowInsetsControllerCompat(activity.window, view).apply {
                    systemBarsBehavior =
                        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            } else {
                activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                WindowCompat.setDecorFitsSystemWindows(activity.window, true)
                WindowInsetsControllerCompat(activity.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }

        onDispose {
            activity?.let { window ->
                window.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                window.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
                WindowCompat.setDecorFitsSystemWindows(window.window, true)
                WindowInsetsControllerCompat(window.window, view)
                    .show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
