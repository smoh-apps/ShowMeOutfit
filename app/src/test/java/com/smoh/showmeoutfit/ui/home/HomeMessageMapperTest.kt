package com.smoh.showmeoutfit.ui.home

import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.ui.components.TimelinePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** Guards for the non-recommendation copy (provenance, errors) and the timeline window. */
class HomeMessageMapperTest {

    @Test
    fun `every domain error maps to distinct localized copy`() {
        val errors = listOf(
            DomainError.NoLocation(),
            DomainError.Offline(),
            DomainError.ApiFailure(),
            DomainError.MalformedData(),
            DomainError.CacheExpired(),
            DomainError.Unknown()
        )

        val resources = errors.map { HomeMessageMapper.errorMessageRes(it) }

        assertTrue("zero error resource", resources.none { it == 0 })
        assertEquals("each subtype needs its own message", errors.size, resources.distinct().size)
    }

    @Test
    fun `unknown errors never surface the exception message`() {
        // The mapper only receives the type, so a vendor-specific payload cannot leak.
        val secret = DomainError.Unknown(IllegalStateException("db://secret-host:5432"))
        val mapped = HomeMessageMapper.errorMessageRes(secret)

        assertEquals(HomeMessageMapper.errorMessageRes(DomainError.Unknown()), mapped)
    }

    @Test
    fun `network origin has no banner and both cache origins do`() {
        assertNull(HomeMessageMapper.cacheBannerWithAgeRes(WeatherOrigin.NETWORK))
        assertNull(HomeMessageMapper.cacheBannerWithoutAgeRes(WeatherOrigin.NETWORK))

        val fresh = HomeMessageMapper.cacheBannerWithAgeRes(WeatherOrigin.CACHE_FRESH)
        val stale = HomeMessageMapper.cacheBannerWithAgeRes(WeatherOrigin.CACHE_STALE)

        assertTrue(fresh != null && fresh != 0)
        assertTrue(stale != null && stale != 0)
        assertNotEquals("fresh and stale must read differently", fresh, stale)
        assertNotEquals(
            HomeMessageMapper.cacheBannerWithoutAgeRes(WeatherOrigin.CACHE_FRESH),
            HomeMessageMapper.cacheBannerWithoutAgeRes(WeatherOrigin.CACHE_STALE)
        )
    }

    @Test
    fun `cache age splits into hours and minutes`() {
        assertEquals(CacheAge(hours = 5, minutes = 20), CacheAge.of(Duration.ofHours(5).plusMinutes(20)))
        assertEquals(CacheAge(hours = 3, minutes = 0), CacheAge.of(Duration.ofHours(3)))
        assertEquals(CacheAge(hours = 0, minutes = 25), CacheAge.of(Duration.ofMinutes(25)))
        assertEquals(CacheAge(hours = 0, minutes = 59), CacheAge.of(Duration.ofSeconds(3_599)))
        assertEquals(CacheAge(hours = 1, minutes = 0), CacheAge.of(Duration.ofMinutes(60)))
    }

    @Test
    fun `cache age never goes negative or crashes without a value`() {
        assertEquals(CacheAge(hours = 0, minutes = 0), CacheAge.of(Duration.ofMinutes(-30)))
        assertEquals(CacheAge(hours = 0, minutes = 0), CacheAge.of(null))
        assertTrue(CacheAge.of(Duration.ofMinutes(42)).isUnderOneHour)
        assertTrue(!CacheAge.of(Duration.ofMinutes(90)).isUnderOneHour)
    }

    // --- Timeline window -------------------------------------------------------------------

    private fun hourly(hour: Int, temp: Double = 15.0): HourlyCondition = HourlyCondition(
        time = LocalDateTime.of(LocalDate.of(2026, 10, 28), LocalTime.of(hour, 0)),
        temperatureCelsius = temp,
        apparentTemperatureCelsius = temp,
        precipitationProbability = hour,
        uvIndex = hour / 10.0,
        windSpeedKmh = 10.0,
        weatherCode = WeatherCode.PARTLY_CLOUDY
    )

    private val label: (LocalTime) -> String = { time -> "%02d:00".format(time.hour) }

    @Test
    fun `timeline covers 07 to 22 inclusive and nothing else`() {
        val points = HomeTimelineMapper.points((0..23).map { hourly(it) }, label)

        assertEquals(16, points.size)
        assertEquals("07:00", points.first().hourLabel)
        assertEquals("22:00", points.last().hourLabel)
        assertTrue(points.none { it.hourLabel == "06:00" || it.hourLabel == "23:00" })
    }

    @Test
    fun `timeline maps the hourly values into the chart points`() {
        val points = HomeTimelineMapper.points(
            listOf(
                HourlyCondition(
                    time = LocalDateTime.of(LocalDate.of(2026, 10, 28), LocalTime.of(13, 0)),
                    temperatureCelsius = 21.5,
                    apparentTemperatureCelsius = 22.0,
                    precipitationProbability = 40,
                    uvIndex = 6.4,
                    windSpeedKmh = 12.0,
                    weatherCode = WeatherCode.CLEAR_SKY
                )
            ),
            label
        )

        assertEquals(
            listOf(TimelinePoint(hourLabel = "13:00", temperatureC = 21.5, precipitationProbability = 40, uvIndex = 6.4)),
            points
        )
    }

    @Test
    fun `timeline is sorted and de-duplicated by hour`() {
        val unordered = listOf(hourly(18, temp = 18.0), hourly(7, temp = 12.0), hourly(18, temp = 99.0))
        val points = HomeTimelineMapper.points(unordered, label)

        assertEquals(listOf("07:00", "18:00"), points.map { it.hourLabel })
        assertEquals(18.0, points.last().temperatureC, 0.0)
    }

    @Test
    fun `an empty or night-only forecast yields no points`() {
        assertEquals(emptyList<TimelinePoint>(), HomeTimelineMapper.points(emptyList(), label))
        assertEquals(
            emptyList<TimelinePoint>(),
            HomeTimelineMapper.points(listOf(hourly(2), hourly(23)), label)
        )
    }

    // --- TextArg formatting ----------------------------------------------------------------

    @Test
    fun `text arguments format numbers and times deterministically`() {
        assertEquals("9", TextArg.Number(9.4, decimals = 0).format(java.util.Locale.US))
        assertEquals("6.2", TextArg.Number(6.24, decimals = 1).format(java.util.Locale.US))
        assertEquals("55", TextArg.Count(55).format(java.util.Locale.US))
        assertEquals("15:00", TextArg.Clock(LocalTime.of(15, 0)).format(java.util.Locale.US))
        assertEquals("07:05", TextArg.Clock(LocalTime.of(7, 5)).format(java.util.Locale.US))
    }

    @Test
    fun `unexpected io failures are typed before they reach the mapper`() {
        // Repositories wrap transport failures; the UI must still resolve a resource id for them.
        val wrapped = IOException("timeout").let { DomainError.Offline(it) }
        assertTrue(HomeMessageMapper.errorMessageRes(wrapped) != 0)
    }
}
