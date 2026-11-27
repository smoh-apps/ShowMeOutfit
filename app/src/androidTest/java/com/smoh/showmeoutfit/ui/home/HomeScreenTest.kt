package com.smoh.showmeoutfit.ui.home

import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.ui.theme.DesignTokens
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
        onEffect: (HomeEffect) -> Unit = {},
        onOpenWardrobe: () -> Unit = {}
    ) {
        composeRule.setContent {
            ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                HomeScreen(
                    state = state,
                    onIntent = onIntent,
                    onEffect = onEffect,
                    onOpenWardrobe = onOpenWardrobe
                )
            }
        }
    }

    private fun garmentName(garment: Garment): String =
        context.getString(GarmentTextMapper.garmentNameRes(garment.id, garment.fit))

    private fun expectedText(mapped: MappedText): String =
        context.getString(mapped.resId, *mapped.resolve(Locale.getDefault()))

    private fun accessoryLabel(alert: AccessoryAlert): String =
        context.getString(GarmentTextMapper.accessoryNameRes(alert.type))

    private fun peakDescriptions(outfit: TodayOutfit): List<String> {
        val highlights = outfit.recommendation.weatherHighlights
        val locale = Locale.getDefault()
        val rain = TextArg.Count(highlights.maxPrecipitationProb).format(locale)
        val uv = TextArg.Number(highlights.maxUvIndex, decimals = 0).format(locale)
        val wind = TextArg.Number(highlights.maxWindSpeedKmh, decimals = 0).format(locale)
        return listOf(
            text(R.string.home_capsule_rain_peak, rain),
            text(R.string.home_capsule_uv_peak, uv),
            text(R.string.home_capsule_wind_peak, wind)
        )
    }

    // --- Content ---------------------------------------------------------------------------

    @Test
    fun content_renders_headline_location_and_capsule_sky_description() {
        val outfit = previewTodayOutfit()
        setState(HomeUiState.Content(outfit))

        composeRule.onNodeWithText(outfit.weather.snapshot.location.cityName).assertIsDisplayed()
        composeRule.onNodeWithText(
            expectedText(GarmentTextMapper.headline(outfit.recommendation.headline))
        ).assertIsDisplayed()
        composeRule.onNodeWithContentDescription(
            context.getString(
                GarmentTextMapper.conditionDescriptionRes(outfit.weather.snapshot.current.weatherCode)
            )
        ).assertExists()
    }

    @Test
    fun hanger_action_is_localized_meets_touch_target_and_opens_wardrobe() {
        var opened = false
        setState(HomeUiState.Content(previewTodayOutfit()), onOpenWardrobe = { opened = true })

        composeRule.onNodeWithTag(HomeTestTags.OPEN_WARDROBE)
            .assertIsDisplayed()
            .assertHeightIsAtLeast(DesignTokens.MinTouchTarget)
            .assertWidthIsAtLeast(DesignTokens.MinTouchTarget)
        composeRule.onNodeWithContentDescription(text(R.string.home_open_wardrobe))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.OPEN_WARDROBE).performClick()

        assertTrue(opened)
    }

    @Test
    fun capsule_city_and_refresh_actions_dispatch_home_intents() {
        val outfit = previewTodayOutfit()
        var intent: HomeIntent? = null
        setState(HomeUiState.Content(outfit), onIntent = { intent = it })

        composeRule.onNodeWithContentDescription(
            text(R.string.home_capsule_change_location, outfit.weather.snapshot.location.cityName)
        ).performScrollTo().performClick()
        assertEquals(HomeIntent.OpenLocationSearch, intent)

        composeRule.onNodeWithContentDescription(text(R.string.home_capsule_refresh))
            .performScrollTo()
            .performClick()
        assertEquals(HomeIntent.Refresh, intent)
    }

    @Test
    fun lookbook_badge_is_separate_text_inside_the_merged_garment_chip() {
        val outfit = previewTodayOutfit()
        val base = outfit.recommendation.baseLayer
        val name = garmentName(base)
        val indicator = text(R.string.home_in_closet_indicator)
        setState(
            HomeUiState.Content(
                outfit = outfit,
                closetItemKeys = setOf(base.category to base.id)
            )
        )

        composeRule.onNode(hasText(name) and hasText(indicator))
            .performScrollTo()
            .assertIsDisplayed()
        // The indicator must not be concatenated into the garment's own title node.
        composeRule.onAllNodesWithText(name).assertCountEquals(1)
    }

    @Test
    fun grid_badge_requires_the_exact_category_for_the_same_garment_id() {
        val outfit = previewTodayOutfit()
        val base = outfit.recommendation.baseLayer
        val wrongCategory = if (base.category == LayerCategory.MID) {
            LayerCategory.BASE
        } else {
            LayerCategory.MID
        }
        setState(
            HomeUiState.Content(
                outfit = outfit,
                closetItemKeys = setOf(wrongCategory to base.id)
            )
        )
        composeRule.onNodeWithText(text(R.string.home_outfit_view_grid))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag(HomeTestTags.outfitTile("base"))
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText(text(R.string.home_in_closet_indicator))
            .assertCountEquals(0)
    }

    @Test
    fun grid_badge_matches_the_catalog_identity_independently_of_fit() {
        val outfit = previewTodayOutfit()
        val base = outfit.recommendation.baseLayer.copy(fit = GarmentFit.WOMEN)
        val womenOutfit = outfit.copy(
            recommendation = outfit.recommendation.copy(baseLayer = base)
        )
        val name = garmentName(base)
        val indicator = text(R.string.home_in_closet_indicator)
        setState(
            HomeUiState.Content(
                outfit = womenOutfit,
                closetItemKeys = setOf(base.category to base.id)
            )
        )
        composeRule.onNodeWithText(text(R.string.home_outfit_view_grid))
            .performScrollTo()
            .performClick()

        composeRule.onNode(
            hasTestTag(HomeTestTags.outfitTile("base")) and hasText(name) and hasText(indicator)
        ).performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText(name).assertCountEquals(1)
    }

    @Test
    fun closet_indicators_appear_only_on_owned_garments_not_accessories() {
        val outfit = previewTodayOutfit()
        val garments = listOfNotNull(
            outfit.recommendation.baseLayer,
            outfit.recommendation.midLayer,
            outfit.recommendation.outerLayer,
            outfit.recommendation.bottomLayer,
            outfit.recommendation.footwear
        )
        setState(
            HomeUiState.Content(
                outfit = outfit,
                closetItemKeys = garments.map { it.category to it.id }.toSet()
            )
        )

        composeRule.onAllNodesWithText(text(R.string.home_in_closet_indicator))
            .assertCountEquals(garments.size)
    }

    @Test
    fun content_renders_only_the_active_layer_tiles() {
        // The sample has a base, mid, bottom and footwear layer but no outer layer.
        val outfit = previewTodayOutfit()
        assertEquals(null, outfit.recommendation.outerLayer)

        setState(HomeUiState.Content(outfit))
        composeRule.onNodeWithText(text(R.string.home_outfit_view_grid))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_GRID).assertExists()

        listOf(
            "base" to R.string.home_layer_base,
            "mid" to R.string.home_layer_mid,
            "bottom" to R.string.home_layer_bottom,
            "footwear" to R.string.home_layer_footwear
        ).forEach { (layerKey, labelRes) ->
            composeRule.onNodeWithTag(HomeTestTags.outfitTile(layerKey))
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText(text(labelRes)).assertExists()
        }

        // The absent layer gets no tile and no label at all.
        composeRule.onNodeWithTag(HomeTestTags.outfitTile("outer")).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.home_layer_outer)).assertDoesNotExist()

        // Every recommended garment resolves to real copy inside its tile.
        listOfNotNull(
            outfit.recommendation.baseLayer,
            outfit.recommendation.midLayer,
            outfit.recommendation.bottomLayer,
            outfit.recommendation.footwear
        ).forEach { garment ->
            composeRule.onNodeWithText(garmentName(garment)).assertExists()
        }
    }

    @Test
    fun no_not_needed_placeholder_is_rendered_for_an_absent_layer() {
        val outfit = previewTodayOutfit()
        assertEquals(null, outfit.recommendation.outerLayer)

        setState(HomeUiState.Content(outfit))

        // The lookbook only shows layers that exist: no filler hotspot or chip for the missing
        // layer (the old "not needed" strings went away with the bento grid rework).
        composeRule.onNodeWithTag(LookbookTestTags.garment("outer")).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.home_layer_outer)).assertDoesNotExist()
    }

    @Test
    fun tapping_a_tile_opens_the_detail_sheet_with_the_full_description() {
        val outfit = previewTodayOutfit()
        val base = requireNotNull(outfit.recommendation.baseLayer)
        setState(HomeUiState.Content(outfit))
        composeRule.onNodeWithText(text(R.string.home_outfit_view_grid))
            .performScrollTo()
            .performClick()

        composeRule.onNodeWithTag(HomeTestTags.outfitTile("base"))
            .performScrollTo()
            .performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(HomeTestTags.GARMENT_DETAIL_SHEET).assertExists()
        // The description is not in the tile, so finding it proves the sheet expanded the garment.
        composeRule.onNodeWithText(
            context.getString(GarmentTextMapper.garmentDescriptionRes(base.id, base.fit))
        ).assertExists()
    }

    @Test
    fun outfit_grid_keeps_every_active_tile_and_title_at_two_hundred_percent_font() {
        val outfit = previewTodayOutfit()

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                    HomeScreen(state = HomeUiState.Content(outfit), onIntent = {}, onEffect = {})
                }
            }
        }

        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_GRID).assertExists()

        listOf(
            "base" to outfit.recommendation.baseLayer,
            "mid" to outfit.recommendation.midLayer,
            "bottom" to outfit.recommendation.bottomLayer,
            "footwear" to outfit.recommendation.footwear
        ).forEach { (layerKey, garment) ->
            composeRule.onNodeWithTag(HomeTestTags.outfitTile(layerKey))
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithText(garmentName(requireNotNull(garment)))
                .assertIsDisplayed()
        }

        composeRule.onNodeWithTag(HomeTestTags.outfitTile("outer")).assertDoesNotExist()
    }

    @Test
    fun content_renders_compact_accessory_chips_with_their_triggers() {
        val outfit = previewTodayOutfit()
        setState(HomeUiState.Content(outfit))

        outfit.recommendation.accessories.forEach { alert ->
            val trigger = GarmentTextMapper.accessoryCompactTrigger(alert.reason)
            val triggerText = context.getString(trigger.resId, *trigger.resolve(Locale.getDefault()))
            val name = accessoryLabel(alert)
            val label = context.getString(
                R.string.home_accessory_trigger_format,
                name,
                triggerText
            )
            val description = context.getString(
                R.string.home_accessory_trigger_content_description,
                name,
                triggerText
            )
            composeRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithContentDescription(description).assertExists()
        }
    }

    @Test
    fun content_renders_timeline_and_peak_chips() {
        val outfit = previewTodayOutfit()
        setState(HomeUiState.Content(outfit))

        // The peaks are always on screen; the chart needs the capsule opened first.
        composeRule.onNodeWithTag(HomeTestTags.METRICS).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.TIMELINE).assertDoesNotExist()

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE_TOGGLE).performScrollTo().performClick()

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE).assertExists()

        // Each PeakChip exposes one complete spoken sentence and hides its bare number.
        peakDescriptions(outfit).forEach { description ->
            composeRule.onNodeWithContentDescription(description)
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun the_hourly_timeline_is_hidden_until_the_capsule_is_opened() {
        setState(HomeUiState.Content(previewTodayOutfit()))

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE).assertDoesNotExist()

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE_TOGGLE).performScrollTo().performClick()

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE).assertExists()
    }

    @Test
    fun tapping_the_timeline_toggle_twice_folds_the_chart_away_again() {
        setState(HomeUiState.Content(previewTodayOutfit()))

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(HomeTestTags.TIMELINE).assertExists()

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE_TOGGLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(HomeTestTags.TIMELINE).assertDoesNotExist()
    }

    @Test
    fun the_peak_chips_are_readable_without_opening_the_timeline() {
        val outfit = previewTodayOutfit()
        setState(HomeUiState.Content(outfit))

        composeRule.onNodeWithTag(HomeTestTags.METRICS).performScrollTo().assertIsDisplayed()

        peakDescriptions(outfit).forEach { description ->
            composeRule.onNodeWithContentDescription(description).assertIsDisplayed()
        }
    }

    @Test
    fun the_timeline_toggle_announces_whether_the_chart_is_open() {
        setState(HomeUiState.Content(previewTodayOutfit()))

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE_TOGGLE)
            .performScrollTo()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    text(R.string.home_weather_state_collapsed)
                )
            )
        composeRule.onNodeWithContentDescription(text(R.string.home_capsule_timeline_show))
            .assertIsDisplayed()

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE_TOGGLE).performScrollTo().performClick()

        composeRule.onNodeWithTag(HomeTestTags.TIMELINE_TOGGLE)
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    text(R.string.home_weather_state_expanded)
                )
            )
        composeRule.onNodeWithContentDescription(text(R.string.home_capsule_timeline_hide))
            .assertExists()
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
        var intent: HomeIntent? = null
        setState(HomeUiState.Empty, onIntent = { intent = it })

        composeRule.onNodeWithText(text(R.string.home_empty_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_empty_body)).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.home_empty_action))
            .assertIsDisplayed()
            .performClick()

        assertEquals(HomeIntent.OpenLocationSearch, intent)
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

        // A plain reload would read the same unusable cache row again.
        assertEquals(HomeIntent.Refresh, intent)
    }

    @Test
    fun error_offers_the_city_picker_as_a_way_out_when_retry_keeps_failing() {
        var intent: HomeIntent? = null
        setState(
            HomeUiState.Error(DomainError.CacheExpired()),
            onIntent = { intent = it }
        )

        composeRule.onNodeWithText(text(R.string.home_error_cache_expired)).assertIsDisplayed()

        composeRule.onNodeWithText(text(R.string.home_error_change_city))
            .assertIsDisplayed()
            .performClick()

        assertEquals(HomeIntent.OpenLocationSearch, intent)
    }

    @Test
    fun error_state_stays_usable_at_two_hundred_percent_font() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                    HomeScreen(
                        state = HomeUiState.Error(DomainError.LocationPermissionDenied()),
                        onIntent = {},
                        onEffect = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText(text(R.string.home_error_title)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_error_location_permission)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_error_retry)).assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_error_change_city)).assertIsDisplayed()
    }

    @Test
    fun an_unrefreshed_dashboard_says_so_and_offers_the_retry() {
        var intent: HomeIntent? = null
        val outfit = previewTodayOutfit(origin = WeatherOrigin.NETWORK)
        setState(
            HomeUiState.Content(outfit = outfit, refreshFailed = true),
            onIntent = { intent = it }
        )

        // NETWORK provenance alone would show no banner, so this is the only hint that the data is
        // older than the screen suggests.
        composeRule.onNodeWithText(text(R.string.home_banner_refresh_failed)).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.CACHE_BANNER).assertDoesNotExist()

        composeRule.onNodeWithText(text(R.string.home_error_retry))
            .assertIsDisplayed()
            .performClick()

        assertEquals(HomeIntent.Refresh, intent)
    }

    @Test
    fun an_unrefreshed_dashboard_is_announced_to_talkback() {
        val outfit = previewTodayOutfit(origin = WeatherOrigin.NETWORK)
        setState(HomeUiState.Content(outfit = outfit, refreshFailed = true))

        composeRule.onNodeWithTag(HomeTestTags.REFRESH_FAILED_BANNER)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun a_cached_dashboard_does_not_stack_two_banners_when_the_refresh_fails() {
        val outfit = previewTodayOutfit(origin = WeatherOrigin.CACHE_STALE, cacheAge = Duration.ofHours(4))
        setState(HomeUiState.Content(outfit = outfit, refreshFailed = true))

        // Provenance already says the data is old; a second banner would repeat it.
        composeRule.onNodeWithTag(HomeTestTags.CACHE_BANNER).assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.REFRESH_FAILED_BANNER).assertDoesNotExist()
    }

    @Test
    fun cache_banner_is_a_live_region_and_survives_two_hundred_percent_font() {
        val outfit = previewTodayOutfit(
            origin = WeatherOrigin.CACHE_STALE,
            cacheAge = Duration.ofHours(5).plusMinutes(20)
        )

        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                    HomeScreen(state = HomeUiState.Content(outfit), onIntent = {}, onEffect = {})
                }
            }
        }

        // TalkBack announces a provenance change without moving focus.
        composeRule.onNodeWithTag(HomeTestTags.CACHE_BANNER)
            .assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))

        composeRule.onNodeWithText("5 hours 20 minutes", substring = true).assertIsDisplayed()
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
