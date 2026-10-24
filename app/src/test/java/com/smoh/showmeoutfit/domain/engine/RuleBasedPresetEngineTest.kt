package com.smoh.showmeoutfit.domain.engine

import com.smoh.showmeoutfit.domain.model.AccessoryType
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LayerCategory
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

    @Test
    fun `TC-ENG-01 High Diurnal Swing recommends removable mid layer and highlights temperature swing`() {
        // Morning 11°C, Afternoon 24°C (deltaT = 13°C)
        val hourly = (7..22).map { hour ->
            val temp = if (hour in 7..9) 11.0 else if (hour in 14..16) 24.0 else 18.0
            HourlyCondition(
                time = LocalDateTime.of(2026, 10, 24, hour, 0),
                temperatureCelsius = temp,
                apparentTemperatureCelsius = temp,
                precipitationProbability = 5,
                uvIndex = 2.0,
                windSpeedKmh = 10.0,
                weatherCode = WeatherCode.MAINLY_CLEAR
            )
        }
        val snapshot = buildSnapshot(hourly, maxTemp = 24.0, minTemp = 11.0)
        val recommendation = engine.recommend(snapshot, UserProfile(thermalSensitivity = ThermalSensitivity.NEUTRAL))

        assertTrue(recommendation.weatherHighlights.hasLargeTempSwing)
        assertEquals(13.0, recommendation.weatherHighlights.deltaTCelsius, 0.01)
        assertNotNull("Mid layer should be present for high swing", recommendation.midLayer)
        assertEquals(GarmentCatalog.CARDIGAN, recommendation.midLayer)
        assertTrue(recommendation.headline.contains("Swing"))
        assertTrue(recommendation.accessories.any { it.type == AccessoryType.EXTRA_EVENING_LAYER })
    }

    @Test
    fun `TC-ENG-02 Rain Spike triggers Critical Umbrella and identifies time window`() {
        // 0% rain except 17:00 - 19:00 (75%)
        val hourly = (7..22).map { hour ->
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
        }
        val snapshot = buildSnapshot(hourly, maxTemp = 16.0, minTemp = 16.0, maxPrecip = 75)
        val recommendation = engine.recommend(snapshot, UserProfile())

        assertTrue(recommendation.weatherHighlights.hasRainSpike)
        val umbrellaAlert = recommendation.accessories.find { it.type == AccessoryType.UMBRELLA }
        assertNotNull("Umbrella alert must be generated", umbrellaAlert)
        assertEquals(PriorityLevel.CRITICAL, umbrellaAlert!!.priority)
        assertTrue("Reason should include rain window", umbrellaAlert.reason.contains("17:00") && umbrellaAlert.reason.contains("19:00"))
        assertEquals(GarmentCatalog.WATERPROOF_SHOES, recommendation.footwear)
    }

    @Test
    fun `TC-ENG-03 Extreme Heat and UV recommends light linen, shorts, and full UV protection accessories`() {
        // 34°C, UV 8.5
        val hourly = (7..22).map { hour ->
            HourlyCondition(
                time = LocalDateTime.of(2026, 10, 24, hour, 0),
                temperatureCelsius = 34.0,
                apparentTemperatureCelsius = 34.0,
                precipitationProbability = 0,
                uvIndex = 8.5,
                windSpeedKmh = 8.0,
                weatherCode = WeatherCode.CLEAR_SKY
            )
        }
        val snapshot = buildSnapshot(hourly, maxTemp = 34.0, minTemp = 26.0)
        val recommendation = engine.recommend(snapshot, UserProfile())

        assertEquals(GarmentCatalog.ULTRA_LIGHT_TANK, recommendation.baseLayer)
        assertEquals(GarmentCatalog.SHORTS, recommendation.bottomLayer)
        assertEquals(GarmentCatalog.SANDALS, recommendation.footwear)
        assertNull(recommendation.midLayer)
        assertNull(recommendation.outerLayer)

        assertTrue(recommendation.accessories.any { it.type == AccessoryType.SUNGLASSES })
        assertTrue(recommendation.accessories.any { it.type == AccessoryType.SUNSCREEN })
        val sunHat = recommendation.accessories.find { it.type == AccessoryType.SUN_HAT }
        assertNotNull("Sun hat required for UV >= 6.0", sunHat)
        assertEquals(PriorityLevel.CRITICAL, sunHat!!.priority)
    }

    @Test
    fun `TC-ENG-04 Cold with High Wind upgrades outer layer to Windbreaker and adds Scarf`() {
        // 4°C, Wind 45 km/h
        val hourly = (7..22).map { hour ->
            HourlyCondition(
                time = LocalDateTime.of(2026, 10, 24, hour, 0),
                temperatureCelsius = 4.0,
                apparentTemperatureCelsius = 2.0,
                precipitationProbability = 10,
                uvIndex = 1.0,
                windSpeedKmh = 45.0,
                weatherCode = WeatherCode.PARTLY_CLOUDY
            )
        }
        val snapshot = buildSnapshot(hourly, maxTemp = 5.0, minTemp = 2.0)
        val recommendation = engine.recommend(snapshot, UserProfile())

        assertEquals(GarmentCatalog.WINDBREAKER, recommendation.outerLayer)
        assertTrue(recommendation.accessories.any { it.type == AccessoryType.SCARF })
    }

    @Test
    fun `TC-ENG-05 Thermal Sensitivity FEELS_COLD_EASILY offsets temperature colder requiring warmer clothing`() {
        // 17°C actual temperature
        val hourly = (7..22).map { hour ->
            HourlyCondition(
                time = LocalDateTime.of(2026, 10, 24, hour, 0),
                temperatureCelsius = 17.0,
                apparentTemperatureCelsius = 17.0,
                precipitationProbability = 0,
                uvIndex = 2.0,
                windSpeedKmh = 10.0,
                weatherCode = WeatherCode.MAINLY_CLEAR
            )
        }
        val snapshot = buildSnapshot(hourly, maxTemp = 17.0, minTemp = 17.0)

        val coldProfile = UserProfile(thermalSensitivity = ThermalSensitivity.FEELS_COLD_EASILY)
        val recommendation = engine.recommend(snapshot, coldProfile)

        // 17°C - 2.5°C = 14.5°C (Cool bracket: < 16°C, requires outer Light Jacket or Hoodie)
        assertEquals(14.5, recommendation.weatherHighlights.tempMaxCelsius, 0.01)
        assertEquals(GarmentCatalog.LIGHT_JACKET, recommendation.outerLayer)
    }

    @Test
    fun `TC-ENG-06 Thermal Sensitivity FEELS_WARM_EASILY offsets temperature warmer requiring lighter clothing`() {
        // 17°C actual temperature
        val hourly = (7..22).map { hour ->
            HourlyCondition(
                time = LocalDateTime.of(2026, 10, 24, hour, 0),
                temperatureCelsius = 17.0,
                apparentTemperatureCelsius = 17.0,
                precipitationProbability = 0,
                uvIndex = 2.0,
                windSpeedKmh = 10.0,
                weatherCode = WeatherCode.MAINLY_CLEAR
            )
        }
        val snapshot = buildSnapshot(hourly, maxTemp = 17.0, minTemp = 17.0)

        val warmProfile = UserProfile(thermalSensitivity = ThermalSensitivity.FEELS_WARM_EASILY)
        val recommendation = engine.recommend(snapshot, warmProfile)

        // 17°C - (-2.5°C) = 19.5°C (Mild bracket: 16-22°C, no heavy outer layer)
        assertEquals(19.5, recommendation.weatherHighlights.tempMaxCelsius, 0.01)
        assertNull(recommendation.outerLayer)
    }

    @Test
    fun `TC-ENG-07 Sub-zero Frost and Snow codes prescribe thermal undershirt, parka, winter boots, and gloves`() {
        // -5°C with Snow Fall (code 73)
        val hourly = (7..22).map { hour ->
            HourlyCondition(
                time = LocalDateTime.of(2026, 10, 24, hour, 0),
                temperatureCelsius = -5.0,
                apparentTemperatureCelsius = -8.0,
                precipitationProbability = 80,
                uvIndex = 0.5,
                windSpeedKmh = 20.0,
                weatherCode = WeatherCode.SNOW_FALL_MODERATE
            )
        }
        val snapshot = buildSnapshot(hourly, maxTemp = -3.0, minTemp = -7.0, weatherCode = WeatherCode.SNOW_FALL_MODERATE)
        val recommendation = engine.recommend(snapshot, UserProfile())

        assertEquals(GarmentCatalog.THERMAL_UNDERSHIRT, recommendation.baseLayer)
        assertEquals(GarmentCatalog.WOOL_SWEATER, recommendation.midLayer)
        assertEquals(GarmentCatalog.DOWN_PARKA, recommendation.outerLayer)
        assertEquals(GarmentCatalog.THERMAL_PANTS, recommendation.bottomLayer)
        assertEquals(GarmentCatalog.WINTER_BOOTS, recommendation.footwear)
        assertTrue(recommendation.accessories.any { it.type == AccessoryType.GLOVES && it.priority == PriorityLevel.CRITICAL })
        assertTrue(recommendation.accessories.any { it.type == AccessoryType.SCARF && it.priority == PriorityLevel.CRITICAL })
    }
}
