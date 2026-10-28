package com.smoh.showmeoutfit.ui.home

import com.smoh.showmeoutfit.domain.model.AccessoryReason
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.HeadlineReason
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.SummaryData
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.ui.components.AccessoryPriority
import com.smoh.showmeoutfit.ui.theme.WeatherConditionGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.util.Locale

/**
 * Exhaustive guards for the presentation mapper: every enum entry and every sealed variant is
 * covered, every resource id is real (non-zero), and no two domain ids collapse onto the same copy.
 */
class GarmentTextMapperTest {

    private val locale = Locale.US

    // --- Garments -------------------------------------------------------------------------

    @Test
    fun `every garment has a non-zero name for every fit`() {
        GarmentId.entries.forEach { id ->
            GarmentFit.entries.forEach { fit ->
                assertTrue(
                    "missing name for $id/$fit",
                    GarmentTextMapper.garmentNameRes(id, fit) != 0
                )
            }
        }
    }

    @Test
    fun `every garment has a non-zero description for every fit`() {
        GarmentId.entries.forEach { id ->
            GarmentFit.entries.forEach { fit ->
                assertTrue(
                    "missing description for $id/$fit",
                    GarmentTextMapper.garmentDescriptionRes(id, fit) != 0
                )
            }
        }
    }

    @Test
    fun `no two garments share a name resource in the same fit`() {
        // Catches copy-paste bugs such as two ids pointing at the same string.
        GarmentFit.entries.forEach { fit ->
            val collapsed = GarmentId.entries
                .groupBy { GarmentTextMapper.garmentNameRes(it, fit) }
                .filterValues { it.size > 1 }

            assertTrue("names collapse for fit $fit: $collapsed", collapsed.isEmpty())
        }
    }

    @Test
    fun `no two garments share a description resource`() {
        val collapsed = GarmentId.entries
            .groupBy { GarmentTextMapper.garmentDescriptionRes(it) }
            .filterValues { it.size > 1 }

        assertTrue("descriptions collapse: $collapsed", collapsed.isEmpty())
    }

    @Test
    fun `fit variants only exist where the cut differs`() {
        // The unisex name stays the fallback for every garment.
        GarmentId.entries.forEach { id ->
            assertTrue(GarmentTextMapper.garmentNameRes(id, GarmentFit.UNISEX) != 0)
        }

        // Multi-word garments whose cut is the same across the catalog keep one label.
        assertEquals(
            GarmentTextMapper.garmentNameRes(GarmentId.RAINCOAT, GarmentFit.MEN),
            GarmentTextMapper.garmentNameRes(GarmentId.RAINCOAT, GarmentFit.WOMEN)
        )

        // Cut-sensitive garments must not silently fall back to the unisex label.
        assertNotEquals(
            GarmentTextMapper.garmentNameRes(GarmentId.JEANS, GarmentFit.MEN),
            GarmentTextMapper.garmentNameRes(GarmentId.JEANS, GarmentFit.UNISEX)
        )
        assertNotEquals(
            GarmentTextMapper.garmentNameRes(GarmentId.JEANS, GarmentFit.MEN),
            GarmentTextMapper.garmentNameRes(GarmentId.JEANS, GarmentFit.WOMEN)
        )
    }

    // --- Accessories ----------------------------------------------------------------------

    @Test
    fun `every accessory has a distinct non-zero name and icon description`() {
        val names = AccessoryType.entries.map { GarmentTextMapper.accessoryNameRes(it) }
        val icons = AccessoryType.entries.map { GarmentTextMapper.accessoryIconDescriptionRes(it) }

        assertTrue("zero name resource", names.none { it == 0 })
        assertTrue("zero icon resource", icons.none { it == 0 })
        assertEquals(
            "accessory names must be distinct",
            AccessoryType.entries.size,
            names.distinct().size
        )
    }

    @Test
    fun `every priority maps onto the design-system urgency`() {
        assertEquals(
            AccessoryPriority.CRITICAL,
            GarmentTextMapper.accessoryPriority(PriorityLevel.CRITICAL)
        )
        assertEquals(
            AccessoryPriority.RECOMMENDED,
            GarmentTextMapper.accessoryPriority(PriorityLevel.RECOMMENDED)
        )
        assertEquals(
            AccessoryPriority.OPTIONAL,
            GarmentTextMapper.accessoryPriority(PriorityLevel.OPTIONAL)
        )
        // Exhaustive: the mapping must be total, not a partial when.
        assertEquals(
            PriorityLevel.entries.size,
            PriorityLevel.entries.map { GarmentTextMapper.accessoryPriority(it) }.distinct().size
        )
    }

    @Test
    fun `every accessory reason has copy and the arguments the UI formats`() {
        val reasons = listOf(
            AccessoryReason.RainWindow(LocalTime.of(15, 0), LocalTime.of(17, 0)),
            AccessoryReason.RainProbability(peakPercent = 55),
            AccessoryReason.FreezingConditions,
            AccessoryReason.Wind(speedKmh = 38),
            AccessoryReason.UvIndex(peakUv = 6.24),
            AccessoryReason.DiurnalSwing(deltaCelsius = 9.4)
        )

        reasons.forEach { reason ->
            val mapped = GarmentTextMapper.accessoryReason(reason)
            assertTrue("zero resource for $reason", mapped.resId != 0)
        }

        assertEquals(
            listOf("15:00", "17:00"),
            GarmentTextMapper.accessoryReason(reasons[0]).resolve(locale).toList()
        )
        assertEquals(
            listOf("55"),
            GarmentTextMapper.accessoryReason(reasons[1]).resolve(locale).toList()
        )
        assertEquals(
            emptyList<String>(),
            GarmentTextMapper.accessoryReason(reasons[2]).resolve(locale).toList()
        )
        assertEquals(
            listOf("38"),
            GarmentTextMapper.accessoryReason(reasons[3]).resolve(locale).toList()
        )
        assertEquals(
            listOf("6.2"),
            GarmentTextMapper.accessoryReason(reasons[4]).resolve(locale).toList()
        )
        assertEquals(
            listOf("9"),
            GarmentTextMapper.accessoryReason(reasons[5]).resolve(locale).toList()
        )
    }

    // --- Weather --------------------------------------------------------------------------

    @Test
    fun `weather codes group through the design-system palette mapping`() {
        WeatherCode.entries.forEach { code ->
            assertEquals(
                "wrong group for $code",
                WeatherConditionGroup.fromWmoCode(code.code),
                GarmentTextMapper.conditionGroup(code)
            )
        }

        // Spot-check the groups the UI actually distinguishes.
        assertEquals(
            WeatherConditionGroup.CLEAR,
            GarmentTextMapper.conditionGroup(WeatherCode.CLEAR_SKY)
        )
        assertEquals(
            WeatherConditionGroup.RAIN,
            GarmentTextMapper.conditionGroup(WeatherCode.RAIN_MODERATE)
        )
        assertEquals(
            WeatherConditionGroup.SNOW,
            GarmentTextMapper.conditionGroup(WeatherCode.SNOW_FALL_HEAVY)
        )
        assertEquals(
            WeatherConditionGroup.STORM,
            GarmentTextMapper.conditionGroup(WeatherCode.THUNDERSTORM_HEAVY_HAIL)
        )
        assertEquals(
            WeatherConditionGroup.CLOUDY,
            GarmentTextMapper.conditionGroup(WeatherCode.UNKNOWN)
        )
    }

    @Test
    fun `every weather code has distinct non-zero copy`() {
        val resources = WeatherCode.entries.map { GarmentTextMapper.conditionDescriptionRes(it) }

        assertTrue("zero condition resource", resources.none { it == 0 })
        assertEquals(
            "condition copy must be distinct per WMO code",
            WeatherCode.entries.size,
            resources.distinct().size
        )
    }

    // --- Headline and summary -------------------------------------------------------------

    @Test
    fun `every headline variant has copy`() {
        val reasons = listOf(
            HeadlineReason.SnowAndFrost,
            HeadlineReason.LargeTempSwing(deltaCelsius = 9.4),
            HeadlineReason.RainExpected,
            HeadlineReason.HotAndIntenseUv,
            HeadlineReason.WarmAndPleasant,
            HeadlineReason.Chilly,
            HeadlineReason.Mild
        )

        reasons.forEach { reason ->
            assertTrue("zero headline resource for $reason", GarmentTextMapper.headline(reason).resId != 0)
        }

        assertEquals(
            listOf("9"),
            GarmentTextMapper.headline(HeadlineReason.LargeTempSwing(9.4)).resolve(locale).toList()
        )
        assertTrue(
            "static headlines take no arguments",
            GarmentTextMapper.headline(HeadlineReason.Mild).resolve(locale).isEmpty()
        )
    }

    @Test
    fun `headline resources differ per variant`() {
        val resources = listOf(
            HeadlineReason.SnowAndFrost,
            HeadlineReason.LargeTempSwing(deltaCelsius = 9.4),
            HeadlineReason.RainExpected,
            HeadlineReason.HotAndIntenseUv,
            HeadlineReason.WarmAndPleasant,
            HeadlineReason.Chilly,
            HeadlineReason.Mild
        ).map { GarmentTextMapper.headline(it).resId }

        assertEquals(7, resources.distinct().size)
    }

    @Test
    fun `summary picks the rain clause from the structured reason`() {
        val base = SummaryData(
            tempMinCelsius = 12.6,
            tempMaxCelsius = 22.0,
            hasLargeTempSwing = true,
            rainReason = null,
            peakUvIndex = 6.2
        )

        val dry = GarmentTextMapper.summary(base)
        val window = GarmentTextMapper.summary(
            base.copy(
                rainReason = AccessoryReason.RainWindow(
                    start = LocalTime.of(15, 0),
                    end = LocalTime.of(17, 0)
                )
            )
        )
        val chance = GarmentTextMapper.summary(
            base.copy(rainReason = AccessoryReason.RainProbability(peakPercent = 55))
        )

        assertEquals(listOf("13", "22"), dry.resolve(locale).toList())
        assertEquals(listOf("13", "22", "15:00", "17:00"), window.resolve(locale).toList())
        assertEquals(listOf("13", "22", "55"), chance.resolve(locale).toList())
        assertEquals(3, setOf(dry.resId, window.resId, chance.resId).size)
    }
}
