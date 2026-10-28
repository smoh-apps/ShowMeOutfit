package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.data.local.datasource.LocalWeatherDataSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlin.coroutines.cancellation.CancellationException

class WeatherRepositoryImplTest {

    private lateinit var fakeRemote: FakeRemoteWeatherDataSource
    private lateinit var fakeLocal: FakeLocalWeatherDataSource
    private lateinit var repository: WeatherRepositoryImpl

    private val fixedNow: Instant = Instant.parse("2026-10-24T10:00:00Z")
    private val clock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)

    private val berlin = LocationInfo(
        latitude = 52.52,
        longitude = 13.405,
        cityName = "Berlin",
        country = "Germany",
        timezone = "Europe/Berlin"
    )
    private val paris = LocationInfo(
        latitude = 48.8566,
        longitude = 2.3522,
        cityName = "Paris",
        country = "France",
        timezone = "Europe/Paris"
    )

    @Before
    fun setUp() {
        fakeRemote = FakeRemoteWeatherDataSource()
        fakeLocal = FakeLocalWeatherDataSource()
        repository = WeatherRepositoryImpl(
            remoteDataSource = fakeRemote,
            localDataSource = fakeLocal,
            clock = clock
        )
    }

    private fun buildSnapshot(
        fetchedAt: Instant,
        temp: Double = 15.0,
        location: LocationInfo = berlin,
        forecastDate: LocalDate = LocalDate.of(2026, 10, 24)
    ): WeatherSnapshot {
        val hour = LocalDateTime.of(2026, 10, 24, 12, 0)
        return WeatherSnapshot(
            location = location,
            fetchedAt = fetchedAt,
            current = CurrentCondition(
                time = hour,
                temperatureCelsius = temp,
                apparentTemperatureCelsius = temp,
                humidityPercent = 60,
                weatherCode = WeatherCode.MAINLY_CLEAR,
                windSpeedKmh = 10.0
            ),
            hourlyForecast = listOf(
                HourlyCondition(
                    time = hour,
                    temperatureCelsius = temp,
                    apparentTemperatureCelsius = temp,
                    precipitationProbability = 0,
                    uvIndex = 2.0,
                    windSpeedKmh = 10.0,
                    weatherCode = WeatherCode.MAINLY_CLEAR
                )
            ),
            dailyForecast = DailySummary(
                date = forecastDate,
                maxTempCelsius = temp + 3.0,
                minTempCelsius = temp - 3.0,
                maxPrecipitationProbability = 0,
                weatherCode = WeatherCode.MAINLY_CLEAR
            )
        )
    }

    // --- Fresh cache ---

    @Test
    fun `fresh cache from the same location and day is used without a network call`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minusSeconds(3600), temp = 12.0))

        val result = repository.getTodayWeather(berlin).getOrThrow()

        assertEquals(WeatherOrigin.CACHE_FRESH, result.origin)
        assertEquals(Duration.ofHours(1), result.cacheAge)
        assertEquals(12.0, result.snapshot.current.temperatureCelsius, 0.01)
        assertEquals(0, fakeRemote.fetchCallCount)
    }

    @Test
    fun `cache from a different city is not reused even when fresh`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minusSeconds(3600), location = paris))
        fakeRemote.nextForecastResult = Result.success(buildSnapshot(fetchedAt = fixedNow))

        val result = repository.getTodayWeather(berlin).getOrThrow()

        assertEquals(WeatherOrigin.NETWORK, result.origin)
        assertEquals(1, fakeRemote.fetchCallCount)
    }

    @Test
    fun `cache from a previous day is not reused even when fresh`() = runTest {
        fakeLocal.save(
            buildSnapshot(
                fetchedAt = fixedNow.minusSeconds(3600),
                forecastDate = LocalDate.of(2026, 10, 23)
            )
        )
        fakeRemote.nextForecastResult = Result.success(buildSnapshot(fetchedAt = fixedNow))

        val result = repository.getTodayWeather(berlin).getOrThrow()

        assertEquals(WeatherOrigin.NETWORK, result.origin)
        assertEquals(1, fakeRemote.fetchCallCount)
    }

    @Test
    fun `cache exactly at the three hour boundary is not fresh`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minus(Duration.ofHours(3))))
        fakeRemote.nextForecastResult = Result.success(buildSnapshot(fetchedAt = fixedNow, temp = 16.0))

        val result = repository.getTodayWeather(berlin).getOrThrow()

        assertEquals(WeatherOrigin.NETWORK, result.origin)
        assertEquals(16.0, result.snapshot.current.temperatureCelsius, 0.01)
        assertEquals(1, fakeRemote.fetchCallCount)
    }

    @Test
    fun `cache newer than three hours triggers a network fetch and is overwritten`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minus(Duration.ofHours(4)), temp = 10.0))
        fakeRemote.nextForecastResult = Result.success(buildSnapshot(fetchedAt = fixedNow, temp = 16.0))

        val result = repository.getTodayWeather(berlin).getOrThrow()

        assertEquals(WeatherOrigin.NETWORK, result.origin)
        assertEquals(1, fakeRemote.fetchCallCount)
        assertEquals(16.0, fakeLocal.getLatest()!!.current.temperatureCelsius, 0.01)
    }

    @Test
    fun `forceRefresh ignores a fresh cache`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minusSeconds(1800), temp = 14.0))
        fakeRemote.nextForecastResult = Result.success(buildSnapshot(fetchedAt = fixedNow, temp = 18.0))

        val result = repository.getTodayWeather(berlin, forceRefresh = true).getOrThrow()

        assertEquals(WeatherOrigin.NETWORK, result.origin)
        assertEquals(18.0, result.snapshot.current.temperatureCelsius, 0.01)
        assertEquals(1, fakeRemote.fetchCallCount)
    }

    @Test
    fun `a future fetchedAt is treated as unusable`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.plusSeconds(3600)))
        fakeRemote.nextForecastResult = Result.success(buildSnapshot(fetchedAt = fixedNow))

        val result = repository.getTodayWeather(berlin).getOrThrow()

        assertEquals(WeatherOrigin.NETWORK, result.origin)
        assertEquals(1, fakeRemote.fetchCallCount)
    }

    // --- Stale fallback ---

    @Test
    fun `network failure falls back to a six hour old cache for the same city and day`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minus(Duration.ofHours(6)), temp = 13.5))
        fakeRemote.nextForecastResult = Result.failure(IOException("No internet connection"))

        val result = repository.getTodayWeather(berlin).getOrThrow()

        assertEquals(WeatherOrigin.CACHE_STALE, result.origin)
        assertEquals(Duration.ofHours(6), result.cacheAge)
        assertEquals(13.5, result.snapshot.current.temperatureCelsius, 0.01)
        assertEquals(1, fakeRemote.fetchCallCount)
    }

    @Test
    fun `network failure never falls back to another city's cache`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minus(Duration.ofHours(1)), location = paris))
        fakeRemote.nextForecastResult = Result.failure(IOException("No internet connection"))

        val result = repository.getTodayWeather(berlin)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is DomainError.CacheExpired)
    }

    @Test
    fun `network failure never falls back to yesterday's forecast`() = runTest {
        fakeLocal.save(
            buildSnapshot(
                fetchedAt = fixedNow.minus(Duration.ofHours(1)),
                forecastDate = LocalDate.of(2026, 10, 23)
            )
        )
        fakeRemote.nextForecastResult = Result.failure(IOException("No internet connection"))

        val result = repository.getTodayWeather(berlin)

        assertTrue(result.exceptionOrNull() is DomainError.CacheExpired)
    }

    @Test
    fun `network failure rejects a cache older than twelve hours`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minus(Duration.ofHours(13))))
        fakeRemote.nextForecastResult = Result.failure(IOException("No internet connection"))

        val result = repository.getTodayWeather(berlin)

        assertTrue(result.exceptionOrNull() is DomainError.CacheExpired)
    }

    // --- Typed errors ---

    @Test
    fun `offline failure without cache maps to DomainError Offline`() = runTest {
        fakeRemote.nextForecastResult = Result.failure(IOException("Network unreachable"))

        val result = repository.getTodayWeather(berlin)

        assertTrue(result.exceptionOrNull() is DomainError.Offline)
    }

    @Test
    fun `malformed payload maps to DomainError MalformedData`() = runTest {
        fakeRemote.nextForecastResult = Result.failure(SerializationException("bad json"))

        val result = repository.getTodayWeather(berlin)

        assertTrue(result.exceptionOrNull() is DomainError.MalformedData)
    }

    @Test
    fun `unexpected failure maps to DomainError Unknown`() = runTest {
        fakeRemote.nextForecastResult = Result.failure(IllegalStateException("boom"))

        val result = repository.getTodayWeather(berlin)

        assertTrue(result.exceptionOrNull() is DomainError.Unknown)
    }

    @Test
    fun `cancellation is rethrown instead of being wrapped`() = runTest {
        fakeRemote.nextForecastResult = Result.failure(CancellationException("cancelled"))

        val caught = runCatching { repository.getTodayWeather(berlin) }

        assertTrue(caught.exceptionOrNull() is CancellationException)
    }

    // --- Retention ---

    @Test
    fun `successful fetch prunes rows older than the retention window`() = runTest {
        fakeRemote.nextForecastResult = Result.success(buildSnapshot(fetchedAt = fixedNow))

        repository.getTodayWeather(berlin).getOrThrow()

        assertEquals(
            fixedNow.minus(Duration.ofDays(7)).toEpochMilli(),
            fakeLocal.lastPruneThresholdMs
        )
    }

    @Test
    fun `a failing prune does not fail an otherwise successful fetch`() = runTest {
        fakeRemote.nextForecastResult = Result.success(buildSnapshot(fetchedAt = fixedNow))
        fakeLocal.failPrune = true

        val result = repository.getTodayWeather(berlin)

        assertTrue(result.isSuccess)
        assertEquals(WeatherOrigin.NETWORK, result.getOrThrow().origin)
    }

    // --- getCachedWeather ---

    @Test
    fun `getCachedWeather reports provenance and age`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minus(Duration.ofHours(2))))

        val cached = repository.getCachedWeather()

        assertNotNull(cached)
        assertEquals(WeatherOrigin.CACHE_FRESH, cached!!.origin)
        assertEquals(Duration.ofHours(2), cached.cacheAge)
    }

    @Test
    fun `getCachedWeather reports stale origin beyond three hours`() = runTest {
        fakeLocal.save(buildSnapshot(fetchedAt = fixedNow.minus(Duration.ofHours(5))))

        val cached = repository.getCachedWeather()

        assertEquals(WeatherOrigin.CACHE_STALE, cached!!.origin)
        assertEquals(Duration.ofHours(5), cached.cacheAge)
    }

    @Test
    fun `getCachedWeather returns null when the cache is empty`() = runTest {
        assertEquals(null, repository.getCachedWeather())
    }

    // --- Fakes ---

    private class FakeRemoteWeatherDataSource : RemoteWeatherDataSource {
        var nextForecastResult: Result<WeatherSnapshot>? = null
        var fetchCallCount = 0

        override suspend fun fetchForecast(location: LocationInfo): Result<WeatherSnapshot> {
            fetchCallCount++
            return nextForecastResult
                ?: Result.failure(IllegalStateException("No response set"))
        }

        override suspend fun geocodeLocation(query: String): Result<List<LocationInfo>> =
            Result.success(emptyList())
    }

    private class FakeLocalWeatherDataSource : LocalWeatherDataSource {
        private var latest: WeatherSnapshot? = null
        var lastPruneThresholdMs: Long? = null
        var failPrune: Boolean = false

        override suspend fun save(snapshot: WeatherSnapshot) {
            latest = snapshot
        }

        override suspend fun getLatest(): WeatherSnapshot? = latest

        override fun observeLatest(): Flow<WeatherSnapshot?> = flowOf(latest)

        override suspend fun pruneOlderThan(thresholdMs: Long) {
            if (failPrune) throw IllegalStateException("prune failed")
            lastPruneThresholdMs = thresholdMs
            if (latest != null && latest!!.fetchedAt.toEpochMilli() < thresholdMs) {
                latest = null
            }
        }
    }
}
