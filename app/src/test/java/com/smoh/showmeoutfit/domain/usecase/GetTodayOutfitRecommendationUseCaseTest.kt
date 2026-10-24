package com.smoh.showmeoutfit.domain.usecase

import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherCode
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
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

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

    @Test
    fun `invoke coordinates user profile location and weather into outfit recommendation`() = runTest {
        val customLoc = LocationInfo(52.52, 13.405, "Berlin", "Germany")
        fakeUserRepo.setProfile(
            UserProfile(
                thermalSensitivity = ThermalSensitivity.NEUTRAL,
                selectedLocation = customLoc
            )
        )
        fakeWeatherRepo.snapshotResult = Result.success(buildSnapshot(temp = 20.0))

        val result = useCase()

        assertTrue(result.isSuccess)
        val recommendation = result.getOrThrow()
        assertNotNull(recommendation.headline)
        assertEquals("Breathable Cotton T-Shirt", recommendation.baseLayer.name)
        assertEquals(customLoc, fakeWeatherRepo.lastRequestedLocation)
    }

    @Test
    fun `invoke falls back to locationRepository when user profile location is null`() = runTest {
        fakeUserRepo.setProfile(UserProfile(selectedLocation = null))
        fakeWeatherRepo.snapshotResult = Result.success(buildSnapshot(temp = 15.0))

        val result = useCase()

        assertTrue(result.isSuccess)
        assertEquals(defaultLoc, fakeWeatherRepo.lastRequestedLocation)
    }

    // --- Fakes ---
    private class FakeWeatherRepository : WeatherRepository {
        var snapshotResult: Result<WeatherSnapshot>? = null
        var lastRequestedLocation: LocationInfo? = null

        override suspend fun getTodayWeather(location: LocationInfo, forceRefresh: Boolean): Result<WeatherSnapshot> {
            lastRequestedLocation = location
            return snapshotResult ?: Result.failure(IllegalStateException("No weather set"))
        }

        override suspend fun getCachedWeather(): WeatherSnapshot? = snapshotResult?.getOrNull()
    }

    private class FakeUserRepository : UserRepository {
        private val flow = MutableStateFlow(UserProfile())
        fun setProfile(p: UserProfile) { flow.value = p }
        override fun getUserProfile(): Flow<UserProfile> = flow
        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            flow.value = profile
            return Result.success(Unit)
        }
    }

    private class FakeLocationRepository : LocationRepository {
        override suspend fun getLastKnownLocation(): Result<LocationInfo> =
            Result.success(LocationInfo(48.8566, 2.3522, "Paris", "France"))

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
            Result.success(emptyList())
    }
}
