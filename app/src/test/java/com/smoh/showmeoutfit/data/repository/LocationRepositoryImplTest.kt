package com.smoh.showmeoutfit.data.repository

import com.smoh.showmeoutfit.data.location.DeviceLocationSource
import com.smoh.showmeoutfit.data.remote.datasource.RemoteWeatherDataSource
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

class LocationRepositoryImplTest {

    private lateinit var fakeLocationSource: FakeDeviceLocationSource
    private lateinit var fakeRemote: FakeRemoteWeatherDataSource
    private lateinit var repository: LocationRepositoryImpl

    private val berlin = LocationInfo(
        latitude = 52.52,
        longitude = 13.405,
        cityName = "Berlin",
        country = "Germany",
        timezone = "Europe/Berlin"
    )

    @Before
    fun setUp() {
        fakeLocationSource = FakeDeviceLocationSource()
        fakeRemote = FakeRemoteWeatherDataSource()
        repository = LocationRepositoryImpl(
            deviceLocationSource = fakeLocationSource,
            remoteDataSource = fakeRemote,
            currentLocationTimeoutMillis = 10_000
        )
    }

    // --- Current location ---

    @Test
    fun `a cached last known fix is returned without asking for a fresh one`() = runTest {
        fakeLocationSource.lastKnown = berlin

        val result = repository.getCurrentLocation().getOrThrow()

        assertEquals(berlin, result)
        assertEquals(0, fakeLocationSource.currentCalls)
    }

    @Test
    fun `no cached fix triggers a single bounded current location request`() = runTest {
        fakeLocationSource.lastKnown = null
        fakeLocationSource.currentResult = Result.success(berlin)

        val result = repository.getCurrentLocation().getOrThrow()

        assertEquals(berlin, result)
        assertEquals(1, fakeLocationSource.currentCalls)
        assertEquals(10_000L, fakeLocationSource.lastTimeoutMillis)
    }

    @Test
    fun `permission denial is surfaced as a typed error`() = runTest {
        fakeLocationSource.lastKnown = null
        fakeLocationSource.currentResult = Result.failure(DomainError.LocationPermissionDenied())

        val result = repository.getCurrentLocation()

        assertTrue(result.exceptionOrNull() is DomainError.LocationPermissionDenied)
    }

    @Test
    fun `a timeout is surfaced as LocationUnavailable and never crashes`() = runTest {
        fakeLocationSource.lastKnown = null
        fakeLocationSource.currentResult = Result.failure(DomainError.LocationUnavailable())

        val result = repository.getCurrentLocation()

        assertTrue(result.exceptionOrNull() is DomainError.LocationUnavailable)
    }

    @Test
    fun `location services disabled is surfaced as LocationDisabled`() = runTest {
        fakeLocationSource.lastKnown = null
        fakeLocationSource.currentResult = Result.failure(DomainError.LocationDisabled())

        val result = repository.getCurrentLocation()

        assertTrue(result.exceptionOrNull() is DomainError.LocationDisabled)
    }

    @Test
    fun `an unexpected platform failure is wrapped instead of escaping`() = runTest {
        fakeLocationSource.lastKnown = null
        fakeLocationSource.currentResult = Result.failure(IllegalStateException("binder died"))

        val result = repository.getCurrentLocation()

        assertTrue(result.exceptionOrNull() is DomainError.LocationUnavailable)
    }

    @Test
    fun `a throwing location source never escapes as a raw exception`() = runTest {
        fakeLocationSource.lastKnown = null
        fakeLocationSource.throwOnCurrent = IllegalStateException("binder died")

        val result = repository.getCurrentLocation()

        assertTrue(result.exceptionOrNull() is DomainError.LocationUnavailable)
    }

    @Test
    fun `cancelling a location request rethrows instead of returning a failure`() = runTest {
        fakeLocationSource.lastKnown = null
        fakeLocationSource.throwOnCurrent = CancellationException("cancelled")

        val caught = runCatching { repository.getCurrentLocation() }

        assertTrue(caught.exceptionOrNull() is CancellationException)
    }

    @Test
    fun `a hanging location source is bounded by the overall deadline instead of blocking`() = runTest {
        // The platform never answers: the whole resolution must still finish and stay recoverable.
        fakeLocationSource.hangLastKnownMillis = Long.MAX_VALUE

        val result = repository.getCurrentLocation()

        assertTrue(result.exceptionOrNull() is DomainError.LocationUnavailable)
    }

    @Test
    fun `there is no implicit default city when location is unavailable`() = runTest {
        fakeLocationSource.lastKnown = null
        fakeLocationSource.currentResult = Result.failure(DomainError.LocationPermissionDenied())

        val result = repository.getCurrentLocation()

        assertTrue(result.isFailure)
        assertEquals(null, result.getOrNull())
    }

    // --- City search ---

    @Test
    fun `a blank query returns an empty result without hitting the network`() = runTest {
        assertEquals(emptyList<LocationInfo>(), repository.searchLocation("   ").getOrThrow())
        assertEquals(0, fakeRemote.geocodeCalls)
    }

    @Test
    fun `a query is trimmed before it reaches the network`() = runTest {
        fakeRemote.geocodeResult = Result.success(listOf(berlin))

        repository.searchLocation("  Berlin  ").getOrThrow()

        assertEquals("Berlin", fakeRemote.lastQuery)
    }

    @Test
    fun `search results are passed through unchanged`() = runTest {
        fakeRemote.geocodeResult = Result.success(listOf(berlin))

        val results = repository.searchLocation("Berlin").getOrThrow()

        assertEquals(listOf(berlin), results)
    }

    @Test
    fun `an offline search failure maps to DomainError Offline`() = runTest {
        fakeRemote.geocodeResult = Result.failure(IOException("no internet"))

        val result = repository.searchLocation("Berlin")

        assertTrue(result.exceptionOrNull() is DomainError.Offline)
    }

    @Test
    fun `a malformed search response maps to DomainError MalformedData`() = runTest {
        fakeRemote.geocodeResult = Result.failure(SerializationException("bad json"))

        val result = repository.searchLocation("Berlin")

        assertTrue(result.exceptionOrNull() is DomainError.MalformedData)
    }

    @Test
    fun `cancelling a search rethrows`() = runTest {
        fakeRemote.geocodeResult = Result.failure(CancellationException("cancelled"))

        val caught = runCatching { repository.searchLocation("Berlin") }

        assertTrue(caught.exceptionOrNull() is CancellationException)
    }

    // --- Fakes ---

    private class FakeDeviceLocationSource : DeviceLocationSource {
        var lastKnown: LocationInfo? = null
        var hangLastKnownMillis: Long? = null
        var currentResult: Result<LocationInfo> =
            Result.failure(DomainError.LocationUnavailable())
        var throwOnCurrent: Throwable? = null
        var currentCalls = 0
        var lastTimeoutMillis: Long? = null

        override suspend fun getLastKnown(): LocationInfo? {
            hangLastKnownMillis?.let { delay(it) }
            return lastKnown
        }

        override suspend fun getCurrent(timeoutMillis: Long): Result<LocationInfo> {
            currentCalls++
            lastTimeoutMillis = timeoutMillis
            throwOnCurrent?.let { throw it }
            return currentResult
        }
    }

    private class FakeRemoteWeatherDataSource : RemoteWeatherDataSource {
        var geocodeResult: Result<List<LocationInfo>> = Result.success(emptyList())
        var geocodeCalls = 0
        var lastQuery: String? = null

        override suspend fun fetchForecast(location: LocationInfo): Result<WeatherSnapshot> {
            val hour = LocalDateTime.of(2026, 10, 24, 12, 0)
            return Result.success(
                WeatherSnapshot(
                    location = location,
                    fetchedAt = Instant.parse("2026-10-24T10:00:00Z"),
                    current = CurrentCondition(
                        hour, 15.0, 15.0, 60, WeatherCode.CLEAR_SKY, 10.0
                    ),
                    hourlyForecast = listOf(
                        HourlyCondition(hour, 15.0, 15.0, 0, 2.0, 10.0, WeatherCode.CLEAR_SKY)
                    ),
                    dailyForecast = DailySummary(
                        LocalDate.of(2026, 10, 24), 18.0, 12.0, 0, WeatherCode.CLEAR_SKY
                    )
                )
            )
        }

        override suspend fun geocodeLocation(query: String): Result<List<LocationInfo>> {
            geocodeCalls++
            lastQuery = query
            return geocodeResult
        }
    }
}
