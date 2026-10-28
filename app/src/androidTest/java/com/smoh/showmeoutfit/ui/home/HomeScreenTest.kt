package com.smoh.showmeoutfit.ui.home

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Duration
import java.util.Locale

/**
 * State coverage for the stateless dashboard.
 *
 * Assertions use resources (never literal English) so the suite keeps passing when copy changes,
 * and they run against the real Material 3 rendering on a device.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(@StringRes id: Int, vararg args: String): String = context.getString(id, *args)

    private fun setState(
        state: HomeUiState,
        onIntent: (HomeIntent) -> Unit = {},
        onEffect: (HomeEffect) -> Unit = {}
    ) {
        composeRule.setContent {
            ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                HomeScreen(state = state, onIntent = onIntent, onEffect = onEffect)
            }
        }
    }

    private fun garmentName(garment: Garment): String =
        context.getString(GarmentTextMapper.garmentNameRes(garment.id, garment.fit))

    private fun expectedText(mapped: MappedText): String =
        context.getString(mapped.resId, *mapped.resolve(Locale.getDefault()))

    private fun accessoryLabel(alert: AccessoryAlert): String =
        context.getString(GarmentTextMapper.accessoryNameRes(alert.type))

    // --- Content ---------------------------------------------------------------------------

    @Test
    fun content_renders_headline_summary_and_location() {
        val outfit = previewTodayOutfit()
        setState(HomeUiState.Content(outfit))

        composeRule.onNodeWithText(outfit.weather.snapshot.location.cityName).assertIsDisplayed()
        composeRule.onNodeWithText(
            expectedText(GarmentTextMapper.headline(outfit.recommendation.headline))
        ).assertIsDisplayed()
        composeRule.onNodeWithText(
            expectedText(GarmentTextMapper.summary(outfit.recommendation.summary))
        ).assertExists()
    }

    @Test
    fun content_renders_all_five_layer_labels() {
        val outfit = previewTodayOutfit()
        setState(HomeUiState.Content(outfit))

        listOf(
            R.string.home_layer_base,
            R.string.home_layer_mid,
            R.string.home_layer_outer,
            R.string.home_layer_bottom,
            R.string.home_layer_footwear
        ).forEach { labelRes ->
            composeRule.onNodeWithText(text(labelRes))
                .performScrollTo()
                .assertIsDisplayed()
        }

        // Every recommended garment resolves to real copy inside its card.
        listOfNotNull(
            outfit.recommendation.baseLayer,
            outfit.recommendation.midLayer,
            outfit.recommendation.outerLayer,
            outfit.recommendation.bottomLayer,
            outfit.recommendation.footwear
        ).forEach { garment ->
            composeRule.onNodeWithText(garmentName(garment)).assertExists()
        }
    }

    @Test
    fun content_renders_a_not_needed_card_instead_of_a_gap_for_absent_layers() {
        // The sample has no outer layer.
        val outfit = previewTodayOutfit()
        assertEquals(null, outfit.recommendation.outerLayer)

        setState(HomeUiState.Content(outfit))

        composeRule.onNodeWithText(text(R.string.home_layer_not_needed))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_layer_outer_not_needed_description))
            .assertExists()
    }

    @Test
    fun content_renders_accessory_badges_with_their_reasons() {
        val outfit = previewTodayOutfit()
        setState(HomeUiState.Content(outfit))

        outfit.recommendation.accessories.forEach { alert ->
            composeRule.onNodeWithContentDescription(accessoryLabel(alert), substring = true)
                .performScrollTo()
                .assertExists()
        }

        // Each badge is followed by the structural reason the engine produced.
        val reason = outfit.recommendation.accessories.first().reason
        composeRule.onNodeWithText(expectedText(GarmentTextMapper.accessoryReason(reason)))
            .assertExists()
    }

    @Test
    fun content_renders_timeline_and_metric_chips() {
        setState(HomeUiState.Content(previewTodayOutfit()))

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE).assertExists()

        // WeatherMetricChip exposes "<label>: <value>" as one content description; the visible
        // label is cleared from the merged tree by the component's clearAndSetSemantics.
        listOf(
            R.string.home_metric_rain_label,
            R.string.home_metric_uv_label,
            R.string.home_metric_wind_label
        ).forEach { labelRes ->
            composeRule.onNodeWithContentDescription("${text(labelRes)}: ", substring = true)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun network_content_shows_no_cache_banner() {
        setState(HomeUiState.Content(previewTodayOutfit(origin = WeatherOrigin.NETWORK)))

        composeRule.onNodeWithTag(HomeTestTags.CACHE_BANNER).assertDoesNotExist()
    }

    // --- Cached provenance -----------------------------------------------------------------

    @Test
    fun stale_cache_shows_the_offline_banner_with_the_age() {
        setState(
            HomeUiState.Content(
                previewTodayOutfit(
                    origin = WeatherOrigin.CACHE_STALE,
                    cacheAge = Duration.ofHours(5).plusMinutes(20)
                )
            )
        )

        composeRule.onNodeWithTag(HomeTestTags.CACHE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("5 hours 20 minutes", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.home_banner_cache_stale, "5 hours 20 minutes")
        ).assertIsDisplayed()
    }

    @Test
    fun fresh_cache_reads_differently_from_stale_cache() {
        setState(
            HomeUiState.Content(
                previewTodayOutfit(
                    origin = WeatherOrigin.CACHE_FRESH,
                    cacheAge = Duration.ofMinutes(48)
                )
            )
        )

        composeRule.onNodeWithTag(HomeTestTags.CACHE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithText("48 minutes", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(
            context.getString(R.string.home_banner_cache_fresh, "48 minutes")
        ).assertIsDisplayed()
    }

    // --- Loading ---------------------------------------------------------------------------

    @Test
    fun loading_exposes_a_spoken_placeholder_and_no_fake_content() {
        setState(HomeUiState.Loading)

        composeRule.onNodeWithContentDescription(
            context.getString(R.string.home_loading_content_description)
        ).assertIsDisplayed()
        // The skeleton must not announce the real section headings as if data had loaded.
        composeRule.onNodeWithText(text(R.string.home_section_outfit)).assertDoesNotExist()
    }

    // --- Empty -----------------------------------------------------------------------------

    @Test
    fun empty_explains_the_missing_location_and_opens_the_search() {
        var effect: HomeEffect? = null
        setState(HomeUiState.Empty, onEffect = { effect = it })

        composeRule.onNodeWithText(text(R.string.home_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_empty_body)).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.home_empty_action))
            .assertIsDisplayed()
            .performClick()

        assertEquals(HomeEffect.OpenLocationSearch, effect)
    }

    // --- Error -----------------------------------------------------------------------------

    @Test
    fun error_shows_localized_copy_and_retries() {
        var intent: HomeIntent? = null
        setState(HomeUiState.Error(DomainError.Offline()), onIntent = { intent = it })

        composeRule.onNodeWithText(text(R.string.home_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_error_offline)).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.home_error_retry))
            .assertIsDisplayed()
            .performClick()

        assertEquals(HomeIntent.DismissError, intent)
    }

    @Test
    fun error_never_renders_the_raw_exception_message() {
        setState(
            HomeUiState.Error(
                DomainError.Unknown(IllegalStateException("db://secret-host:5432 boom"))
            )
        )

        composeRule.onNodeWithText("db://secret-host:5432", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.home_error_unknown)).assertIsDisplayed()
    }

    @Test
    fun every_domain_error_subtype_has_copy_on_screen() {
        val errors = listOf(
            DomainError.NoLocation(),
            DomainError.Offline(),
            DomainError.ApiFailure(),
            DomainError.MalformedData(),
            DomainError.CacheExpired(),
            DomainError.Unknown()
        )

        errors.forEach { error ->
            val message = text(HomeMessageMapper.errorMessageRes(error))
            assertTrue("empty copy for $error", message.isNotBlank())
        }
    }
}
