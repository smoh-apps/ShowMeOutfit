package com.smoh.showmeoutfit.data.scheduler

import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import com.smoh.showmeoutfit.domain.notification.OutfitNotifier
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import com.smoh.showmeoutfit.domain.usecase.GetTodayOutfitRecommendationUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * Exercises the real recommendation pipeline through the routine, so the assertions cover the
 * contract the worker actually depends on: which profile gates delivery, whether a fresh snapshot
 * is requested, and how a refusal is reported.
 */
class MorningOutfitRoutineTest {

    private val paris = LocationInfo(48.8566, 2.3522, "Paris", "France")
    private val notifier = FakeNotifier()
    private val profile = MutableStateFlow(
        UserProfile(isConfigured = true, selectedLocation = paris)
    )
    private val weatherRepository = FakeWeatherRepository()

    private fun routine(
        userRepository: UserRepository = FakeUserRepository(profile)
    ): MorningOutfitRoutine = MorningOutfitRoutine(
        getTodayOutfit = GetTodayOutfitRecommendationUseCase(
            weatherRepository = weatherRepository,
            userRepository = userRepository,
            locationRepository = FakeLocationRepository(),
            engine = RuleBasedPresetEngine()
        ),
        userRepository = userRepository,
        notifier = notifier
    )

    @Test
    fun `a successful run asks for fresh weather and delivers a full recommendation`() = runTest {
        val result = routine().deliver()

        assertEquals(MorningDelivery.Delivered, result)
        assertEquals(listOf(true), weatherRepository.forceRefreshRequests)
        val delivered = notifier.deliveredOutfits.single()
        assertEquals(WeatherOrigin.NETWORK, delivered.weather.origin)
        assertEquals(paris, delivered.weather.snapshot.location)
    }

    @Test
    fun `disabled alerts skip the weather request entirely`() = runTest {
        profile.value = profile.value.copy(isNotificationEnabled = false)

        val result = routine().deliver()

        assertEquals(MorningDelivery.AlertsDisabled, result)
        assertTrue(weatherRepository.forceRefreshRequests.isEmpty())
        assertTrue(notifier.deliveredOutfits.isEmpty())
    }

    @Test
    fun `an unconfigured profile is never announced`() = runTest {
        profile.value = profile.value.copy(isConfigured = false)

        val result = routine().deliver()

        assertEquals(MorningDelivery.AlertsDisabled, result)
        assertTrue(weatherRepository.forceRefreshRequests.isEmpty())
    }

    @Test
    fun `a blocked notification is reported instead of claimed as delivered`() = runTest {
        notifier.result = false

        val result = routine().deliver()

        assertEquals(MorningDelivery.NotificationBlocked, result)
    }

    @Test
    fun `weather failure reports that nothing could be announced`() = runTest {
        weatherRepository.result = Result.failure(DomainError.Offline())

        val result = routine().deliver()

        assertEquals(MorningDelivery.RecommendationUnavailable, result)
        assertTrue(notifier.deliveredOutfits.isEmpty())
    }

    @Test
    fun `an unreadable profile fails the run without notifying`() = runTest {
        val failing = object : UserRepository {
            override fun getUserProfile(): Flow<UserProfile> = flow { throw IOException("corrupt") }

            override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> =
                Result.success(Unit)
        }

        val result = routine(failing).deliver()

        assertEquals(MorningDelivery.RecommendationUnavailable, result)
        assertTrue(notifier.deliveredOutfits.isEmpty())
    }

    @Test
    fun `cancellation is never reported as a weather problem`() = runTest {
        weatherRepository.result = null
        weatherRepository.cancellation = CancellationException("scope closed")

        try {
            routine().deliver()
            throw AssertionError("cancellation must propagate")
        } catch (_: CancellationException) {
            // Expected: WorkManager must see the cancellation, not a failed delivery.
        }
        assertTrue(notifier.deliveredOutfits.isEmpty())
    }

    @Test
    fun `the engine output for the stored location is what gets delivered`() = runTest {
        routine().deliver()

        val delivered = notifier.deliveredOutfits.single()
        assertEquals(18.0, delivered.recommendation.summary.tempMaxCelsius, 0.001)
        assertEquals(paris.cityName, delivered.weather.snapshot.location.cityName)
    }

    private class FakeNotifier : OutfitNotifier {
        val deliveredOutfits = mutableListOf<TodayOutfit>()
        var result = true

        override suspend fun notifyMorningOutfit(outfit: TodayOutfit): Boolean {
            deliveredOutfits.add(outfit)
            return result
        }
    }

    private inner class FakeWeatherRepository : WeatherRepository {
        var result: Result<WeatherData>? = Result.success(weatherData())
        var cancellation: CancellationException? = null
        val forceRefreshRequests = mutableListOf<Boolean>()

        override suspend fun getTodayWeather(
            location: LocationInfo,
            forceRefresh: Boolean
        ): Result<WeatherData> {
            forceRefreshRequests.add(forceRefresh)
            cancellation?.let { throw it }
            return result ?: Result.failure(DomainError.Offline())
        }

        override suspend fun getCachedWeather(): WeatherData? = null
    }

    private class FakeLocationRepository : LocationRepository {
        override suspend fun getCurrentLocation(): Result<LocationInfo> =
            Result.failure(DomainError.NoLocation())

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
            Result.success(emptyList())
    }

    private class FakeUserRepository(
        private val profile: MutableStateFlow<UserProfile>
    ) : UserRepository {
        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            this.profile.value = profile
            return Result.success(Unit)
        }
    }

    private fun weatherData(temperature: Double = 18.0): WeatherData {
        val hour = LocalDateTime.of(2026, 11, 1, 12, 0)
        return WeatherData(
            snapshot = WeatherSnapshot(
                location = paris,
                fetchedAt = Instant.parse("2026-11-01T10:00:00Z"),
                current = CurrentCondition(
                    hour,
                    temperature,
                    temperature,
                    60,
                    WeatherCode.CLEAR_SKY,
                    10.0
                ),
                hourlyForecast = (7..22).map {
                    HourlyCondition(
                        time = LocalDateTime.of(2026, 11, 1, it, 0),
                        temperatureCelsius = temperature,
                        apparentTemperatureCelsius = temperature,
                        precipitationProbability = 0,
                        uvIndex = 2.0,
                        windSpeedKmh = 10.0,
                        weatherCode = WeatherCode.CLEAR_SKY
                    )
                },
                dailyForecast = DailySummary(
                    date = LocalDate.of(2026, 11, 1),
                    maxTempCelsius = temperature,
                    minTempCelsius = temperature,
                    maxPrecipitationProbability = 0,
                    weatherCode = WeatherCode.CLEAR_SKY
                )
            ),
            origin = WeatherOrigin.NETWORK
        )
    }
}
