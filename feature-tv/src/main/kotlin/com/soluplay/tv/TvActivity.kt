package com.soluplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.soluplay.app.AppGraph
import com.soluplay.app.ui.SoluPlayTheme
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.BrowseViewModelFactory

/**
 * The TV entry. It ships inside the same APK as the phone app, so a box that
 * installs one file gets both launchers.
 */
class TvActivity : ComponentActivity() {

    private val browseViewModel: BrowseViewModel by viewModels {
        BrowseViewModelFactory(AppGraph.repository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoluPlayTheme {
                TvHomeScreen(viewModel = browseViewModel)
            }
        }
    }
}
