package com.smoh.showmeoutfit.ui.notifications

import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AccessoryAlert
import com.smoh.showmeoutfit.domain.model.AccessoryReason
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.Garment
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.HeadlineReason
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.OutfitRecommendation
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.SummaryData
import com.smoh.showmeoutfit.domain.model.WeatherHighlights
import com.smoh.showmeoutfit.ui.home.MappedText
import com.smoh.showmeoutfit.ui.home.TextArg
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The highlights are the only part of the notification that argues with the layer list, so each
 * rule is pinned: the mapper reads the engine's structured reasons and must never invent a warning
 * the dashboard would not show.
 */
class NotificationHighlightsTest {

    // --- Rain -----------------------------------------------------------------------------

    @Test
    fun `a rain window becomes a timed highlight`() {
        val highlights = NotificationHighlights.of(
            recommendation(rainReason = AccessoryReason.RainWindow(LocalTime.of(16, 0), LocalTime.of(18, 0)))
        )

        val rain = highlights.single()
        assertEquals(R.string.notification_highlight_rain_window, rain.resId)
        assertEquals(
            listOf(TextArg.Clock(LocalTime.of(16, 0)), TextArg.Clock(LocalTime.of(18, 0))),
            rain.args
        )
    }

    @Test
    fun `a rain probability without a window reports the peak chance`() {
        val highlights = NotificationHighlights.of(
            recommendation(rainReason = AccessoryReason.RainProbability(peakPercent = 60))
        )

        val rain = highlights.single()
        assertEquals(R.string.notification_highlight_rain_chance, rain.resId)
        assertEquals(listOf(TextArg.Count(60)), rain.args)
    }

    @Test
    fun `rain falls back to the umbrella reason when the summary carries none`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                rainReason = null,
                accessories = listOf(
                    alert(AccessoryType.UMBRELLA, AccessoryReason.RainProbability(peakPercent = 40))
                )
            )
        )

        assertEquals(R.string.notification_highlight_rain_chance, highlights.single().resId)
    }

    // --- UV -------------------------------------------------------------------------------

    @Test
    fun `uv highlight carries the peak index and no hat when none was recommended`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                accessories = listOf(
                    alert(AccessoryType.SUNGLASSES, AccessoryReason.UvIndex(peakUv = 4.5)),
                    alert(AccessoryType.SUNSCREEN, AccessoryReason.UvIndex(peakUv = 4.5))
                )
            )
        )

        val uv = highlights.single()
        assertEquals(R.string.notification_highlight_uv, uv.resId)
        assertEquals(listOf(TextArg.Number(4.5, decimals = 1)), uv.args)
    }

    @Test
    fun `uv highlight upgrades its wording when a sun hat is in the set`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                accessories = listOf(
                    alert(AccessoryType.SUNGLASSES, AccessoryReason.UvIndex(peakUv = 7.0)),
                    alert(AccessoryType.SUN_HAT, AccessoryReason.UvIndex(peakUv = 7.0))
                )
            )
        )

        assertEquals(R.string.notification_highlight_uv_hat, highlights.single().resId)
    }

    @Test
    fun `no uv highlight without the sunglasses signal`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                accessories = listOf(
                    alert(AccessoryType.SUNSCREEN, AccessoryReason.UvIndex(peakUv = 5.0))
                )
            )
        )

        assertTrue(highlights.isEmpty())
    }

    // --- Wind -----------------------------------------------------------------------------

    @Test
    fun `wind highlight appears only with a windbreaker and a strong peak`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                maxWindKmh = 42.0,
                outerLayer = Garment(GarmentId.WINDBREAKER, LayerCategory.OUTER)
            )
        )

        val wind = highlights.single()
        assertEquals(R.string.notification_highlight_wind, wind.resId)
        assertEquals(listOf(TextArg.Count(42)), wind.args)
    }

    @Test
    fun `wind without a windbreaker is not announced`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                maxWindKmh = 42.0,
                outerLayer = Garment(GarmentId.LIGHT_JACKET, LayerCategory.OUTER)
            )
        )

        assertTrue(highlights.isEmpty())
    }

    @Test
    fun `windbreaker on a calm day is not announced`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                maxWindKmh = 20.0,
                outerLayer = Garment(GarmentId.WINDBREAKER, LayerCategory.OUTER)
            )
        )

        assertTrue(highlights.isEmpty())
    }

    // --- Heat -----------------------------------------------------------------------------

    @Test
    fun `a critical water bottle becomes a hydration highlight`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                accessories = listOf(
                    alert(
                        AccessoryType.WATER_BOTTLE,
                        AccessoryReason.ExtremeHeat(tempCelsius = 41.4),
                        PriorityLevel.CRITICAL
                    )
                )
            )
        )

        val heat = highlights.single()
        assertEquals(R.string.notification_highlight_hydration, heat.resId)
        assertEquals(listOf(TextArg.Number(41.4, decimals = 0)), heat.args)
    }

    @Test
    fun `hydration follows the rain line and precedes the UV line`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                rainReason = AccessoryReason.RainProbability(peakPercent = 50),
                accessories = listOf(
                    alert(
                        AccessoryType.WATER_BOTTLE,
                        AccessoryReason.ExtremeHeat(tempCelsius = 41.0),
                        PriorityLevel.CRITICAL
                    ),
                    alert(AccessoryType.SUNGLASSES, AccessoryReason.UvIndex(peakUv = 8.0))
                )
            )
        )

        assertEquals(
            listOf(
                R.string.notification_highlight_rain_chance,
                R.string.notification_highlight_hydration,
                R.string.notification_highlight_uv
            ),
            highlights.map { it.resId }
        )
    }

    // --- Diurnal swing --------------------------------------------------------------------

    @Test
    fun `the evening layer reason becomes a swing highlight`() {
        val highlights = NotificationHighlights.of(
            recommendation(
                accessories = listOf(
                    alert(
                        AccessoryType.EXTRA_EVENING_LAYER,
                        AccessoryReason.DiurnalSwing(deltaCelsius = 9.0)
                    )
                )
            )
        )

        val swing = highlights.single()
        assertEquals(R.string.notification_highlight_swing, swing.resId)
        assertEquals(listOf(TextArg.Number(9.0, decimals = 0)), swing.args)
    }

    @Test
    fun `a plain day produces no highlights at all`() {
        assertTrue(NotificationHighlights.of(recommendation()).isEmpty())
    }

    // --- Fixture --------------------------------------------------------------------------

    private fun recommendation(
        rainReason: AccessoryReason? = null,
        accessories: List<AccessoryAlert> = emptyList(),
        maxWindKmh: Double = 0.0,
        outerLayer: Garment? = Garment(GarmentId.LIGHT_JACKET, LayerCategory.OUTER)
    ): OutfitRecommendation = OutfitRecommendation(
        headline = HeadlineReason.Mild,
        summary = SummaryData(
            tempMinCelsius = 12.0,
            tempMaxCelsius = 19.0,
            hasLargeTempSwing = false,
            rainReason = rainReason,
            peakUvIndex = 0.0
        ),
        baseLayer = Garment(GarmentId.COTTON_TEE, LayerCategory.BASE),
        midLayer = null,
        outerLayer = outerLayer,
        bottomLayer = Garment(GarmentId.CHINOS, LayerCategory.BOTTOM),
        footwear = Garment(GarmentId.SNEAKERS, LayerCategory.FOOTWEAR),
        accessories = accessories,
        weatherHighlights = WeatherHighlights(
            tempMinCelsius = 12.0,
            tempMaxCelsius = 19.0,
            deltaTCelsius = 7.0,
            maxPrecipitationProb = 60,
            maxUvIndex = 0.0,
            maxWindSpeedKmh = maxWindKmh,
            hasRainSpike = rainReason != null,
            hasLargeTempSwing = false
        )
    )

    private fun alert(
        type: AccessoryType,
        reason: AccessoryReason,
        priority: PriorityLevel = PriorityLevel.RECOMMENDED
    ): AccessoryAlert = AccessoryAlert(type = type, reason = reason, priority = priority)
}
