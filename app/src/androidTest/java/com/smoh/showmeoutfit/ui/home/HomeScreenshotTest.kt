package com.smoh.showmeoutfit.ui.home

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * QA artifact: renders the Content state and writes a PNG to the app's external files directory so
 * the sprint evidence can be pulled with
 * `adb pull /sdcard/Android/data/com.smoh.showmeoutfit/files/qa_s2-03_home.png`.
 *
 * It doubles as a rendering smoke test: a crash, a zero-sized layout or an unreadable screenshot
 * fails the build.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenshotTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun content_state_renders_and_screenshots() {
        composeRule.setContent {
            ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                HomeScreen(
                    state = HomeUiState.Content(outfit = previewTodayOutfit()),
                    onIntent = {},
                    onEffect = {}
                )
            }
        }
        composeRule.waitForIdle()

        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        assertTrue("captured bitmap is empty", bitmap.width > 0 && bitmap.height > 0)

        val directory = InstrumentationRegistry.getInstrumentation()
            .targetContext
            .getExternalFilesDir(null)
        assertTrue("no external files directory", directory != null)

        val file = File(directory, SCREENSHOT_NAME)
        file.outputStream().use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        assertTrue("screenshot was not written", file.length() > 0L)
    }

    private companion object {
        const val SCREENSHOT_NAME = "qa_s2-03_home.png"
    }
}
