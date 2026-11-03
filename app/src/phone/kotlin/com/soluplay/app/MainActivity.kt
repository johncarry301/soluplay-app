package com.soluplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.soluplay.app.ui.SoluPlayTheme
import com.soluplay.core.browse.BrowseViewModel
import com.soluplay.core.browse.BrowseViewModelFactory
import com.soluplay.phone.PhoneHomeScreen

/**
 * The handset entry. It lives in the phone flavour because the TV build has
 * no phone surface to open.
 */
class MainActivity : ComponentActivity() {

    private val browseViewModel: BrowseViewModel by viewModels {
        BrowseViewModelFactory(AppGraph.repository(applicationContext))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoluPlayTheme {
                PhoneHomeScreen(viewModel = browseViewModel)
            }
        }
    }
}
