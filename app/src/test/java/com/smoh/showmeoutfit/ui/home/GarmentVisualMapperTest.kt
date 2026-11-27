package com.smoh.showmeoutfit.ui.home

import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GarmentVisualMapperTest {

    private val fitSensitive = setOf(
        GarmentId.COTTON_TEE,
        GarmentId.POLO_SHIRT,
        GarmentId.CARDIGAN,
        GarmentId.HOODIE,
        GarmentId.JEANS,
        GarmentId.CHINOS,
        GarmentId.TROUSERS,
        GarmentId.SHORTS
    )

    @Test
    fun `every garment has a non-zero cutout for every fit`() {
        GarmentId.entries.forEach { id ->
            GarmentFit.entries.forEach { fit ->
                assertTrue(
                    "missing cutout for $id/$fit",
                    GarmentVisualMapper.garmentDrawableRes(id, fit) != 0
                )
            }
        }
    }

    @Test
    fun `men and unisex share the base cut`() {
        GarmentId.entries.forEach { id ->
            assertEquals(
                "men and unisex art differ for $id",
                GarmentVisualMapper.garmentDrawableRes(id, GarmentFit.UNISEX),
                GarmentVisualMapper.garmentDrawableRes(id, GarmentFit.MEN)
            )
        }
    }

    @Test
    fun `only fit-sensitive garments have a distinct women cut`() {
        GarmentId.entries.forEach { id ->
            val unisex = GarmentVisualMapper.garmentDrawableRes(id, GarmentFit.UNISEX)
            val women = GarmentVisualMapper.garmentDrawableRes(id, GarmentFit.WOMEN)
            if (id in fitSensitive) {
                assertNotEquals("women cut is missing for $id", unisex, women)
            } else {
                assertEquals("unexpected women-only cut for $id", unisex, women)
            }
        }
    }

    @Test
    fun `women cut resolves to the eight expected resources`() {
        val expected = mapOf(
            GarmentId.COTTON_TEE to R.drawable.cutout_cotton_tee_women,
            GarmentId.POLO_SHIRT to R.drawable.cutout_polo_shirt_women,
            GarmentId.CARDIGAN to R.drawable.cutout_cardigan_women,
            GarmentId.HOODIE to R.drawable.cutout_hoodie_women,
            GarmentId.JEANS to R.drawable.cutout_jeans_women,
            GarmentId.CHINOS to R.drawable.cutout_chinos_women,
            GarmentId.TROUSERS to R.drawable.cutout_trousers_women,
            GarmentId.SHORTS to R.drawable.cutout_shorts_women
        )
        expected.forEach { (id, res) ->
            assertEquals(
                "wrong women cut for $id",
                res,
                GarmentVisualMapper.garmentDrawableRes(id, GarmentFit.WOMEN)
            )
        }
    }

    @Test
    fun `every garment resolves to its own base cutout`() {
        val byRes = GarmentId.entries.groupBy { GarmentVisualMapper.garmentDrawableRes(it) }
        assertTrue("cutouts shared by several garments: $byRes", byRes.values.all { it.size == 1 })
        assertEquals(29, byRes.size)
    }

    @Test
    fun `garment visual sources expose the same preset as the drawable mapper`() {
        GarmentId.entries.forEach { id ->
            GarmentFit.entries.forEach { fit ->
                val source = GarmentVisualMapper.garmentVisualSource(id, fit)
                assertTrue("non-preset source for $id/$fit", source is GarmentVisualSource.Preset)
                assertEquals(
                    GarmentVisualMapper.garmentDrawableRes(id, fit),
                    (source as GarmentVisualSource.Preset).drawableRes
                )
            }
        }
    }

    @Test
    fun `all garment fits resolve to 37 unique cutouts`() {
        val resources = GarmentId.entries.flatMap { id ->
            GarmentFit.entries.map { fit -> GarmentVisualMapper.garmentDrawableRes(id, fit) }
        }.toSet()
        assertEquals(29 + 8, resources.size)
    }

    @Test
    fun `every accessory has its own non-zero cutout and preset source`() {
        val byRes = AccessoryType.entries.groupBy { type ->
            val res = GarmentVisualMapper.accessoryDrawableRes(type)
            assertTrue("missing accessory cutout for $type", res != 0)
            val source = GarmentVisualMapper.accessoryVisualSource(type)
            assertTrue("non-preset accessory source for $type", source is GarmentVisualSource.Preset)
            assertEquals(res, (source as GarmentVisualSource.Preset).drawableRes)
            res
        }
        assertTrue("accessory cutouts shared: $byRes", byRes.values.all { it.size == 1 })
        assertEquals(8, byRes.size)
    }

    @Test
    fun `garment and accessory cutouts never collide`() {
        val garments = GarmentId.entries.flatMap { id ->
            GarmentFit.entries.map { fit -> GarmentVisualMapper.garmentDrawableRes(id, fit) }
        }.toSet()
        val accessories = AccessoryType.entries.map { GarmentVisualMapper.accessoryDrawableRes(it) }.toSet()
        assertTrue("a drawable is used for both: ${garments intersect accessories}", (garments intersect accessories).isEmpty())
        assertEquals("45 studio cutouts total", 45, (garments + accessories).size)
    }
}
