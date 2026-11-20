package com.soluplay.app

import android.app.UiModeManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soluplay.app.player.Media3PlaybackHost
import com.soluplay.app.ui.SoluPlayTheme
import com.soluplay.app.update.ApkInstaller
import com.soluplay.app.update.UpdateViewModel
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.BrowseViewModelFactory
import com.soluplay.core.favourites.FavouritesViewModel
import com.soluplay.core.player.PlaybackViewModel
import com.soluplay.core.player.PlaybackViewModelFactory
import com.soluplay.core.ui.Background
import com.soluplay.core.ui.LocalUiLanguage
import com.soluplay.core.ui.UiLanguageStore
import com.soluplay.core.ui.UpdateBlockPanel
import com.soluplay.core.ui.UpdateSheet
import com.soluplay.core.update.UpdateState
import com.soluplay.phone.PhoneShell
import com.soluplay.tv.TvActivity

/**
 * The handset entry. It lives in the phone flavour because the TV build has
 * no phone surface to open.
 */
class MainActivity : ComponentActivity() {

    /** Set when the door was only a passage to the television screen. */
    private var forwarded = false

    private val browseViewModel: BrowseViewModel by viewModels {
        BrowseViewModelFactory(AppGraph.repository(applicationContext))
    }

    /**
     * The player is made once and lives as long as the app does.
     *
     * The watch screen and the playback rules share it: the surface draws the
     * picture and the rules decide which mirror is on it. It belongs to the
     * app rather than to this screen, so the picture is still there after the
     * phone is turned on its side.
     */
    private val host: Media3PlaybackHost by lazy { AppGraph.host(applicationContext) }

    private val playbackViewModel: PlaybackViewModel by viewModels {
        PlaybackViewModelFactory(
            repository = AppGraph.repository(applicationContext),
            host = host,
        )
    }

    /** The viewer's own list of channels, kept on this device. */
    private val favouritesViewModel: FavouritesViewModel by viewModels {
        AppGraph.favourites(applicationContext)
    }

    /** The language the chrome speaks, and the choice that is remembered. */
    private val languageStore: UiLanguageStore by lazy { AppGraph.language(applicationContext) }

    /** The updater, which asks the site whether there is a newer build. */
    private val updateViewModel: UpdateViewModel by viewModels {
        AppGraph.updater(applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // A box whose launcher opens the handset door is still a television:
        // the plain LAUNCHER category lands here on any box that does not run
        // the Android TV home, so the television is sent to its own screen
        // before a single frame of the phone is drawn.
        if (isTelevision()) {
            forwarded = true
            startActivity(Intent(this, TvActivity::class.java))
            finish()
            return
        }

        AppGraph.prepare(applicationContext)
        setContent {
            SoluPlayTheme {
                val updateState by updateViewModel.state.collectAsStateWithLifecycle()
                val spoken by languageStore.language.collectAsStateWithLifecycle()

                Surface(color = Background, modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        val blocked = updateState as? UpdateState.Blocked
                        if (blocked != null) {
                            // A build that may not play is not shown the
                            // guide: there is nothing on it that works.
                            UpdateBlockPanel(
                                state = blocked,
                                onInstall = { updateViewModel.install() },
                                onAllowInstall = { allowInstall() },
                            )
                        } else {
                            CompositionLocalProvider(LocalUiLanguage provides spoken) {
                                PhoneShell(
                                    viewModel = browseViewModel,
                                    playback = playbackViewModel,
                                    host = host,
                                    favourites = favouritesViewModel,
                                )
                            }
                        }

                        UpdateSheet(
                            state = updateState,
                            alignment = Alignment.BottomCenter,
                            onInstall = { updateViewModel.install() },
                            onAllowInstall = { allowInstall() },
                            onLater = { updateViewModel.dismiss() },
                            onRetry = { updateViewModel.check(force = true) },
                        )
                    }
                }
            }
        }
    }

    /**
     * The manifest is asked for when the app comes forward.
     *
     * The ask is throttled inside the updater, so coming back to the app ten
     * times in a day is one fetch, not ten.
     */
    override fun onResume() {
        super.onResume()
        if (!forwarded) updateViewModel.check()
    }

    /**
     * The app came to the front.
     *
     * A feed that was stopped because the app was put away starts again here,
     * so a viewer who comes back to the phone finds the channel where it was.
     */
    override fun onStart() {
        super.onStart()
        if (!forwarded) playbackViewModel.exitBackground()
    }

    /**
     * The app was put away.
     *
     * A phone that is locked, or an app that is minimised, must not keep
     * playing a sound into the background — so the feed is stopped the moment
     * the screen is no longer on top, and the picture is only held, not
     * given up.
     */
    override fun onStop() {
        if (!forwarded) playbackViewModel.enterBackground()
        super.onStop()
    }

    /** The screen where the owner lets this app install its own updates. */
    private fun allowInstall() {
        startActivity(ApkInstaller(applicationContext).permissionIntent())
    }

    /**
     * Whether this device is a television.
     *
     * Three answers count as yes: the device says it is in television mode,
     * it carries the leanback feature, or it has no touchscreen at all — a
     * box that never declared itself a TV still cannot be a handset.
     */
    private fun isTelevision(): Boolean {
        val uiMode = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        return uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
            !packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
    }
}
