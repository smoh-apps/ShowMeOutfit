package com.smoh.showmeoutfit.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GarmentArtworkTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun preset_artwork_renders_with_its_content_description() {
        composeRule.setContent {
            ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                GarmentArtwork(
                    source = GarmentVisualSource.Preset(R.drawable.cutout_cardigan_women),
                    contentDescription = "Women's cardigan",
                    modifier = Modifier.size(104.dp)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Women's cardigan").assertIsDisplayed()
    }

    @Test
    fun missing_user_photo_renders_the_preset_fallback() {
        composeRule.setContent {
            ShowMeOutfitTheme(darkTheme = true, dynamicColor = false) {
                GarmentArtwork(
                    source = GarmentVisualSource.UserClosetPhoto(
                        filePath = "/missing/wardrobe-photo.webp",
                        fallbackRes = R.drawable.cutout_cotton_tee
                    ),
                    contentDescription = "Closet photo fallback",
                    modifier = Modifier.size(104.dp)
                )
            }
        }

        composeRule.onNodeWithContentDescription("Closet photo fallback").assertIsDisplayed()
    }
}
