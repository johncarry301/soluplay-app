package com.soluplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.app.ui.SoluPlayTheme
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.BrowseViewModelFactory
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.player.PlaybackViewModelFactory
import com.soluplay.phone.PhoneHomeScreen

/**
 * The handset entry. It lives in the phone flavour because the TV build has
 * no phone surface to open.
 */
class MainActivity : ComponentActivity() {

    private val browseViewModel: BrowseViewModel by viewModels {
        BrowseViewModelFactory(AppGraph.repository(applicationContext))
    }

    /**
     * The player is made once and lives as long as the screen does.
     *
     * The watch screen and the playback rules share it: the surface draws the
     * picture and the rules decide which mirror is on it.
     */
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
                PhoneHomeScreen(
                    viewModel = browseViewModel,
                    playback = playbackViewModel,
                    host = host,
                )
            }
        }
    }
}
