package com.smoh.showmeoutfit.data.scheduler

import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
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
import java.time.LocalTime

/**
 * Proves the scheduler chain emits the structured events the observability ticket promises, and
 * that a graph without a logger keeps working exactly as before.
 */
class MorningAlertLogTest {

    private val log = RecordingLog()

    @Test
    fun `arming the alarm records the scheduled time`() {
        val scheduler = RecordingAlarmScheduler()
        val coordinator = MorningScheduleCoordinator(
            userRepository = FakeUserRepository(MutableStateFlow(configuredProfile())),
            alarmScheduler = scheduler,
            log = log
        )

        coordinator.apply(configuredProfile().copy(morningNotificationTime = LocalTime.of(6, 45)))

        assertEquals(listOf("alarm_scheduled@06:45"), log.events)
        assertEquals(listOf(LocalTime.of(6, 45)), scheduler.scheduledTimes)
    }

    @Test
    fun `a disabled profile cancels without logging a schedule`() {
        val coordinator = MorningScheduleCoordinator(
            userRepository = FakeUserRepository(MutableStateFlow(configuredProfile())),
            alarmScheduler = RecordingAlarmScheduler(),
            log = log
        )

        coordinator.apply(configuredProfile().copy(isNotificationEnabled = false))

        assertTrue(log.events.isEmpty())
    }

    @Test
    fun `an unreadable profile records a schedule sync failure`() = runTest {
        val failing = object : UserRepository {
            override fun getUserProfile(): Flow<UserProfile> = flow { throw IOException("corrupt") }

            override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> =
                Result.success(Unit)
        }
        val coordinator = MorningScheduleCoordinator(
            userRepository = failing,
            alarmScheduler = RecordingAlarmScheduler(),
            log = log
        )

        coordinator.sync()

        assertEquals(listOf("schedule_sync_failed"), log.events)
    }

    @Test
    fun `a refused notification records the blocked delivery`() = runTest {
        val routine = MorningOutfitRoutine(
            getTodayOutfit = successfulUseCase(),
            userRepository = FakeUserRepository(MutableStateFlow(configuredProfile())),
            notifier = FakeNotifier(result = false),
            log = log
        )

        val delivery = routine.deliver()

        assertEquals(MorningDelivery.NotificationBlocked, delivery)
        assertEquals(listOf("notification_blocked"), log.events)
    }

    @Test
    fun `a delivered notification logs no failure event`() = runTest {
        val routine = MorningOutfitRoutine(
            getTodayOutfit = successfulUseCase(),
            userRepository = FakeUserRepository(MutableStateFlow(configuredProfile())),
            notifier = FakeNotifier(result = true),
            log = log
        )

        val delivery = routine.deliver()

        assertEquals(MorningDelivery.Delivered, delivery)
        assertTrue(log.events.isEmpty())
    }

    @Test
    fun `the no-op default keeps existing constructor call sites safe`() = runTest {
        val scheduler = RecordingAlarmScheduler()
        // No logger argument: exactly how every pre-S4-04 call site is written.
        MorningScheduleCoordinator(
            userRepository = FakeUserRepository(MutableStateFlow(configuredProfile())),
            alarmScheduler = scheduler
        ).apply(configuredProfile())

        assertEquals(listOf(LocalTime.of(7, 0)), scheduler.scheduledTimes)

        // Every event method is safe to call on the default.
        with(MorningAlertLog.NoOp) {
            alarmScheduled(LocalTime.of(7, 0))
            alarmFired(MorningAlertSource.EXACT_ALARM)
            workerStarted()
            workerFinished(MorningDelivery.Delivered)
            notificationBlocked()
            scheduleSyncFailed()
        }
    }

    private fun successfulUseCase(): GetTodayOutfitRecommendationUseCase {
        // A real use case wired to in-memory fakes. MockK cannot stand in for a suspend function
        // returning an inline `Result<T>`: the value-class boxing makes the stubbed answer arrive as
        // a `kotlin.Result` where `TodayOutfit` is expected.
        val weatherRepository = object : WeatherRepository {
            override suspend fun getTodayWeather(
                location: LocationInfo,
                forceRefresh: Boolean
            ): Result<WeatherData> = Result.success(weatherData())

            override suspend fun getCachedWeather(): WeatherData? = null
        }
        val locationRepository = object : LocationRepository {
            override suspend fun getCurrentLocation(): Result<LocationInfo> =
                Result.success(berlinLocation)

            override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
                Result.success(emptyList())
        }
        return GetTodayOutfitRecommendationUseCase(
            weatherRepository = weatherRepository,
            userRepository = FakeUserRepository(MutableStateFlow(configuredProfile())),
            locationRepository = locationRepository,
            engine = RuleBasedPresetEngine()
        )
    }

    private fun weatherData(): WeatherData = WeatherData(
        snapshot = WeatherSnapshot(
            location = berlinLocation,
            fetchedAt = Instant.parse("2026-10-24T08:00:00Z"),
            current = CurrentCondition(
                time = LocalDateTime.of(2026, 10, 24, 10, 0),
                temperatureCelsius = 14.2,
                apparentTemperatureCelsius = 12.1,
                humidityPercent = 70,
                weatherCode = WeatherCode.PARTLY_CLOUDY,
                windSpeedKmh = 18.0
            ),
            hourlyForecast = emptyList(),
            dailyForecast = DailySummary(
                date = LocalDate.of(2026, 10, 24),
                maxTempCelsius = 16.1,
                minTempCelsius = 9.5,
                maxPrecipitationProbability = 20,
                weatherCode = WeatherCode.PARTLY_CLOUDY
            )
        ),
        origin = WeatherOrigin.NETWORK
    )

    private val berlinLocation = LocationInfo(
        latitude = 52.52,
        longitude = 13.405,
        cityName = "Berlin",
        country = "Germany",
        timezone = "Europe/Berlin"
    )

    private fun configuredProfile() = UserProfile(
        isConfigured = true,
        isNotificationEnabled = true,
        morningNotificationTime = LocalTime.of(7, 0),
        selectedLocation = berlinLocation
    )

    /** Only event names and event arguments are recorded: no free-form message channel exists. */
    private class RecordingLog : MorningAlertLog {
        val events = mutableListOf<String>()

        override fun alarmScheduled(time: LocalTime) {
            events += "alarm_scheduled@$time"
        }

        override fun alarmFired(source: MorningAlertSource) {
            events += "alarm_fired@${source.name}"
        }

        override fun workerStarted() {
            events += "worker_started"
        }

        override fun workerFinished(delivery: MorningDelivery) {
            events += "worker_finished@$delivery"
        }

        override fun notificationBlocked() {
            events += "notification_blocked"
        }

        override fun scheduleSyncFailed() {
            events += "schedule_sync_failed"
        }
    }

    private class FakeNotifier(private val result: Boolean) : OutfitNotifier {
        override suspend fun notifyMorningOutfit(outfit: TodayOutfit): Boolean = result
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

    private class RecordingAlarmScheduler : AlarmScheduler {
        val scheduledTimes = mutableListOf<LocalTime>()

        override fun scheduleNext(time: LocalTime) {
            scheduledTimes.add(time)
        }

        override fun cancel() = Unit

        override val canScheduleExactAlarms: Boolean = true
    }
}
