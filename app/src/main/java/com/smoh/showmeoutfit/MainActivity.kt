package com.smoh.showmeoutfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.smoh.showmeoutfit.ui.ShowMeOutfitApp
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme

class MainActivity : ComponentActivity() {
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
