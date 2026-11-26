package com.smoh.showmeoutfit.ui.home

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.filter
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.engine.GarmentCatalog
import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.domain.model.AccessoryReason
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.ui.theme.DesignTokens
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalTime
import java.util.Locale

/** Lookbook, switcher, compact-trigger and accessibility contracts from ADR-DESIGN-V2 D4. */
@RunWith(AndroidJUnit4::class)
class OutfitLookbookTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun text(id: Int): String = context.getString(id)

    private fun accessoryLabel(alert: AccessoryAlert): String {
        val trigger = GarmentTextMapper.accessoryCompactTrigger(alert.reason)
        val triggerText = context.getString(trigger.resId, *trigger.resolve(Locale.getDefault()))
        return context.getString(
            R.string.home_accessory_trigger_format,
            context.getString(GarmentTextMapper.accessoryNameRes(alert.type)),
            triggerText
        )
    }

    private fun setHome(
        outfit: TodayOutfit = previewTodayOutfit(),
        onAccessoryClick: (AccessoryAlert) -> Unit = {}
    ) {
        composeRule.setContent {
            ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                HomeScreen(
                    state = HomeUiState.Content(outfit),
                    onIntent = {},
                    onEffect = {},
                    onAccessoryClick = onAccessoryClick
                )
            }
        }
    }

    private fun setCanvas(
        recommendation: OutfitRecommendation = previewTodayOutfit().recommendation,
        rtl: Boolean = false,
        closetItemKeys: Set<Pair<LayerCategory, GarmentId>> = emptySet(),
        onGarmentClick: (OutfitTileUi) -> Unit = {},
        onAccessoryClick: (AccessoryAlert) -> Unit = {}
    ) {
        composeRule.setContent {
            ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                CompositionLocalProvider(
                    LocalLayoutDirection provides
                        if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
                ) {
                    ColumnWithPadding {
                        OutfitLookbookCanvas(
                            recommendation = recommendation,
                            onGarmentClick = onGarmentClick,
                            onAccessoryClick = onAccessoryClick
                        )
                        OutfitLookbookChips(
                            recommendation = recommendation,
                            closetItemKeys = closetItemKeys,
                            onGarmentClick = onGarmentClick,
                            onAccessoryClick = onAccessoryClick
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun ColumnWithPadding(content: @Composable () -> Unit) {
        Column(
            modifier = Modifier.padding(DesignTokens.Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(DesignTokens.Spacing.md)
        ) {
            content()
        }
    }

    @Test
    fun home_defaults_to_look_and_exposes_the_material_switcher() {
        setHome()

        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_VIEW_SWITCHER)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithText(text(R.string.home_outfit_view_look)).assertIsSelected()
        composeRule.onNodeWithText(text(R.string.home_outfit_view_grid)).assertIsNotSelected()
        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_LOOKBOOK)
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_GRID).assertDoesNotExist()
    }

    @Test
    fun switcher_toggles_between_lookbook_and_grid() {
        setHome()

        composeRule.onNodeWithText(text(R.string.home_outfit_view_grid))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_GRID).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_LOOKBOOK).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.home_outfit_view_grid)).assertIsSelected()

        composeRule.onNodeWithText(text(R.string.home_outfit_view_look))
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_LOOKBOOK).assertExists()
        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_GRID).assertDoesNotExist()
        composeRule.onNodeWithText(text(R.string.home_outfit_view_look)).assertIsSelected()
    }

    @Test
    fun lookbook_garment_chip_opens_the_same_detail_sheet_as_a_grid_tile() {
        val outfit = previewTodayOutfit()
        val base = outfit.recommendation.baseLayer
        setHome(outfit)

        composeRule.onNodeWithText(
            context.getString(GarmentTextMapper.garmentNameRes(base.id, base.fit))
        ).performScrollTo().performClick()

        composeRule.onNodeWithTag(HomeTestTags.GARMENT_DETAIL_SHEET).assertExists()
        composeRule.onNodeWithText(
            context.getString(GarmentTextMapper.garmentDescriptionRes(base.id, base.fit))
        ).assertExists()
    }

    @Test
    fun garment_and_accessory_chips_forward_the_exact_recommended_items() {
        val recommendation = previewTodayOutfit().recommendation
        var garment: OutfitTileUi? = null
        var accessory: AccessoryAlert? = null
        setCanvas(
            recommendation = recommendation,
            onGarmentClick = { garment = it },
            onAccessoryClick = { accessory = it }
        )

        val base = recommendation.baseLayer
        composeRule.onNodeWithText(
            context.getString(GarmentTextMapper.garmentNameRes(base.id, base.fit))
        ).performClick()
        assertEquals("base", garment?.layerKey)
        assertEquals(base, garment?.garment)

        val alert = recommendation.accessories.first()
        composeRule.onNodeWithText(accessoryLabel(alert)).performClick()
        assertEquals(alert, accessory)
    }

    @Test
    fun home_forwards_accessory_chip_callbacks() {
        val alert = previewTodayOutfit().recommendation.accessories.first()
        var clicked: AccessoryAlert? = null
        setHome(onAccessoryClick = { clicked = it })

        composeRule.onNodeWithText(accessoryLabel(alert)).performScrollTo().performClick()
        assertEquals(alert, clicked)
    }

    @Test
    fun canvas_has_only_active_pieces_and_every_hotspot_is_at_least_64dp() {
        val sample = previewTodayOutfit().recommendation
        val reasons = listOf(
            AccessoryReason.RainWindow(LocalTime.of(14, 0), LocalTime.of(17, 0)),
            AccessoryReason.UvIndex(6.0),
            AccessoryReason.FreezingConditions,
            AccessoryReason.Wind(38),
            AccessoryReason.DiurnalSwing(9.0),
            AccessoryReason.ExtremeHeat(41.0),
            AccessoryReason.RainProbability(55)
        )
        val recommendation = sample.copy(
            outerLayer = GarmentCatalog.LIGHT_JACKET,
            accessories = AccessoryType.entries.mapIndexed { index, type ->
                AccessoryAlert(type, reasons[index % reasons.size], PriorityLevel.RECOMMENDED)
            }
        )
        var garment: OutfitTileUi? = null
        var accessory: AccessoryAlert? = null
        setCanvas(
            recommendation = recommendation,
            onGarmentClick = { garment = it },
            onAccessoryClick = { accessory = it }
        )

        val garmentTags = listOf("base", "mid", "outer", "bottom", "footwear")
            .map(LookbookTestTags::garment) +
            recommendation.accessories.map { LookbookTestTags.accessory(it.type) }
        garmentTags.forEach { tag ->
            val node = composeRule.onNodeWithTag(tag, useUnmergedTree = true).assertExists()
            val bounds = node.fetchSemanticsNode().boundsInRoot
            val width = with(composeRule.density) { bounds.width.toDp() }
            val height = with(composeRule.density) { bounds.height.toDp() }
            assertTrue("$tag is narrower than 64.dp: $width", width >= 64.dp)
            assertTrue("$tag is shorter than 64.dp: $height", height >= 64.dp)
        }

        composeRule.onNodeWithTag(LookbookTestTags.garment("base"), useUnmergedTree = true)
            .performClick()
        assertEquals("base", garment?.layerKey)
        val umbrella = recommendation.accessories.first { it.type == AccessoryType.UMBRELLA }
        composeRule.onNodeWithTag(
            LookbookTestTags.accessory(AccessoryType.UMBRELLA),
            useUnmergedTree = true
        ).performClick()
        assertEquals(umbrella, accessory)
    }

    @Test
    fun canvas_adds_no_text_or_click_semantics_and_each_chip_is_read_once() {
        val recommendation = previewTodayOutfit().recommendation
        setCanvas(recommendation)

        composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(HomeTestTags.OUTFIT_LOOKBOOK)),
            useUnmergedTree = true
        ).filter(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(0)
        composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(HomeTestTags.OUTFIT_LOOKBOOK)),
            useUnmergedTree = true
        ).filter(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertCountEquals(0)

        listOfNotNull(
            recommendation.baseLayer,
            recommendation.midLayer,
            recommendation.outerLayer,
            recommendation.bottomLayer,
            recommendation.footwear
        ).forEach { garment ->
            val name = context.getString(
                GarmentTextMapper.garmentNameRes(garment.id, garment.fit)
            )
            composeRule.onAllNodesWithText(name).assertCountEquals(1)
        }
        recommendation.accessories.forEach { alert ->
            composeRule.onAllNodesWithText(accessoryLabel(alert)).assertCountEquals(1)
        }
    }

    @Test
    fun closet_badges_stay_in_chip_semantics_without_changing_the_silent_canvas() {
        val recommendation = previewTodayOutfit().recommendation
        val base = recommendation.baseLayer
        val name = context.getString(GarmentTextMapper.garmentNameRes(base.id, base.fit))
        val indicator = text(R.string.home_in_closet_indicator)
        setCanvas(
            recommendation = recommendation,
            closetItemKeys = setOf(base.category to base.id)
        )

        composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(HomeTestTags.OUTFIT_LOOKBOOK)),
            useUnmergedTree = true
        ).filter(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text)).assertCountEquals(0)
        composeRule.onAllNodes(
            hasAnyAncestor(hasTestTag(HomeTestTags.OUTFIT_LOOKBOOK)),
            useUnmergedTree = true
        ).filter(SemanticsMatcher.keyIsDefined(SemanticsActions.OnClick)).assertCountEquals(0)

        composeRule.onNode(hasText(name) and hasText(indicator)).assertExists()
        composeRule.onAllNodesWithText(name).assertCountEquals(1)
        composeRule.onAllNodesWithText(indicator).assertCountEquals(1)
        recommendation.accessories.forEach { alert ->
            composeRule.onAllNodesWithText(accessoryLabel(alert)).assertCountEquals(1)
        }
    }

    @Test
    fun spatial_hotspots_remain_left_to_right_in_an_rtl_locale() {
        setCanvas(rtl = true)

        val baseLeft = composeRule.onNodeWithTag(
            LookbookTestTags.garment("base"),
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot.left
        val accessoryLeft = composeRule.onNodeWithTag(
            LookbookTestTags.accessory(previewTodayOutfit().recommendation.accessories.first().type),
            useUnmergedTree = true
        ).fetchSemanticsNode().boundsInRoot.left
        assertTrue("RTL must not mirror the spatial collage", baseLeft < accessoryLeft)
    }

    @Test
    fun two_hundred_percent_font_selects_grid_and_keeps_all_content_usable() {
        val outfit = previewTodayOutfit()
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                    HomeScreen(
                        state = HomeUiState.Content(outfit),
                        onIntent = {},
                        onEffect = {}
                    )
                }
            }
        }

        composeRule.onNodeWithText(text(R.string.home_outfit_view_grid)).assertIsSelected()
        composeRule.onNodeWithText(text(R.string.home_outfit_view_look)).assertIsNotEnabled()
        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_LOOKBOOK).assertDoesNotExist()
        composeRule.onNodeWithTag(HomeTestTags.OUTFIT_GRID).assertExists()

        listOf("base", "mid", "bottom", "footwear").forEach { key ->
            composeRule.onNodeWithTag(HomeTestTags.outfitTile(key))
                .performScrollTo()
                .assertIsDisplayed()
        }
        outfit.recommendation.accessories.forEach { alert ->
            composeRule.onNodeWithText(accessoryLabel(alert))
                .performScrollTo()
                .assertIsDisplayed()
        }
    }

    @Test
    fun bento_grid_is_one_column_at_two_hundred_percent_font() {
        val recommendation = previewTodayOutfit().recommendation
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                ShowMeOutfitTheme(darkTheme = false, dynamicColor = false) {
                    OutfitBentoGrid(
                        recommendation = recommendation,
                        accentColor = androidx.compose.material3.MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(DesignTokens.Spacing.lg)
                    )
                }
            }
        }

        val base = composeRule.onNodeWithTag(HomeTestTags.outfitTile("base"))
            .fetchSemanticsNode().boundsInRoot
        val mid = composeRule.onNodeWithTag(HomeTestTags.outfitTile("mid"))
            .fetchSemanticsNode().boundsInRoot
        assertEquals("one-column tiles share a left edge", base.left, mid.left, 1f)
        assertTrue("the second tile starts below the first", base.bottom <= mid.top)
    }
}
