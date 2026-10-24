package com.smoh.showmeoutfit.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

class DomainModelsTest {

    @Test
    fun `WeatherCode correctly identifies precipitation and rain`() {
        val rainSlight = WeatherCode.fromCode(61)
        assertEquals(WeatherCode.RAIN_SLIGHT, rainSlight)
        assertTrue(rainSlight.isPrecipitation)
        assertTrue(rainSlight.isRain)
        assertFalse(rainSlight.isSnow)

        val snowHeavy = WeatherCode.fromCode(75)
        assertEquals(WeatherCode.SNOW_FALL_HEAVY, snowHeavy)
        assertTrue(snowHeavy.isPrecipitation)
        assertTrue(snowHeavy.isSnow)
        assertFalse(snowHeavy.isRain)

        val clearSky = WeatherCode.fromCode(0)
        assertEquals(WeatherCode.CLEAR_SKY, clearSky)
        assertFalse(clearSky.isPrecipitation)
        assertFalse(clearSky.isRain)
        assertFalse(clearSky.isSnow)

        val unknown = WeatherCode.fromCode(999)
        assertEquals(WeatherCode.UNKNOWN, unknown)
        assertFalse(unknown.isPrecipitation)
    }

    @Test
    fun `ThermalSensitivity offsets match specifications`() {
        assertEquals(+2.5, ThermalSensitivity.FEELS_COLD_EASILY.tempOffsetCelsius, 0.001)
        assertEquals(0.0, ThermalSensitivity.NEUTRAL.tempOffsetCelsius, 0.001)
        assertEquals(-2.5, ThermalSensitivity.FEELS_WARM_EASILY.tempOffsetCelsius, 0.001)
    }

    @Test
    fun `UserProfile default values match product requirements`() {
        val defaultProfile = UserProfile()
        assertEquals(GenderPreference.ALL, defaultProfile.genderPreference)
        assertEquals(ThermalSensitivity.NEUTRAL, defaultProfile.thermalSensitivity)
        assertEquals(LocalTime.of(7, 0), defaultProfile.morningNotificationTime)
        assertTrue(defaultProfile.isNotificationEnabled)
        assertEquals(null, defaultProfile.selectedLocation)
    }

    @Test
    fun `WeatherSnapshot holds consistent daily and hourly domain data`() {
        val location = LocationInfo(
            latitude = 52.52,
            longitude = 13.405,
            cityName = "Berlin",
            country = "Germany"
        )
        val now = LocalDateTime.of(2026, 10, 24, 8, 0)
        val snapshot = WeatherSnapshot(
            location = location,
            fetchedAt = Instant.now(),
            current = CurrentCondition(
                time = now,
                temperatureCelsius = 12.0,
                apparentTemperatureCelsius = 10.5,
                humidityPercent = 75,
                weatherCode = WeatherCode.PARTLY_CLOUDY,
                windSpeedKmh = 14.0
            ),
            hourlyForecast = listOf(
                HourlyCondition(
                    time = now,
                    temperatureCelsius = 12.0,
                    apparentTemperatureCelsius = 10.5,
                    precipitationProbability = 10,
                    uvIndex = 1.2,
                    windSpeedKmh = 14.0,
                    weatherCode = WeatherCode.PARTLY_CLOUDY
                )
            ),
            dailyForecast = DailySummary(
                date = LocalDate.of(2026, 10, 24),
                maxTempCelsius = 18.0,
                minTempCelsius = 9.0,
                maxPrecipitationProbability = 20,
                weatherCode = WeatherCode.PARTLY_CLOUDY
            )
        )

        assertEquals("Berlin", snapshot.location.cityName)
        assertEquals(1, snapshot.hourlyForecast.size)
        assertEquals(18.0, snapshot.dailyForecast.maxTempCelsius, 0.01)
    }
}
