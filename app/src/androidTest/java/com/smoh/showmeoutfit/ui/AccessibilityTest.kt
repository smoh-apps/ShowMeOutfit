package com.smoh.showmeoutfit.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.ui.home.GarmentTextMapper
import com.smoh.showmeoutfit.ui.home.HomeScreen
import com.smoh.showmeoutfit.ui.home.HomeTestTags
import com.smoh.showmeoutfit.ui.home.HomeTimelineMapper
import com.smoh.showmeoutfit.ui.home.HomeUiState
import com.smoh.showmeoutfit.ui.home.previewTodayOutfit
import com.smoh.showmeoutfit.ui.settings.SettingsScreen
import com.smoh.showmeoutfit.ui.settings.SettingsTestTags
import com.smoh.showmeoutfit.ui.settings.SettingsUiState
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * TalkBack and font-scale gate for SMO-S4-04.
 *
 * The assertions are behavioural rather than screenshot-based: one coherent semantics node per
 * composite component, no fragmented chart ticks, and every control still displayed (not clipped)
 * when the user doubles the system font size.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    // --- TalkBack semantics -----------------------------------------------------------------

    @Test
    fun timeline_reads_as_one_summary_and_does_not_announce_its_tick_labels() {
        val outfit = previewTodayOutfit()
        showHome(HomeUiState.Content(outfit))
        // The chart lives in the collapsed drawer, so the drawer has to be opened first.
        expandTimeline()

        composeRule.onNodeWithContentDescription(expectedTimelineDescription(outfit)).assertExists()

        // The hour ticks and the legend are visual aids; exposing them would duplicate the
        // summary and make TalkBack read the same data four times.
        composeRule.onNodeWithText("07:00").assertDoesNotExist()
        composeRule.onNodeWithText(context.getString(R.string.component_timeline_legend_temperature))
            .assertDoesNotExist()
    }

    @Test
    fun outfit_layer_card_exposes_its_label_and_garment_as_one_node() {
        val outfit = previewTodayOutfit()
        showHome(HomeUiState.Content(outfit))
        val base = requireNotNull(outfit.recommendation.baseLayer)

        val layerLabel = context.getString(R.string.home_layer_base)
        val garmentName = context.getString(GarmentTextMapper.garmentNameRes(base.id, base.fit))

        // A merged node carries both texts, so TalkBack reads "<layer>, <garment>, <reason>"
        // instead of stopping on three separate fragments.
        composeRule.onNode(hasText(layerLabel) and hasText(garmentName))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun metric_chips_keep_one_description_per_chip() {
        val outfit = previewTodayOutfit()
        showHome(HomeUiState.Content(outfit))

        listOf(
            R.string.home_metric_rain_label,
            R.string.home_metric_uv_label,
            R.string.home_metric_wind_label
        ).forEach { labelRes ->
            composeRule.onNodeWithContentDescription(
                "${context.getString(labelRes)}: ",
                substring = true
            ).performScrollTo().assertIsDisplayed()
        }
    }

    // --- 200% font scale --------------------------------------------------------------------

    @Test
    fun metric_chips_reflow_instead_of_clipping_at_two_hundred_percent_font() {
        val outfit = previewTodayOutfit()
        showHome(HomeUiState.Content(outfit), fontScale = 2f)

        composeRule.onNodeWithTag(HomeTestTags.METRICS).assertExists()

        // Full-width stacked chips: every label/value pair is fully on screen.
        listOf(
            R.string.home_metric_rain_label,
            R.string.home_metric_uv_label,
            R.string.home_metric_wind_label
        ).forEach { labelRes ->
            composeRule.onNodeWithContentDescription(
                "${context.getString(labelRes)}: ",
                substring = true
            ).performScrollTo().assertIsDisplayed()
        }
    }

    @Test
    fun sensitivity_control_stacks_and_stays_selectable_at_two_hundred_percent_font() {
        showSettings(fontScale = 2f, profile = profileWithSensitivity())

        composeRule.onNodeWithTag(SettingsTestTags.SENSITIVITY_COLD)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SENSITIVITY_NEUTRAL)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.SENSITIVITY_WARM)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun sensitivity_control_keeps_its_segmented_layout_at_the_default_font() {
        showSettings(fontScale = 1f, profile = profileWithSensitivity())

        composeRule.onNodeWithTag(SettingsTestTags.SENSITIVITY_COLD)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTestTags.SENSITIVITY_NEUTRAL)
            .performScrollTo()
            .assertIsDisplayed()
            .assertIsSelected()
        composeRule.onNodeWithTag(SettingsTestTags.SENSITIVITY_WARM)
            .performScrollTo()
            .assertIsDisplayed()
    }

    // --- Helpers ----------------------------------------------------------------------------

    /** Opens the collapsible weather drawer; the chart is folded away by default. */
    private fun expandTimeline() {
        composeRule.onNodeWithTag(HomeTestTags.TIMELINE_TOGGLE)
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()
    }

    private fun showHome(state: HomeUiState, fontScale: Float = 1f) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                    HomeScreen(state = state, onIntent = {}, onEffect = {})
                }
            }
        }
    }

    private fun showSettings(fontScale: Float, profile: UserProfile) {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = density.density, fontScale = fontScale)
            ) {
                ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                    SettingsScreen(
                        state = SettingsUiState(profile = profile, isLoading = false),
                        onIntent = {},
                        onBack = {}
                    )
                }
            }
        }
    }

    private fun profileWithSensitivity() = UserProfile(
        isConfigured = true,
        isNotificationEnabled = false,
        morningNotificationTime = LocalTime.of(7, 0)
    )

    private fun expectedTimelineDescription(outfit: TodayOutfit): String {
        val points = HomeTimelineMapper.points(outfit.weather.snapshot.hourlyForecast) { time ->
            time.format(HOUR_FORMAT)
        }
        return context.getString(
            R.string.component_timeline_content_description,
            points.first().hourLabel,
            points.last().hourLabel,
            points.minOf { it.temperatureC },
            points.maxOf { it.temperatureC },
            points.maxOf { it.precipitationProbability },
            points.maxOf { it.uvIndex }
        )
    }

    private companion object {
        val HOUR_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
