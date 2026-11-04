package com.soluplay.app.player

import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The picture.
 *
 * The surface is the player's own, and the app draws its own words over it —
 * so the state of the feed is said in the app's own language, inside the
 * frame the video occupies, and never as a separate screen.
 *
 * The stage is empty when it is made: the host puts a view for every mirror
 * in the race into it, and takes away the views of the mirrors that lost.
 */
@Composable
fun PlayerSurface(host: Media3PlaybackHost, modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            FrameLayout(context).also { host.attach(it) }
        },
        update = { view -> host.attach(view) },
    )
}
