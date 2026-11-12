package com.smoh.showmeoutfit.ui.home

import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards for the illustration pack mapping: full enum coverage, a real (non-zero) drawable per
 * entry, one distinct illustration per entry, and fit staying purely cosmetic.
 */
class GarmentVisualMapperTest {

    // --- Garments -------------------------------------------------------------------------

    @Test
    fun `every garment has a non-zero illustration for every fit`() {
        GarmentId.entries.forEach { id ->
            GarmentFit.entries.forEach { fit ->
                assertTrue(
                    "missing illustration for $id/$fit",
                    GarmentVisualMapper.garmentDrawableRes(id, fit) != 0
                )
            }
        }
    }

    @Test
    fun `fit never changes the illustration`() {
        GarmentId.entries.forEach { id ->
            val unisex = GarmentVisualMapper.garmentDrawableRes(id, GarmentFit.UNISEX)
            assertEquals(
                "men and unisex art differ for $id",
                unisex,
                GarmentVisualMapper.garmentDrawableRes(id, GarmentFit.MEN)
            )
            assertEquals(
                "women and unisex art differ for $id",
                unisex,
                GarmentVisualMapper.garmentDrawableRes(id, GarmentFit.WOMEN)
            )
        }
    }

    @Test
    fun `every garment resolves to its own illustration`() {
        val byRes = GarmentId.entries.groupBy { GarmentVisualMapper.garmentDrawableRes(it) }
        val shared = byRes.filterValues { it.size > 1 }
        assertTrue("illustrations shared by several garments: $shared", shared.isEmpty())
        assertEquals("one illustration per garment", GarmentId.entries.size, byRes.size)
    }

    @Test
    fun `garment catalog size matches the illustration pack`() {
        // The pack must stay in lock-step with the domain catalog: 29 garments, no more, no less.
        assertEquals(29, GarmentId.entries.size)
        assertEquals(29, GarmentId.entries.map { GarmentVisualMapper.garmentDrawableRes(it) }.toSet().size)
    }

    // --- Accessories ----------------------------------------------------------------------

    @Test
    fun `every accessory has a non-zero illustration`() {
        AccessoryType.entries.forEach { type ->
            assertTrue(
                "missing illustration for $type",
                GarmentVisualMapper.accessoryDrawableRes(type) != 0
            )
        }
    }

    @Test
    fun `every accessory resolves to its own illustration`() {
        val byRes = AccessoryType.entries.groupBy { GarmentVisualMapper.accessoryDrawableRes(it) }
        val shared = byRes.filterValues { it.size > 1 }
        assertTrue("illustrations shared by several accessories: $shared", shared.isEmpty())
        assertEquals("one illustration per accessory", AccessoryType.entries.size, byRes.size)
    }

    @Test
    fun `accessory catalog size matches the illustration pack`() {
        assertEquals(8, AccessoryType.entries.size)
        assertEquals(
            8,
            AccessoryType.entries.map { GarmentVisualMapper.accessoryDrawableRes(it) }.toSet().size
        )
    }

    @Test
    fun `garment and accessory illustrations never collide`() {
        val garments = GarmentId.entries.map { GarmentVisualMapper.garmentDrawableRes(it) }.toSet()
        val accessories = AccessoryType.entries.map { GarmentVisualMapper.accessoryDrawableRes(it) }.toSet()
        assertNotEquals(garments, accessories)
        assertTrue(
            "a drawable is used for both a garment and an accessory: ${garments intersect accessories}",
            (garments intersect accessories).isEmpty()
        )
    }
}
