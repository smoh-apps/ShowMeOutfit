package com.smoh.showmeoutfit.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.smoh.showmeoutfit.core.database.dao.WardrobeDao
import com.smoh.showmeoutfit.core.database.entity.WardrobeEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumented Room in-memory test for the v2 wardrobe table.
 * Verifies upsert (insert + update), ordering, category filtering, and delete.
 * Requires an Android emulator or device.
 */
@RunWith(AndroidJUnit4::class)
class WardrobeDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var dao: WardrobeDao

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.wardrobeDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun buildEntity(
        id: String,
        category: String = "OUTER",
        createdAt: Long = 1000L,
        name: String = "Item $id"
    ) = WardrobeEntity(
        id = id,
        name = name,
        category = category,
        garmentId = "RAINCOAT",
        fit = "UNISEX",
        isWaterResistant = true,
        photoRelativePath = null,
        createdAtEpochMillis = createdAt
    )

    @Test
    fun upsertInsert_observeAllReturnsTheItem() = runTest {
        dao.upsert(buildEntity(id = "a", name = "Navy raincoat"))

        val items = dao.observeAll().first()
        assertEquals(1, items.size)
        val stored = items.single()
        assertEquals("a", stored.id)
        assertEquals("Navy raincoat", stored.name)
        assertEquals("OUTER", stored.category)
        assertEquals("RAINCOAT", stored.garmentId)
        assertEquals("UNISEX", stored.fit)
        assertTrue(stored.isWaterResistant)
        assertEquals(1000L, stored.createdAtEpochMillis)
    }

    @Test
    fun upsertExistingId_updatesInPlaceWithoutDuplicatingRows() = runTest {
        dao.upsert(buildEntity(id = "a", name = "Old name", createdAt = 1000L))
        dao.upsert(buildEntity(id = "a", name = "New name", createdAt = 2000L))

        val items = dao.observeAll().first()
        assertEquals(1, items.size)
        assertEquals("New name", items.single().name)
        assertEquals(2000L, items.single().createdAtEpochMillis)
    }

    @Test
    fun observeAll_ordersByCreatedAtDescending() = runTest {
        dao.upsert(buildEntity(id = "old", createdAt = 1000L))
        dao.upsert(buildEntity(id = "newest", createdAt = 3000L))
        dao.upsert(buildEntity(id = "middle", createdAt = 2000L))

        val ids = dao.observeAll().first().map { it.id }
        assertEquals(listOf("newest", "middle", "old"), ids)
    }

    @Test
    fun observeByCategory_returnsOnlyMatchingCategoryInDescendingOrder() = runTest {
        dao.upsert(buildEntity(id = "outer-old", category = "OUTER", createdAt = 1000L))
        dao.upsert(buildEntity(id = "mid", category = "MID", createdAt = 2000L))
        dao.upsert(buildEntity(id = "outer-new", category = "OUTER", createdAt = 3000L))

        val outer = dao.observeByCategory("OUTER").first()
        assertEquals(listOf("outer-new", "outer-old"), outer.map { it.id })

        val mid = dao.observeByCategory("MID").first()
        assertEquals(listOf("mid"), mid.map { it.id })

        assertTrue(dao.observeByCategory("FOOTWEAR").first().isEmpty())
    }

    @Test
    fun deleteById_removesOnlyTheTargetRow() = runTest {
        dao.upsert(buildEntity(id = "keep", createdAt = 1000L))
        dao.upsert(buildEntity(id = "drop", createdAt = 2000L))

        dao.deleteById("drop")

        val remaining = dao.observeAll().first()
        assertEquals(listOf("keep"), remaining.map { it.id })
    }

    @Test
    fun deleteById_whenMissing_isANoOp() = runTest {
        dao.upsert(buildEntity(id = "keep"))

        dao.deleteById("does-not-exist")

        assertEquals(1, dao.observeAll().first().size)
    }
}
