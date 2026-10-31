package com.smoh.showmeoutfit.domain.engine

import com.smoh.showmeoutfit.domain.model.AccessoryReason
import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.HeadlineReason
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class RuleBasedPresetEngineTest {

    private lateinit var engine: RuleBasedPresetEngine
    private val sampleLocation = LocationInfo(
        latitude = 48.8566,
        longitude = 2.3522,
        cityName = "Paris",
        country = "France"
    )

    @Before
    fun setUp() {
        engine = RuleBasedPresetEngine()
    }

    private fun buildSnapshot(
        hourlyList: List<HourlyCondition>,
        maxTemp: Double,
        minTemp: Double,
        maxPrecip: Int = 0,
        weatherCode: WeatherCode = WeatherCode.CLEAR_SKY
    ): WeatherSnapshot {
        val firstHour = hourlyList.firstOrNull()
        return WeatherSnapshot(
            location = sampleLocation,
            fetchedAt = Instant.now(),
            current = CurrentCondition(
                time = firstHour?.time ?: LocalDateTime.of(2026, 10, 24, 8, 0),
                temperatureCelsius = firstHour?.temperatureCelsius ?: minTemp,
                apparentTemperatureCelsius = firstHour?.apparentTemperatureCelsius ?: minTemp,
                humidityPercent = 60,
                weatherCode = firstHour?.weatherCode ?: weatherCode,
                windSpeedKmh = firstHour?.windSpeedKmh ?: 10.0
            ),
            hourlyForecast = hourlyList,
            dailyForecast = DailySummary(
                date = LocalDate.of(2026, 10, 24),
                maxTempCelsius = maxTemp,
                minTempCelsius = minTemp,
                maxPrecipitationProbability = maxPrecip,
                weatherCode = weatherCode
            )
        )
    }

    private fun hourly(
        temp: Double,
        apparent: Double = temp,
        precip: Int = 0,
        uv: Double = 2.0,
        wind: Double = 10.0,
        code: WeatherCode = WeatherCode.MAINLY_CLEAR,
        tempAt: (Int) -> Double = { temp }
    ): List<HourlyCondition> = (7..22).map { hour ->
        val value = tempAt(hour)
        HourlyCondition(
            time = LocalDateTime.of(2026, 10, 24, hour, 0),
            temperatureCelsius = value,
            apparentTemperatureCelsius = if (apparent == temp) value else apparent,
            precipitationProbability = precip,
            uvIndex = uv,
            windSpeedKmh = wind,
            weatherCode = code
        )
    }

    @Test
    fun `TC-ENG-01 large diurnal swing recommends a removable mid layer`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 18.0) { hour ->
                when (hour) {
                    in 7..9 -> 11.0
                    in 14..16 -> 24.0
                    else -> 18.0
                }
            },
            maxTemp = 24.0,
            minTemp = 11.0
        )

        val recommendation = engine.recommend(snapshot, UserProfile())

        assertTrue(recommendation.weatherHighlights.hasLargeTempSwing)
        assertEquals(13.0, recommendation.weatherHighlights.deltaTCelsius, 0.01)
        assertEquals(GarmentId.CARDIGAN, recommendation.midLayer?.id)
        assertEquals(HeadlineReason.LargeTempSwing(13.0), recommendation.headline)
        assertEquals(13.0, recommendation.summary.tempMaxCelsius - recommendation.summary.tempMinCelsius, 0.01)
        assertTrue(recommendation.accessories.any { it.type == AccessoryType.EXTRA_EVENING_LAYER })
    }

    @Test
    fun `TC-ENG-02 rain spike triggers a critical umbrella with a structured window`() {
        val snapshot = buildSnapshot(
            hourlyList = (7..22).map { hour ->
                val isRainHour = hour in 17..19
                HourlyCondition(
                    time = LocalDateTime.of(2026, 10, 24, hour, 0),
                    temperatureCelsius = 16.0,
                    apparentTemperatureCelsius = 16.0,
                    precipitationProbability = if (isRainHour) 75 else 0,
                    uvIndex = 1.0,
                    windSpeedKmh = 12.0,
                    weatherCode = if (isRainHour) WeatherCode.RAIN_MODERATE else WeatherCode.PARTLY_CLOUDY
                )
            },
            maxTemp = 16.0,
            minTemp = 16.0,
            maxPrecip = 75
        )

        val recommendation = engine.recommend(snapshot, UserProfile())
        val umbrella = recommendation.accessories.find { it.type == AccessoryType.UMBRELLA }

        assertTrue(recommendation.weatherHighlights.hasRainSpike)
        assertNotNull(umbrella)
        assertEquals(PriorityLevel.CRITICAL, umbrella!!.priority)
        assertEquals(
            AccessoryReason.RainWindow(LocalTime.of(17, 0), LocalTime.of(19, 0)),
            umbrella.reason
        )
        assertEquals(umbrella.reason, recommendation.summary.rainReason)
        assertEquals(GarmentId.WATERPROOF_SHOES, recommendation.footwear.id)
    }

    @Test
    fun `TC-ENG-03 extreme heat and UV recommends light layers and full UV protection`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 34.0, uv = 8.5, wind = 8.0, code = WeatherCode.CLEAR_SKY),
            maxTemp = 34.0,
            minTemp = 26.0
        )

        val recommendation = engine.recommend(snapshot, UserProfile())

        assertEquals(GarmentId.ULTRA_LIGHT_TANK, recommendation.baseLayer.id)
        assertEquals(GarmentId.SHORTS, recommendation.bottomLayer.id)
        assertEquals(GarmentId.SANDALS, recommendation.footwear.id)
        assertNull(recommendation.midLayer)
        assertNull(recommendation.outerLayer)
        assertTrue(recommendation.accessories.any { it.type == AccessoryType.SUNGLASSES })
        assertTrue(recommendation.accessories.any { it.type == AccessoryType.SUNSCREEN })
        assertEquals(
            PriorityLevel.CRITICAL,
            recommendation.accessories.first { it.type == AccessoryType.SUN_HAT }.priority
        )
        assertEquals(8.5, recommendation.summary.peakUvIndex, 0.01)
    }

    @Test
    fun `TC-ENG-04 cold with high wind upgrades to a windbreaker and adds a scarf`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(
                temp = 4.0,
                apparent = 2.0,
                precip = 10,
                uv = 1.0,
                wind = 45.0,
                code = WeatherCode.PARTLY_CLOUDY
            ),
            maxTemp = 5.0,
            minTemp = 2.0
        )

        val recommendation = engine.recommend(snapshot, UserProfile())

        assertEquals(GarmentId.WINDBREAKER, recommendation.outerLayer?.id)
        val scarf = recommendation.accessories.first { it.type == AccessoryType.SCARF }
        assertEquals(AccessoryReason.Wind(45), scarf.reason)
    }

    @Test
    fun `TC-ENG-05 feels-cold sensitivity offsets the temperature colder`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 17.0),
            maxTemp = 17.0,
            minTemp = 17.0
        )

        val recommendation = engine.recommend(
            snapshot,
            UserProfile(thermalSensitivity = ThermalSensitivity.FEELS_COLD_EASILY)
        )

        assertEquals(14.5, recommendation.weatherHighlights.tempMaxCelsius, 0.01)
        assertEquals(GarmentId.LIGHT_JACKET, recommendation.outerLayer?.id)
    }

    @Test
    fun `TC-ENG-06 feels-warm sensitivity offsets the temperature warmer`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 17.0),
            maxTemp = 17.0,
            minTemp = 17.0
        )

        val recommendation = engine.recommend(
            snapshot,
            UserProfile(thermalSensitivity = ThermalSensitivity.FEELS_WARM_EASILY)
        )

        assertEquals(19.5, recommendation.weatherHighlights.tempMaxCelsius, 0.01)
        assertNull(recommendation.outerLayer)
    }

    @Test
    fun `TC-ENG-07 sub-zero snow prescribes thermal layers and critical gloves and scarf`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(
                temp = -5.0,
                apparent = -8.0,
                precip = 80,
                uv = 0.5,
                wind = 20.0,
                code = WeatherCode.SNOW_FALL_MODERATE
            ),
            maxTemp = -3.0,
            minTemp = -7.0,
            weatherCode = WeatherCode.SNOW_FALL_MODERATE
        )

        val recommendation = engine.recommend(snapshot, UserProfile())

        assertEquals(GarmentId.THERMAL_UNDERSHIRT, recommendation.baseLayer.id)
        assertEquals(GarmentId.WOOL_SWEATER, recommendation.midLayer?.id)
        assertEquals(GarmentId.DOWN_PARKA, recommendation.outerLayer?.id)
        assertEquals(GarmentId.THERMAL_PANTS, recommendation.bottomLayer.id)
        assertEquals(GarmentId.WINTER_BOOTS, recommendation.footwear.id)
        assertEquals(HeadlineReason.SnowAndFrost, recommendation.headline)
        assertTrue(
            recommendation.accessories.any {
                it.type == AccessoryType.GLOVES && it.priority == PriorityLevel.CRITICAL
            }
        )
        assertTrue(
            recommendation.accessories.any {
                it.type == AccessoryType.SCARF && it.priority == PriorityLevel.CRITICAL
            }
        )
    }

    // --- Personalization: fit variants ---

    @Test
    fun `TC-ENG-08 gender preference changes fit on every layer without changing selections`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 17.0),
            maxTemp = 17.0,
            minTemp = 17.0
        )

        val unisex = engine.recommend(snapshot, UserProfile(genderPreference = GenderPreference.ALL))
        val men = engine.recommend(snapshot, UserProfile(genderPreference = GenderPreference.MEN))
        val women = engine.recommend(snapshot, UserProfile(genderPreference = GenderPreference.WOMEN))

        assertEquals(GarmentFit.UNISEX, unisex.baseLayer.fit)
        assertEquals(GarmentFit.MEN, men.baseLayer.fit)
        assertEquals(GarmentFit.MEN, men.footwear.fit)
        assertEquals(GarmentFit.WOMEN, women.baseLayer.fit)
        assertEquals(GarmentFit.WOMEN, women.footwear.fit)

        // Selections are identical; only the fit label differs.
        assertEquals(unisex.baseLayer.id, men.baseLayer.id)
        assertEquals(unisex.bottomLayer.id, women.bottomLayer.id)
        assertEquals(unisex.weatherHighlights, men.weatherHighlights)
    }

    // --- Personalization: age ---

    @Test
    fun `TC-ENG-09 age band buffer applies on top of thermal sensitivity`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 17.0),
            maxTemp = 17.0,
            minTemp = 17.0
        )

        val toddler = engine.recommend(
            snapshot,
            UserProfile(ageBand = AgeBand.CHILD_0_5)
        )
        val child = engine.recommend(
            snapshot,
            UserProfile(ageBand = AgeBand.CHILD_6_12)
        )
        val adult = engine.recommend(
            snapshot,
            UserProfile(ageBand = AgeBand.ADULT_20_39)
        )

        assertEquals(16.0, toddler.weatherHighlights.tempMaxCelsius, 0.01)
        assertEquals(16.5, child.weatherHighlights.tempMaxCelsius, 0.01)
        assertEquals(17.0, adult.weatherHighlights.tempMaxCelsius, 0.01)
    }

    @Test
    fun `TC-ENG-10 child escalation promotes sunscreen to critical at high UV`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 30.0, uv = 7.0, code = WeatherCode.CLEAR_SKY),
            maxTemp = 30.0,
            minTemp = 24.0
        )

        val adult = engine.recommend(snapshot, UserProfile(ageBand = AgeBand.ADULT_20_39))
        val senior = engine.recommend(snapshot, UserProfile(ageBand = AgeBand.SENIOR_60_PLUS))

        assertEquals(
            PriorityLevel.RECOMMENDED,
            adult.accessories.first { it.type == AccessoryType.SUNSCREEN }.priority
        )
        assertEquals(
            PriorityLevel.CRITICAL,
            senior.accessories.first { it.type == AccessoryType.SUNSCREEN }.priority
        )
    }

    @Test
    fun `TC-ENG-11 child escalation promotes the extra evening layer`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 18.0) { hour ->
                when (hour) {
                    in 7..9 -> 10.0
                    in 14..16 -> 24.0
                    else -> 18.0
                }
            },
            maxTemp = 24.0,
            minTemp = 10.0
        )

        val adult = engine.recommend(snapshot, UserProfile(ageBand = AgeBand.ADULT_20_39))
        val child = engine.recommend(snapshot, UserProfile(ageBand = AgeBand.CHILD_6_12))

        assertEquals(
            PriorityLevel.RECOMMENDED,
            adult.accessories.first { it.type == AccessoryType.EXTRA_EVENING_LAYER }.priority
        )
        assertEquals(
            PriorityLevel.CRITICAL,
            child.accessories.first { it.type == AccessoryType.EXTRA_EVENING_LAYER }.priority
        )
    }

    @Test
    fun `TC-ENG-12 neutral defaults reproduce the pre-personalization behaviour`() {
        val snapshot = buildSnapshot(
            hourlyList = hourly(temp = 18.0),
            maxTemp = 18.0,
            minTemp = 18.0
        )

        val recommendation = engine.recommend(snapshot, UserProfile())

        assertEquals(18.0, recommendation.weatherHighlights.tempMaxCelsius, 0.01)
        assertEquals(GarmentFit.UNISEX, recommendation.baseLayer.fit)
        assertEquals(HeadlineReason.Mild, recommendation.headline)
    }
}
