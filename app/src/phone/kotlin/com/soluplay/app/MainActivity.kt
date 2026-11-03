package com.soluplay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.soluplay.app.ui.SoluPlayTheme
import com.soluplay.phone.PhoneHomeScreen

/**
 * The handset entry. It lives in the phone flavour because the TV build has
 * no phone surface to open.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SoluPlayTheme {
                PhoneHomeScreen()
            }
        }
    }
}
