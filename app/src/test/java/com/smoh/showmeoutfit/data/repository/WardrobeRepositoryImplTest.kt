package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.core.database.dao.WardrobeDao
import com.smoh.showmeoutfit.core.database.entity.WardrobeEntity
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.WardrobeItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WardrobeRepositoryImplTest {

    private lateinit var dao: FakeWardrobeDao
    private lateinit var repository: WardrobeRepositoryImpl

    @Before
    fun setUp() {
        dao = FakeWardrobeDao()
        repository = WardrobeRepositoryImpl(dao)
    }

    private fun item(
        id: String,
        category: LayerCategory = LayerCategory.MID,
        createdAt: Long = 1000L,
        photo: String? = null
    ) = WardrobeItem(
        id = id,
        name = "Item $id",
        category = category,
        garmentId = GarmentId.CARDIGAN,
        fit = GarmentFit.WOMEN,
        isWaterResistant = true,
        photoRelativePath = photo,
        createdAtEpochMillis = createdAt
    )

    @Test
    fun `observeAll maps every entity field to the domain model`() = runTest {
        dao.store.value = listOf(
            WardrobeEntity(
                id = "a",
                name = "Grey cardigan",
                category = LayerCategory.MID.name,
                garmentId = GarmentId.CARDIGAN.name,
                fit = GarmentFit.WOMEN.name,
                isWaterResistant = false,
                photoRelativePath = "wardrobe/a.jpg",
                createdAtEpochMillis = 500L
            )
        )

        val items = repository.observeAll().first()

        assertEquals(1, items.size)
        val mapped = items.single()
        assertEquals("a", mapped.id)
        assertEquals("Grey cardigan", mapped.name)
        assertEquals(LayerCategory.MID, mapped.category)
        assertEquals(GarmentId.CARDIGAN, mapped.garmentId)
        assertEquals(GarmentFit.WOMEN, mapped.fit)
        assertEquals(false, mapped.isWaterResistant)
        assertEquals("wardrobe/a.jpg", mapped.photoRelativePath)
        assertEquals(500L, mapped.createdAtEpochMillis)
    }

    @Test
    fun `observeAll keeps the dao ordering and maps every row`() = runTest {
        dao.store.value = listOf(
            WardrobeEntity("new", "New", LayerCategory.BASE.name, GarmentId.POLO_SHIRT.name, GarmentFit.MEN.name, false, null, 3000L),
            WardrobeEntity("old", "Old", LayerCategory.BASE.name, GarmentId.POLO_SHIRT.name, GarmentFit.MEN.name, false, null, 1000L)
        )

        val ids = repository.observeAll().first().map { it.id }

        assertEquals(listOf("new", "old"), ids)
    }

    @Test
    fun `observeByCategory forwards the category name and returns only that category`() = runTest {
        dao.store.value = listOf(
            WardrobeEntity("outer", "Raincoat", LayerCategory.OUTER.name, GarmentId.RAINCOAT.name, GarmentFit.UNISEX.name, true, null, 2000L),
            WardrobeEntity("mid", "Cardigan", LayerCategory.MID.name, GarmentId.CARDIGAN.name, GarmentFit.UNISEX.name, false, null, 1000L)
        )

        val observed = repository.observeByCategory(LayerCategory.OUTER).first()

        assertEquals(LayerCategory.OUTER.name, dao.lastCategoryQuery)
        assertEquals(listOf("outer"), observed.map { it.id })
        assertEquals(LayerCategory.OUTER, observed.single().category)
    }

    @Test
    fun `upsert maps the domain model onto the entity and persists it`() = runTest {
        repository.upsert(item(id = "x", category = LayerCategory.FOOTWEAR, createdAt = 42L, photo = "p.png"))

        val stored = dao.store.value.single()
        assertEquals("x", stored.id)
        assertEquals("Item x", stored.name)
        assertEquals(LayerCategory.FOOTWEAR.name, stored.category)
        assertEquals(GarmentId.CARDIGAN.name, stored.garmentId)
        assertEquals(GarmentFit.WOMEN.name, stored.fit)
        assertTrue(stored.isWaterResistant)
        assertEquals("p.png", stored.photoRelativePath)
        assertEquals(42L, stored.createdAtEpochMillis)
        assertEquals(stored, dao.lastUpsert)
    }

    @Test
    fun `upsert with an existing id replaces the row instead of duplicating it`() = runTest {
        repository.upsert(item(id = "dup", createdAt = 10L))
        repository.upsert(item(id = "dup", createdAt = 20L))

        assertEquals(1, dao.store.value.size)
        assertEquals(20L, dao.store.value.single().createdAtEpochMillis)
    }

    @Test
    fun `delete removes the row through the dao`() = runTest {
        dao.store.value = listOf(
            WardrobeEntity("keep", "Keep", LayerCategory.BASE.name, GarmentId.COTTON_TEE.name, GarmentFit.UNISEX.name, false, null, 1000L),
            WardrobeEntity("drop", "Drop", LayerCategory.BASE.name, GarmentId.COTTON_TEE.name, GarmentFit.UNISEX.name, false, null, 2000L)
        )

        repository.delete("drop")

        assertEquals("drop", dao.lastDeletedId)
        assertEquals(listOf("keep"), repository.observeAll().first().map { it.id })
    }

    @Test
    fun `unknown persisted enum names degrade to catalog defaults instead of crashing`() = runTest {
        dao.store.value = listOf(
            WardrobeEntity("future", "Future", "HAT", "BEANIE", "KIDS", false, null, 1000L)
        )

        val mapped = repository.observeAll().first().single()

        assertEquals(LayerCategory.BASE, mapped.category)
        assertEquals(GarmentId.COTTON_TEE, mapped.garmentId)
        assertEquals(GarmentFit.UNISEX, mapped.fit)
        assertNull(mapped.photoRelativePath)
    }

    // --- Fake DAO ---
    private class FakeWardrobeDao : WardrobeDao {
        val store = MutableStateFlow<List<WardrobeEntity>>(emptyList())
        var lastCategoryQuery: String? = null
        var lastUpsert: WardrobeEntity? = null
        var lastDeletedId: String? = null

        override fun observeAll(): Flow<List<WardrobeEntity>> = store

        override fun observeByCategory(category: String): Flow<List<WardrobeEntity>> {
            lastCategoryQuery = category
            // Mirrors the SQL WHERE clause so the repository assertion sees a pre-filtered list.
            return store.map { entities -> entities.filter { it.category == category } }
        }

        override suspend fun upsert(entity: WardrobeEntity) {
            lastUpsert = entity
            store.update { current ->
                current.filterNot { it.id == entity.id } + entity
            }
        }

        override suspend fun deleteById(id: String) {
            lastDeletedId = id
            store.update { current -> current.filterNot { it.id == id } }
        }
    }
}
