package com.soluplay.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soluplay.app.ui.SoluPlayTheme

/**
 * The TV entry. It ships inside the same APK as the phone app, so a box that
 * installs one file gets both launchers.
 */
class TvActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoluPlayTheme {
                TvHomeScreen()
            }
        }
    }
}
