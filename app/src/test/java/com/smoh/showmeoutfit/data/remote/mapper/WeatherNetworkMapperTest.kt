package com.smoh.showmeoutfit.data.remote.mapper

import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoCurrentDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoDailyDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoForecastDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoGeocodingResultDto
import com.smoh.showmeoutfit.data.remote.dto.OpenMeteoHourlyDto
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Resilience guards for the wire -> domain mapping (SMO-S4-03).
 *
 * Open-Meteo replies are not guaranteed to be rectangular: arrays can be shorter than the time
 * axis, a block can be empty, and a geocoding hit can carry a placeholder name or a non-finite
 * coordinate. None of those may crash the app or degrade into the generic `Unknown` retry copy.
 */
class WeatherNetworkMapperTest {

    private val fetchedAt = Instant.parse("2026-10-24T09:00:00Z")

    private val berlin = LocationInfo(
        latitude = 52.52,
        longitude = 13.405,
        cityName = "Berlin",
        country = "Germany",
        timezone = "Europe/Berlin"
    )

    private fun current(time: String = "2026-10-24T10:00") = OpenMeteoCurrentDto(
        time = time,
        temperature2m = 14.2,
        apparentTemperature = 12.1,
        relativeHumidity2m = 70,
        weatherCode = 2,
        windSpeed10m = 18.0
    )

    private fun daily(
        time: List<String> = listOf("2026-10-24"),
        max: List<Double> = listOf(16.1),
        min: List<Double> = listOf(9.5),
        precip: List<Int> = listOf(20),
        codes: List<Int> = listOf(2)
    ) = OpenMeteoDailyDto(
        time = time,
        temperature2mMax = max,
        temperature2mMin = min,
        precipitationProbabilityMax = precip,
        weatherCode = codes
    )

    private fun hourly(
        time: List<String> = listOf("2026-10-24T07:00", "2026-10-24T08:00"),
        temperature: List<Double> = listOf(10.5, 11.2),
        apparent: List<Double> = listOf(8.5, 9.2),
        precip: List<Int> = listOf(5, 10),
        uv: List<Double> = listOf(0.5, 1.2),
        wind: List<Double> = listOf(15.0, 16.0),
        codes: List<Int> = listOf(2, 61)
    ) = OpenMeteoHourlyDto(
        time = time,
        temperature2m = temperature,
        apparentTemperature = apparent,
        precipitationProbability = precip,
        uvIndex = uv,
        windSpeed10m = wind,
        weatherCode = codes
    )

    private fun payload(
        hourly: OpenMeteoHourlyDto = hourly(),
        daily: OpenMeteoDailyDto = daily(),
        current: OpenMeteoCurrentDto = current()
    ) = OpenMeteoForecastDto(
        latitude = 52.52,
        longitude = 13.405,
        timezone = "Europe/Berlin",
        current = current,
        hourly = hourly,
        daily = daily
    )

    // --- Normal payload -------------------------------------------------------------------

    @Test
    fun `a complete payload maps every field in wire order`() {
        val snapshot = WeatherNetworkMapper.mapToDomain(payload(), berlin, fetchedAt)

        assertEquals(berlin, snapshot.location)
        assertEquals(fetchedAt, snapshot.fetchedAt)
        assertEquals(LocalDateTime.of(2026, 10, 24, 10, 0), snapshot.current.time)
        assertEquals(14.2, snapshot.current.temperatureCelsius, 0.0)
        assertEquals(WeatherCode.PARTLY_CLOUDY, snapshot.current.weatherCode)

        assertEquals(2, snapshot.hourlyForecast.size)
        assertEquals(
            listOf(LocalDateTime.of(2026, 10, 24, 7, 0), LocalDateTime.of(2026, 10, 24, 8, 0)),
            snapshot.hourlyForecast.map { it.time }
        )
        assertEquals(listOf(10.5, 11.2), snapshot.hourlyForecast.map { it.temperatureCelsius })
        assertEquals(listOf(0.5, 1.2), snapshot.hourlyForecast.map { it.uvIndex })
        assertEquals(WeatherCode.RAIN_SLIGHT, snapshot.hourlyForecast[1].weatherCode)

        assertEquals(LocalDate.of(2026, 10, 24), snapshot.dailyForecast.date)
        assertEquals(16.1, snapshot.dailyForecast.maxTempCelsius, 0.0)
        assertEquals(9.5, snapshot.dailyForecast.minTempCelsius, 0.0)
        assertEquals(20, snapshot.dailyForecast.maxPrecipitationProbability)
    }

    // --- Sparse hourly --------------------------------------------------------------------

    @Test
    fun `a shorter hourly array truncates to the shortest column instead of crashing`() {
        val sparse = payload(
            hourly = hourly(
                time = listOf("2026-10-24T07:00", "2026-10-24T08:00", "2026-10-24T09:00"),
                temperature = listOf(10.0, 11.0, 12.0),
                apparent = listOf(9.0, 10.0, 11.0),
                precip = listOf(5, 10, 15),
                uv = listOf(0.5, 1.0),
                wind = listOf(15.0, 16.0, 17.0),
                codes = listOf(2, 2, 2)
            )
        )

        val snapshot = WeatherNetworkMapper.mapToDomain(sparse, berlin, fetchedAt)

        assertEquals(2, snapshot.hourlyForecast.size)
        assertEquals(listOf(10.0, 11.0), snapshot.hourlyForecast.map { it.temperatureCelsius })
        // The daily block is independent and still maps normally.
        assertEquals(LocalDate.of(2026, 10, 24), snapshot.dailyForecast.date)
    }

    @Test
    fun `an hour column that is empty yields no hourly entries rather than a failure`() {
        val snapshot = WeatherNetworkMapper.mapToDomain(
            payload(hourly = hourly(uv = emptyList())),
            berlin,
            fetchedAt
        )

        assertTrue(snapshot.hourlyForecast.isEmpty())
        assertEquals(16.1, snapshot.dailyForecast.maxTempCelsius, 0.0)
    }

    // --- Daily failure modes --------------------------------------------------------------

    @Test
    fun `a payload without a daily entry fails as MalformedData`() {
        val empty = payload(
            daily = daily(
                time = emptyList(),
                max = emptyList(),
                min = emptyList(),
                precip = emptyList(),
                codes = emptyList()
            )
        )

        val thrown = assertThrows(DomainError.MalformedData::class.java) {
            WeatherNetworkMapper.mapToDomain(empty, berlin, fetchedAt)
        }

        assertTrue(thrown.message?.isNotEmpty() == true)
    }

    @Test
    fun `a daily block with one missing column fails as MalformedData`() {
        // `time` has a day, but the temperatures do not: there is no complete entry to read.
        val truncated = payload(daily = daily(max = emptyList()))

        assertThrows(DomainError.MalformedData::class.java) {
            WeatherNetworkMapper.mapToDomain(truncated, berlin, fetchedAt)
        }
    }

    // --- Parse failures -------------------------------------------------------------------

    @Test
    fun `a malformed hourly timestamp fails as MalformedData, not Unknown`() {
        val malformed = payload(
            hourly = hourly(time = listOf("2026-10-24T07:00", "not-a-timestamp"))
        )

        val thrown = assertThrows(DomainError.MalformedData::class.java) {
            WeatherNetworkMapper.mapToDomain(malformed, berlin, fetchedAt)
        }

        assertTrue(thrown.message?.isNotEmpty() == true)
    }

    @Test
    fun `a malformed current timestamp fails as MalformedData`() {
        val malformed = payload(current = current(time = "24/10/2026 10:00"))

        assertThrows(DomainError.MalformedData::class.java) {
            WeatherNetworkMapper.mapToDomain(malformed, berlin, fetchedAt)
        }
    }

    @Test
    fun `a malformed daily date fails as MalformedData`() {
        val malformed = payload(daily = daily(time = listOf("2026-13-45")))

        assertThrows(DomainError.MalformedData::class.java) {
            WeatherNetworkMapper.mapToDomain(malformed, berlin, fetchedAt)
        }
    }

    // --- Geocoding ------------------------------------------------------------------------

    private fun geocodingHit(
        name: String = "Paris",
        latitude: Double = 48.8534,
        longitude: Double = 2.3488,
        country: String? = "France",
        admin1: String? = "Île-de-France",
        timezone: String? = "Europe/Paris"
    ) = OpenMeteoGeocodingResultDto(
        id = 2988507,
        name = name,
        latitude = latitude,
        longitude = longitude,
        country = country,
        admin1 = admin1,
        timezone = timezone
    )

    @Test
    fun `unusable geocoding hits are filtered out and the rest keep their order`() {
        val hits = listOf(
            geocodingHit(name = "Paris"),
            geocodingHit(name = "   "),
            geocodingHit(name = "NaN city", latitude = Double.NaN),
            geocodingHit(name = "Infinite city", longitude = Double.POSITIVE_INFINITY),
            geocodingHit(name = "Too far north", latitude = 95.0),
            geocodingHit(name = "Too far west", longitude = -181.0),
            geocodingHit(name = "Lyon", latitude = 45.7640, longitude = 4.8357)
        )

        val mapped = WeatherNetworkMapper.mapGeocodingResults(hits)

        assertEquals(listOf("Paris", "Lyon"), mapped.map { it.cityName })
        assertEquals(48.8534, mapped.first().latitude, 0.0)
        assertEquals(4.8357, mapped.last().longitude, 0.0)
    }

    @Test
    fun `a single invalid geocoding hit maps to null`() {
        assertNull(WeatherNetworkMapper.mapGeocodingResultToDomain(geocodingHit(name = "")))
        assertNull(
            WeatherNetworkMapper.mapGeocodingResultToDomain(
                geocodingHit(latitude = Double.NEGATIVE_INFINITY)
            )
        )
        assertNull(WeatherNetworkMapper.mapGeocodingResultToDomain(geocodingHit(longitude = 180.5)))
    }

    @Test
    fun `blank optional geocoding metadata stays nullable`() {
        val mapped = WeatherNetworkMapper.mapGeocodingResultToDomain(
            geocodingHit(name = "  Paris  ", country = "  ", admin1 = null, timezone = "")
        )

        assertEquals("Paris", mapped?.cityName)
        assertNull(mapped?.country)
        assertNull(mapped?.adminRegion)
        assertNull(mapped?.timezone)
    }
}
