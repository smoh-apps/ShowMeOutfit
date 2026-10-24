package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.data.local.datasource.LocalWeatherDataSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

class WeatherRepositoryImplTest {

    private lateinit var fakeRemote: FakeRemoteWeatherDataSource
    private lateinit var fakeLocal: FakeLocalWeatherDataSource
    private lateinit var repository: WeatherRepositoryImpl
    private var testCurrentTime: Instant = Instant.ofEpochMilli(1_000_000_000_000L) // Fixed base epoch

    private val sampleLocation = LocationInfo(
        latitude = 52.52,
        longitude = 13.405,
        cityName = "Berlin",
        country = "Germany"
    )

    @Before
    fun setUp() {
        fakeRemote = FakeRemoteWeatherDataSource()
        fakeLocal = FakeLocalWeatherDataSource()
        repository = WeatherRepositoryImpl(
            remoteDataSource = fakeRemote,
            localDataSource = fakeLocal,
            currentTimeProvider = { testCurrentTime }
        )
    }

    private fun buildSnapshot(fetchedAt: Instant, temp: Double = 15.0): WeatherSnapshot {
        val now = LocalDateTime.of(2026, 10, 24, 10, 0)
        return WeatherSnapshot(
            location = sampleLocation,
            fetchedAt = fetchedAt,
            current = CurrentCondition(
                time = now,
                temperatureCelsius = temp,
                apparentTemperatureCelsius = temp,
                humidityPercent = 60,
                weatherCode = WeatherCode.MAINLY_CLEAR,
                windSpeedKmh = 10.0
            ),
            hourlyForecast = listOf(
                HourlyCondition(
                    time = now,
                    temperatureCelsius = temp,
                    apparentTemperatureCelsius = temp,
                    precipitationProbability = 0,
                    uvIndex = 2.0,
                    windSpeedKmh = 10.0,
                    weatherCode = WeatherCode.MAINLY_CLEAR
                )
            ),
            dailyForecast = DailySummary(
                date = LocalDate.of(2026, 10, 24),
                maxTempCelsius = temp + 3.0,
                minTempCelsius = temp - 3.0,
                maxPrecipitationProbability = 0,
                weatherCode = WeatherCode.MAINLY_CLEAR
            )
        )
    }

    @Test
    fun `getTodayWeather returns cached data when cache is fresh within 3 hours`() = runTest {
        // Cache created 1 hour ago
        val oneHourAgo = testCurrentTime.minusSeconds(3600)
        val cachedSnapshot = buildSnapshot(fetchedAt = oneHourAgo, temp = 12.0)
        fakeLocal.save(cachedSnapshot)

        val result = repository.getTodayWeather(sampleLocation, forceRefresh = false)

        assertTrue(result.isSuccess)
        assertEquals(12.0, result.getOrThrow().current.temperatureCelsius, 0.01)
        assertEquals(0, fakeRemote.fetchCallCount) // No network call made
    }

    @Test
    fun `getTodayWeather fetches from network when cache is older than 3 hours`() = runTest {
        // Cache created 4 hours ago
        val fourHoursAgo = testCurrentTime.minusSeconds(4 * 3600)
        fakeLocal.save(buildSnapshot(fetchedAt = fourHoursAgo, temp = 10.0))

        val networkSnapshot = buildSnapshot(fetchedAt = testCurrentTime, temp = 16.0)
        fakeRemote.nextForecastResult = Result.success(networkSnapshot)

        val result = repository.getTodayWeather(sampleLocation, forceRefresh = false)

        assertTrue(result.isSuccess)
        assertEquals(16.0, result.getOrThrow().current.temperatureCelsius, 0.01)
        assertEquals(1, fakeRemote.fetchCallCount)
        assertEquals(16.0, fakeLocal.getLatest()!!.current.temperatureCelsius, 0.01) // Saved to cache
    }

    @Test
    fun `getTodayWeather with forceRefresh true ignores fresh cache and fetches network`() = runTest {
        val thirtyMinsAgo = testCurrentTime.minusSeconds(1800)
        fakeLocal.save(buildSnapshot(fetchedAt = thirtyMinsAgo, temp = 14.0))

        val networkSnapshot = buildSnapshot(fetchedAt = testCurrentTime, temp = 18.0)
        fakeRemote.nextForecastResult = Result.success(networkSnapshot)

        val result = repository.getTodayWeather(sampleLocation, forceRefresh = true)

        assertTrue(result.isSuccess)
        assertEquals(18.0, result.getOrThrow().current.temperatureCelsius, 0.01)
        assertEquals(1, fakeRemote.fetchCallCount)
    }

    @Test
    fun `getTodayWeather falls back to cache under 12 hours old on network failure`() = runTest {
        // Cache created 6 hours ago (stale > 3h, but valid fallback < 12h)
        val sixHoursAgo = testCurrentTime.minusSeconds(6 * 3600)
        val cachedSnapshot = buildSnapshot(fetchedAt = sixHoursAgo, temp = 13.5)
        fakeLocal.save(cachedSnapshot)

        fakeRemote.nextForecastResult = Result.failure(IOException("No internet connection"))

        val result = repository.getTodayWeather(sampleLocation, forceRefresh = false)

        assertTrue(result.isSuccess)
        assertEquals(13.5, result.getOrThrow().current.temperatureCelsius, 0.01)
        assertEquals(1, fakeRemote.fetchCallCount)
    }

    @Test
    fun `getTodayWeather returns failure when network fails and no cached data exists`() = runTest {
        fakeRemote.nextForecastResult = Result.failure(IOException("Network unreachable"))

        val result = repository.getTodayWeather(sampleLocation, forceRefresh = false)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IOException)
    }

    // --- Fake Data Sources ---
    private class FakeRemoteWeatherDataSource : RemoteWeatherDataSource {
        var nextForecastResult: Result<WeatherSnapshot>? = null
        var fetchCallCount = 0

        override suspend fun fetchForecast(location: LocationInfo): Result<WeatherSnapshot> {
            fetchCallCount++
            return nextForecastResult ?: Result.failure(IllegalStateException("No response set"))
        }

        override suspend fun geocodeLocation(query: String): Result<List<LocationInfo>> {
            return Result.success(emptyList())
        }
    }

    private class FakeLocalWeatherDataSource : LocalWeatherDataSource {
        private var latest: WeatherSnapshot? = null

        override suspend fun save(snapshot: WeatherSnapshot) {
            latest = snapshot
        }

        override suspend fun getLatest(): WeatherSnapshot? = latest

        override fun observeLatest(): Flow<WeatherSnapshot?> = flowOf(latest)

        override suspend fun pruneOlderThan(thresholdMs: Long) {
            if (latest != null && latest!!.fetchedAt.toEpochMilli() < thresholdMs) {
                latest = null
            }
        }
    }
}
