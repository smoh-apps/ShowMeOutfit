package com.smoh.showmeoutfit.ui.wardrobe

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem
import com.smoh.showmeoutfit.domain.repository.WardrobeRepository
import com.smoh.showmeoutfit.ui.theme.ShowMeOutfitTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WardrobeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun showWardrobe(
        items: List<WardrobeItem> = emptyList(),
        fontScale: Float = 1f,
        onBack: () -> Unit = {}
    ): FakeWardrobeRepository {
        val repository = FakeWardrobeRepository(items)
        val viewModel = WardrobeViewModel(repository, Dispatchers.Main)
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density.density, fontScale = fontScale)
            ) {
                ShowMeOutfitTheme(dynamicColor = false) {
                    WardrobeRoute(onBack = onBack, viewModel = viewModel)
                }
            }
        }
        return repository
    }

    @Test
    fun empty_closet_shows_onboarding_and_opens_add_sheet() {
        showWardrobe()

        composeRule.onNodeWithTag(WardrobeTestTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithText(context.getString(com.smoh.showmeoutfit.R.string.wardrobe_empty_title))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(WardrobeTestTags.EMPTY_ADD).performClick()

        composeRule.onNodeWithTag(WardrobeTestTags.EDITOR).assertIsDisplayed()
        composeRule.onNodeWithTag(WardrobeTestTags.PREVIEW).assertIsDisplayed()
        composeRule.onNodeWithTag(WardrobeTestTags.SAVE).assertExists()
    }

    @Test
    fun add_flow_selects_category_garment_fit_and_persists_custom_name() {
        val repository = showWardrobe()

        composeRule.onNodeWithTag(WardrobeTestTags.ADD_ITEM).performClick()
        composeRule.onNodeWithTag(WardrobeTestTags.CATEGORY_SELECTOR).performClick()
        composeRule.onNodeWithTag(WardrobeTestTags.categoryOption(LayerCategory.MID)).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(WardrobeTestTags.GARMENT_SELECTOR).performScrollTo().performClick()
        composeRule.onNodeWithTag(WardrobeTestTags.garmentOption(GarmentId.CARDIGAN)).performClick()
        composeRule.onNodeWithTag(WardrobeTestTags.fit(GarmentFit.WOMEN)).performScrollTo().performClick()
        composeRule.onNodeWithTag(WardrobeTestTags.NAME_INPUT).performScrollTo()
            .performTextInput("Rainy day cardigan")
        composeRule.onNodeWithTag(WardrobeTestTags.WATER_SWITCH).performScrollTo().performClick()
        composeRule.onNodeWithTag(WardrobeTestTags.SAVE).performScrollTo().performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Rainy day cardigan").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag(WardrobeTestTags.EDITOR)
                    .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(WardrobeTestTags.EDITOR).assertDoesNotExist()
        val saved = repository.items.value.single()
        composeRule.onNodeWithTag(WardrobeTestTags.item(saved.id)).assertIsDisplayed()
        composeRule.onNodeWithText("Rainy day cardigan").assertIsDisplayed()
        composeRule.onAllNodesWithText(
            context.getString(com.smoh.showmeoutfit.R.string.wardrobe_category_mid)
        ).assertCountEquals(2) // Filter chip plus the saved card's category label.
        composeRule.onNodeWithTag(WardrobeTestTags.waterBadge(saved.id)).assertIsDisplayed()
    }

    @Test
    fun every_filter_chip_shows_only_its_category() {
        val items = listOf(
            sampleItem("base", LayerCategory.BASE, GarmentId.THERMAL_UNDERSHIRT),
            sampleItem("mid", LayerCategory.MID, GarmentId.CARDIGAN),
            sampleItem("outer", LayerCategory.OUTER, GarmentId.RAINCOAT),
            sampleItem("bottom", LayerCategory.BOTTOM, GarmentId.JEANS),
            sampleItem("footwear", LayerCategory.FOOTWEAR, GarmentId.SNEAKERS)
        )
        showWardrobe(items)

        WardrobeFilter.entries.drop(1).forEach { filter ->
            composeRule.onNodeWithTag(WardrobeTestTags.filter(filter)).performScrollTo().performClick()
            composeRule.waitForIdle()
            val expected = items.single { it.category == filter.category }
            composeRule.onNodeWithTag(WardrobeTestTags.item(expected.id)).assertIsDisplayed()
            items.filterNot { it.category == filter.category }.forEach { hidden ->
                composeRule.onNodeWithTag(WardrobeTestTags.item(hidden.id)).assertDoesNotExist()
            }
        }

        composeRule.onNodeWithTag(WardrobeTestTags.filter(WardrobeFilter.ALL))
            .performScrollTo().performClick()
        composeRule.waitForIdle()
        items.forEachIndexed { index, item ->
            composeRule.onNodeWithTag(WardrobeTestTags.GRID).performScrollToIndex(index)
            composeRule.onNodeWithTag(WardrobeTestTags.item(item.id)).assertIsDisplayed()
        }
    }

    @Test
    fun edit_and_delete_update_the_visible_closet() {
        val original = sampleItem("jeans", LayerCategory.BOTTOM, GarmentId.JEANS)
        showWardrobe(listOf(original))

        composeRule.onNodeWithTag(WardrobeTestTags.item(original.id)).performClick()
        composeRule.onNodeWithTag(WardrobeTestTags.NAME_INPUT).performScrollTo().performTextClearance()
        composeRule.onNodeWithTag(WardrobeTestTags.NAME_INPUT).performTextInput("Dark jeans")
        composeRule.onNodeWithTag(WardrobeTestTags.SAVE).performScrollTo().performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithText("Dark jeans").fetchSemanticsNodes().isNotEmpty() &&
                composeRule.onAllNodesWithTag(WardrobeTestTags.EDITOR)
                    .fetchSemanticsNodes().isEmpty()
        }
        composeRule.onNodeWithTag(WardrobeTestTags.EDITOR).assertDoesNotExist()
        composeRule.onNodeWithTag(WardrobeTestTags.item(original.id)).performClick()
        composeRule.onNodeWithTag(WardrobeTestTags.DELETE).performScrollTo().performClick()

        composeRule.waitUntil(TIMEOUT_MILLIS) {
            composeRule.onAllNodesWithTag(WardrobeTestTags.EMPTY).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(WardrobeTestTags.item(original.id)).assertDoesNotExist()
    }

    @Test
    fun large_font_switches_the_grid_to_one_column() {
        val first = sampleItem("first", LayerCategory.BASE, GarmentId.COTTON_TEE).copy(name = "One")
        val second = sampleItem("second", LayerCategory.BASE, GarmentId.POLO_SHIRT).copy(name = "Two")
        showWardrobe(listOf(first, second), fontScale = 2f)

        val firstBounds = composeRule.onNodeWithTag(WardrobeTestTags.item(first.id))
            .fetchSemanticsNode().boundsInRoot
        composeRule.onNodeWithTag(WardrobeTestTags.GRID).performScrollToIndex(1)
        val secondBounds = composeRule.onNodeWithTag(WardrobeTestTags.item(second.id))
            .fetchSemanticsNode().boundsInRoot

        assertEquals(firstBounds.left, secondBounds.left, 1f)
        assertEquals(firstBounds.width, secondBounds.width, 1f)
    }

    @Test
    fun back_action_leaves_the_closet() {
        var wentBack = false
        showWardrobe(onBack = { wentBack = true })

        composeRule.onNodeWithTag(WardrobeTestTags.BACK).performClick()

        assertTrue(wentBack)
    }

    private fun sampleItem(
        id: String,
        category: LayerCategory,
        garmentId: GarmentId
    ) = WardrobeItem(
        id = id,
        name = id,
        category = category,
        garmentId = garmentId,
        fit = GarmentFit.UNISEX,
        createdAtEpochMillis = 1L
    )

    private class FakeWardrobeRepository(
        initial: List<WardrobeItem>
    ) : WardrobeRepository {
        val items = MutableStateFlow(initial)

        override fun observeAll(): Flow<List<WardrobeItem>> = items

        override fun observeByCategory(category: LayerCategory): Flow<List<WardrobeItem>> =
            items.map { owned -> owned.filter { it.category == category } }

        override suspend fun upsert(item: WardrobeItem) {
            items.value = (items.value.filterNot { it.id == item.id } + item)
                .sortedByDescending { it.createdAtEpochMillis }
        }

        override suspend fun delete(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000L
    }
}
