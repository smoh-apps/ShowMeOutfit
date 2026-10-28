package com.smoh.showmeoutfit.domain.usecase

import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

class GetTodayOutfitRecommendationUseCaseTest {

    private lateinit var fakeWeatherRepo: FakeWeatherRepository
    private lateinit var fakeUserRepo: FakeUserRepository
    private lateinit var fakeLocationRepo: FakeLocationRepository
    private lateinit var useCase: GetTodayOutfitRecommendationUseCase

    private val defaultLoc = LocationInfo(48.8566, 2.3522, "Paris", "France")

    @Before
    fun setUp() {
        fakeWeatherRepo = FakeWeatherRepository()
        fakeUserRepo = FakeUserRepository()
        fakeLocationRepo = FakeLocationRepository()
        useCase = GetTodayOutfitRecommendationUseCase(
            weatherRepository = fakeWeatherRepo,
            userRepository = fakeUserRepo,
            locationRepository = fakeLocationRepo,
            engine = RuleBasedPresetEngine()
        )
    }

    private fun buildSnapshot(temp: Double = 18.0): WeatherSnapshot {
        val now = LocalDateTime.of(2026, 10, 24, 10, 0)
        return WeatherSnapshot(
            location = defaultLoc,
            fetchedAt = Instant.now(),
            current = CurrentCondition(now, temp, temp, 60, WeatherCode.CLEAR_SKY, 10.0),
            hourlyForecast = (7..22).map { hour ->
                HourlyCondition(
                    time = LocalDateTime.of(2026, 10, 24, hour, 0),
                    temperatureCelsius = temp,
                    apparentTemperatureCelsius = temp,
                    precipitationProbability = 0,
                    uvIndex = 2.0,
                    windSpeedKmh = 10.0,
                    weatherCode = WeatherCode.CLEAR_SKY
                )
            },
            dailyForecast = DailySummary(
                date = LocalDate.of(2026, 10, 24),
                maxTempCelsius = temp,
                minTempCelsius = temp,
                maxPrecipitationProbability = 0,
                weatherCode = WeatherCode.CLEAR_SKY
            )
        )
    }

    private fun weatherData(
        temp: Double = 18.0,
        origin: WeatherOrigin = WeatherOrigin.NETWORK
    ) = WeatherData(
        snapshot = buildSnapshot(temp),
        origin = origin,
        cacheAge = null
    )

    @Test
    fun `invoke coordinates profile location and weather into an outfit recommendation`() = runTest {
        val customLoc = LocationInfo(52.52, 13.405, "Berlin", "Germany")
        fakeUserRepo.setProfile(
            UserProfile(
                thermalSensitivity = ThermalSensitivity.NEUTRAL,
                selectedLocation = customLoc
            )
        )
        fakeWeatherRepo.dataResult = Result.success(weatherData(temp = 20.0))

        val result = useCase()

        assertTrue(result.isSuccess)
        val todayOutfit = result.getOrThrow()
        assertEquals(GarmentId.COTTON_TEE, todayOutfit.recommendation.baseLayer.id)
        assertEquals(GarmentFit.UNISEX, todayOutfit.recommendation.baseLayer.fit)
        assertEquals(customLoc, fakeWeatherRepo.lastRequestedLocation)
        assertEquals(WeatherOrigin.NETWORK, todayOutfit.weather.origin)
    }

    @Test
    fun `invoke preserves cache provenance for the presentation layer`() = runTest {
        fakeUserRepo.setProfile(UserProfile(selectedLocation = defaultLoc))
        fakeWeatherRepo.dataResult = Result.success(
            WeatherData(
                snapshot = buildSnapshot(),
                origin = WeatherOrigin.CACHE_STALE,
                cacheAge = java.time.Duration.ofHours(5)
            )
        )

        val todayOutfit = useCase().getOrThrow()

        assertEquals(WeatherOrigin.CACHE_STALE, todayOutfit.weather.origin)
        assertEquals(java.time.Duration.ofHours(5), todayOutfit.weather.cacheAge)
    }

    @Test
    fun `invoke falls back to locationRepository when the profile has no location`() = runTest {
        fakeUserRepo.setProfile(UserProfile(selectedLocation = null))
        fakeWeatherRepo.dataResult = Result.success(weatherData(temp = 15.0))

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(defaultLoc, fakeWeatherRepo.lastRequestedLocation)
    }

    @Test
    fun `invoke returns NoLocation when neither profile nor device has a location`() = runTest {
        fakeUserRepo.setProfile(UserProfile(selectedLocation = null))
        fakeLocationRepo.lastKnownResult = Result.failure(IOException("no fix"))

        val result = useCase()

        assertTrue(result.exceptionOrNull() is DomainError.NoLocation)
        assertEquals(0, fakeWeatherRepo.calls)
    }

    @Test
    fun `invoke surfaces the repository DomainError unchanged`() = runTest {
        fakeUserRepo.setProfile(UserProfile(selectedLocation = defaultLoc))
        fakeWeatherRepo.dataResult = Result.failure(DomainError.Offline(IOException("offline")))

        val result = useCase()

        assertTrue(result.exceptionOrNull() is DomainError.Offline)
    }

    @Test
    fun `invoke rethrows cancellation instead of returning a failed Result`() = runTest {
        fakeUserRepo.setProfile(UserProfile(selectedLocation = defaultLoc))
        fakeWeatherRepo.dataResult = Result.failure(CancellationException("cancelled"))

        val caught = runCatching { useCase() }

        assertTrue(caught.exceptionOrNull() is CancellationException)
    }

    // --- Fakes ---

    private class FakeWeatherRepository : WeatherRepository {
        var dataResult: Result<WeatherData>? = null
        var lastRequestedLocation: LocationInfo? = null
        var calls = 0

        override suspend fun getTodayWeather(
            location: LocationInfo,
            forceRefresh: Boolean
        ): Result<WeatherData> {
            calls++
            lastRequestedLocation = location
            return dataResult ?: Result.failure(IllegalStateException("No weather set"))
        }

        override suspend fun getCachedWeather(): WeatherData? = dataResult?.getOrNull()
    }

    private class FakeUserRepository : UserRepository {
        private val flow = MutableStateFlow(UserProfile())

        fun setProfile(profile: UserProfile) {
            flow.value = profile
        }

        override fun getUserProfile(): Flow<UserProfile> = flow

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            flow.value = profile
            return Result.success(Unit)
        }
    }

    private class FakeLocationRepository : LocationRepository {
        var lastKnownResult: Result<LocationInfo> =
            Result.success(LocationInfo(48.8566, 2.3522, "Paris", "France"))

        override suspend fun getLastKnownLocation(): Result<LocationInfo> = lastKnownResult

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
            Result.success(emptyList())
    }
}
