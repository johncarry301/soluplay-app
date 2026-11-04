package com.soluplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.soluplay.app.AppGraph
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.app.ui.SoluPlayTheme
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.BrowseViewModelFactory
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.player.PlaybackViewModelFactory

/**
 * The TV entry. It ships inside the same APK as the phone app, so a box that
 * installs one file gets both launchers.
 */
class TvActivity : ComponentActivity() {

    private val browseViewModel: BrowseViewModel by viewModels {
        BrowseViewModelFactory(AppGraph.repository(applicationContext))
    }

    /** The player is made once and lives as long as the screen does. */
    private val host: Media3PlaybackHost by lazy { Media3PlaybackHost(applicationContext) }

    private val playbackViewModel: PlaybackViewModel by viewModels {
        PlaybackViewModelFactory(
            repository = AppGraph.repository(applicationContext),
            host = host,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppGraph.prepare(applicationContext)
        setContent {
            SoluPlayTheme {
                TvHomeScreen(
                    viewModel = browseViewModel,
                    playback = playbackViewModel,
                    host = host,
                )
            }
        }
    }
}
