package com.smoh.showmeoutfit.ui.wardrobe

import app.cash.turbine.test
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem
import com.smoh.showmeoutfit.domain.repository.WardrobeRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WardrobeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: FakeWardrobeRepository

    private val sampleItems = listOf(
        item("base", "Thermal", LayerCategory.BASE, GarmentId.THERMAL_UNDERSHIRT, 5),
        item("mid", "Cardigan", LayerCategory.MID, GarmentId.CARDIGAN, 4),
        item("outer", "Raincoat", LayerCategory.OUTER, GarmentId.RAINCOAT, 3),
        item("bottom", "Jeans", LayerCategory.BOTTOM, GarmentId.JEANS, 2),
        item("footwear", "Sneakers", LayerCategory.FOOTWEAR, GarmentId.SNEAKERS, 1)
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = FakeWardrobeRepository(sampleItems)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = WardrobeViewModel(repository, dispatcher)

    @Test
    fun `catalog assigns every garment to exactly one selector category`() {
        val mapped = GarmentId.entries.map(WardrobeCatalog::categoryFor)

        assertTrue(mapped.all { it != null })
        val selectorGarments = LayerCategory.entries.flatMap(WardrobeCatalog::garmentsFor)
        assertEquals(GarmentId.entries.toSet(), selectorGarments.toSet())
        assertEquals(GarmentId.entries.size, selectorGarments.size)
        LayerCategory.entries.forEach { category ->
            assertTrue(WardrobeCatalog.garmentsFor(category).isNotEmpty())
        }
    }

    @Test
    fun `state emits persisted items and every category filter`() = runTest(dispatcher) {
        val viewModel = viewModel()

        viewModel.state.test {
            assertEquals(sampleItems, awaitItem().items)

            WardrobeFilter.entries.drop(1).forEach { filter ->
                viewModel.onIntent(WardrobeIntent.SelectFilter(filter))
                var emitted = awaitItem()
                while (emitted.isLoading || emitted.selectedFilter != filter) {
                    emitted = awaitItem()
                }
                assertEquals(filter.category, repository.lastObservedCategory)
                assertEquals(
                    sampleItems.filter { it.category == filter.category },
                    emitted.items
                )
            }

            viewModel.onIntent(WardrobeIntent.SelectFilter(WardrobeFilter.ALL))
            var all = awaitItem()
            while (all.isLoading || all.selectedFilter != WardrobeFilter.ALL) {
                all = awaitItem()
            }
            assertEquals(sampleItems, all.items)
            assertNull(repository.lastObservedCategory)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `add trims a custom name and persists garment fit and water resistance`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onIntent(WardrobeIntent.OpenAdd(LayerCategory.BASE, "Thermal undershirt"))
                viewModel.onIntent(
                    WardrobeIntent.UpdateEditorGarment(GarmentId.COTTON_TEE, "Cotton tee")
                )
                viewModel.onIntent(
                    WardrobeIntent.UpdateEditorFit(GarmentFit.WOMEN, "Women's cotton tee")
                )
                viewModel.onIntent(WardrobeIntent.UpdateEditorName("  Weekend tee  "))
                viewModel.onIntent(WardrobeIntent.UpdateEditorWaterResistant(true))
                viewModel.onIntent(WardrobeIntent.SaveEditor)

                assertEquals(WardrobeEffect.ItemSaved, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = repository.items.value.single { it.name == "Weekend tee" }
            assertEquals(LayerCategory.BASE, saved.category)
            assertEquals(GarmentId.COTTON_TEE, saved.garmentId)
            assertEquals(GarmentFit.WOMEN, saved.fit)
            assertTrue(saved.isWaterResistant)
            assertNull(saved.photoRelativePath)
            assertTrue(saved.createdAtEpochMillis > 0)
            assertNull(viewModel.state.value.editor)
        }

    @Test
    fun `a whitespace name falls back to the localized default garment name`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onIntent(WardrobeIntent.OpenAdd(LayerCategory.MID, "Wool sweater"))
                viewModel.onIntent(
                    WardrobeIntent.UpdateEditorGarment(GarmentId.CARDIGAN, "Cardigan")
                )
                viewModel.onIntent(WardrobeIntent.UpdateEditorName(" \t\n "))
                viewModel.onIntent(WardrobeIntent.SaveEditor)

                assertEquals(WardrobeEffect.ItemSaved, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            val saved = repository.items.value.first()
            assertEquals("Cardigan", saved.name)
            assertEquals(GarmentId.CARDIGAN, saved.garmentId)
        }

    @Test
    fun `an untouched name follows garment and fit selector changes`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(WardrobeIntent.OpenAdd(LayerCategory.BASE, "Thermal undershirt"))

        viewModel.onIntent(WardrobeIntent.UpdateEditorGarment(GarmentId.COTTON_TEE, "Cotton tee"))
        assertEquals("Cotton tee", viewModel.state.value.editor?.name)

        viewModel.onIntent(
            WardrobeIntent.UpdateEditorFit(GarmentFit.WOMEN, "Women's cotton tee")
        )
        assertEquals("Women's cotton tee", viewModel.state.value.editor?.name)
        assertEquals(GarmentFit.WOMEN, viewModel.state.value.editor?.fit)
    }

    @Test
    fun `category changes constrain the garment selector but retain a custom name`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            viewModel.onIntent(WardrobeIntent.OpenAdd(LayerCategory.OUTER, "Down parka"))
            viewModel.onIntent(
                WardrobeIntent.UpdateEditorGarment(GarmentId.RAINCOAT, "Raincoat")
            )
            viewModel.onIntent(WardrobeIntent.UpdateEditorName("Blue shell"))

            viewModel.onIntent(
                WardrobeIntent.UpdateEditorCategory(LayerCategory.BOTTOM, "Thermal pants")
            )

            val editor = requireNotNull(viewModel.state.value.editor)
            assertEquals(LayerCategory.BOTTOM, editor.category)
            assertEquals(WardrobeCatalog.garmentsFor(LayerCategory.BOTTOM).first(), editor.garmentId)
            assertEquals("Blue shell", editor.name)
        }

    @Test
    fun `a garment from another category is rejected`() = runTest(dispatcher) {
        val viewModel = viewModel()
        viewModel.onIntent(WardrobeIntent.OpenAdd(LayerCategory.BASE, "Thermal undershirt"))

        viewModel.onIntent(WardrobeIntent.UpdateEditorGarment(GarmentId.CARDIGAN, "Cardigan"))

        assertEquals(GarmentId.THERMAL_UNDERSHIRT, viewModel.state.value.editor?.garmentId)
        assertEquals(0, repository.writeAttempts)
    }

    @Test
    fun `edit preserves identity creation time and media path`() = runTest(dispatcher) {
        val original = sampleItems[1].copy(
            name = "Everyday cardigan",
            photoRelativePath = "wardrobe/cardigan.webp"
        )
        repository.items.value = listOf(original)
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(WardrobeIntent.OpenEdit(original, "Cardigan"))
            viewModel.onIntent(WardrobeIntent.UpdateEditorName("   "))
            viewModel.onIntent(
                WardrobeIntent.UpdateEditorFit(GarmentFit.WOMEN, "Women's cardigan")
            )
            viewModel.onIntent(WardrobeIntent.UpdateEditorWaterResistant(true))
            viewModel.onIntent(WardrobeIntent.SaveEditor)

            assertEquals(WardrobeEffect.ItemSaved, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        val saved = repository.items.value.single()
        assertEquals(original.id, saved.id)
        assertEquals(original.createdAtEpochMillis, saved.createdAtEpochMillis)
        assertEquals(original.photoRelativePath, saved.photoRelativePath)
        assertEquals("Women's cardigan", saved.name)
        assertEquals(GarmentFit.WOMEN, saved.fit)
        assertTrue(saved.isWaterResistant)
    }

    @Test
    fun `delete removes the edited item and emits confirmation`() = runTest(dispatcher) {
        val original = sampleItems.first()
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(WardrobeIntent.OpenEdit(original, "Thermal undershirt"))
            viewModel.onIntent(WardrobeIntent.DeleteEditor)

            assertEquals(WardrobeEffect.ItemDeleted, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(repository.items.value.none { it.id == original.id })
        assertEquals(1, repository.deleteAttempts)
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun `validation rejects a missing fallback and an overlong name without writing`() =
        runTest(dispatcher) {
            val viewModel = viewModel()

            viewModel.effects.test {
                viewModel.onIntent(WardrobeIntent.OpenAdd(LayerCategory.BASE, "   "))
                assertEquals(
                    WardrobeNameError.DEFAULT_NAME_MISSING,
                    viewModel.state.value.editor?.validationError
                )
                viewModel.onIntent(WardrobeIntent.SaveEditor)
                expectNoEvents()

                viewModel.onIntent(
                    WardrobeIntent.UpdateEditorCategory(
                        LayerCategory.MID,
                        "Cardigan"
                    )
                )
                viewModel.onIntent(
                    WardrobeIntent.UpdateEditorName("x".repeat(MAX_WARDROBE_NAME_LENGTH + 1))
                )
                assertEquals(
                    WardrobeNameError.TOO_LONG,
                    viewModel.state.value.editor?.validationError
                )
                viewModel.onIntent(WardrobeIntent.SaveEditor)
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(0, repository.writeAttempts)
            assertNotNull(viewModel.state.value.editor)
            assertFalse(viewModel.state.value.editor!!.isSaving)
        }

    @Test
    fun `failed saves keep the form open and emit an error effect`() = runTest(dispatcher) {
        repository.failWrites = true
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(WardrobeIntent.OpenAdd(LayerCategory.BASE, "Thermal undershirt"))
            viewModel.onIntent(WardrobeIntent.SaveEditor)

            assertEquals(
                WardrobeEffect.ShowError(WardrobeError.SAVE_FAILED),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }

        assertNotNull(viewModel.state.value.editor)
        assertFalse(requireNotNull(viewModel.state.value.editor).isSaving)
        assertEquals(sampleItems, repository.items.value)
    }

    @Test
    fun `failed deletes keep the item and editor open`() = runTest(dispatcher) {
        repository.failDeletes = true
        val original = sampleItems.first()
        val viewModel = viewModel()

        viewModel.effects.test {
            viewModel.onIntent(WardrobeIntent.OpenEdit(original, "Thermal undershirt"))
            viewModel.onIntent(WardrobeIntent.DeleteEditor)

            assertEquals(
                WardrobeEffect.ShowError(WardrobeError.DELETE_FAILED),
                awaitItem()
            )
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(repository.items.value.any { it.id == original.id })
        assertFalse(requireNotNull(viewModel.state.value.editor).isSaving)
    }

    @Test
    fun `an in-flight save cannot be dismissed or submitted twice`() = runTest(dispatcher) {
        val gate = CompletableDeferred<Unit>()
        repository.writeGate = gate
        val viewModel = viewModel()
        viewModel.onIntent(WardrobeIntent.OpenAdd(LayerCategory.BASE, "Thermal undershirt"))
        viewModel.onIntent(WardrobeIntent.SaveEditor)

        assertTrue(requireNotNull(viewModel.state.value.editor).isSaving)
        viewModel.onIntent(WardrobeIntent.DismissEditor)
        viewModel.onIntent(WardrobeIntent.SaveEditor)
        assertNotNull(viewModel.state.value.editor)
        assertEquals(1, repository.writeAttempts)

        gate.complete(Unit)
        assertNull(viewModel.state.value.editor)
        assertEquals(sampleItems.size + 1, repository.items.value.size)
    }

    private class FakeWardrobeRepository(
        initial: List<WardrobeItem>
    ) : WardrobeRepository {
        val items = MutableStateFlow(initial)
        var lastObservedCategory: LayerCategory? = null
        var writeAttempts = 0
        var deleteAttempts = 0
        var failWrites = false
        var failDeletes = false
        var writeGate: CompletableDeferred<Unit>? = null

        override fun observeAll(): Flow<List<WardrobeItem>> {
            lastObservedCategory = null
            return items
        }

        override fun observeByCategory(category: LayerCategory): Flow<List<WardrobeItem>> {
            lastObservedCategory = category
            return items.map { owned -> owned.filter { it.category == category } }
        }

        override suspend fun upsert(item: WardrobeItem) {
            writeAttempts++
            writeGate?.await()
            if (failWrites) throw IOException("disk full")
            items.value = (items.value.filterNot { it.id == item.id } + item)
                .sortedByDescending { it.createdAtEpochMillis }
        }

        override suspend fun delete(id: String) {
            deleteAttempts++
            if (failDeletes) throw IOException("disk full")
            items.value = items.value.filterNot { it.id == id }
        }
    }

    private companion object {
        fun item(
            id: String,
            name: String,
            category: LayerCategory,
            garmentId: GarmentId,
            order: Int
        ) = WardrobeItem(
            id = id,
            name = name,
            category = category,
            garmentId = garmentId,
            createdAtEpochMillis = order.toLong()
        )
    }
}
