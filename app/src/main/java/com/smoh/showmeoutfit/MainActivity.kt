package com.smoh.showmeoutfit

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smoh.showmeoutfit.ui.ShowMeOutfitApp
import com.smoh.showmeoutfit.ui.settings.AndroidAppLocaleController
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme

class MainActivity : ComponentActivity() {

    /**
     * Applies the in-app language choice on Android 8-12, where there is no per-app locale API.
     * On Android 13+ the platform resolves the application locales itself and this is a no-op.
     */
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AndroidAppLocaleController(newBase).wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShowMeOutfitTheme {
                ShowMeOutfitApp()
            }
        }
    }
}
