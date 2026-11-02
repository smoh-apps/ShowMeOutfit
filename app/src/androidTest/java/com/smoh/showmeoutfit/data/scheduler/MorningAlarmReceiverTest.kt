package com.smoh.showmeoutfit.data.scheduler

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.Configuration
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * The alarm receiver → WorkManager → routine → notification → re-arm chain, on a real device.
 *
 * `WorkManagerTestInitHelper` swaps in a synchronous executor so the enqueued work runs before the
 * assertions, and Koin holds the same fakes the app-level tests use. The receivers are invoked
 * directly: the point is the work they enqueue, not Android's broadcast plumbing.
 */
@RunWith(AndroidJUnit4::class)
class MorningAlarmReceiverTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val profile = MutableStateFlow(
        UserProfile(isConfigured = true, selectedLocation = PARIS)
    )
    private val notifier = RecordingNotifier()
    private val alarmScheduler = RecordingAlarmScheduler()

    @Before
    fun setUp() {
        stopKoin()
        startKoin {
            androidContext(context.applicationContext)
            modules(
                module {
                    single<UserRepository> { FakeUserRepository(profile) }
                    single<WeatherRepository> { FakeWeatherRepository() }
                    single<LocationRepository> { FakeLocationRepository() }
                    single<AlarmScheduler> { alarmScheduler }
                    single<OutfitNotifier> { notifier }
                    single { MorningScheduleCoordinator(userRepository = get(), alarmScheduler = get()) }
                    single {
                        MorningOutfitRoutine(
                            getTodayOutfit = get(),
                            userRepository = get(),
                            notifier = get()
                        )
                    }
                },
                module {
                    single {
                        GetTodayOutfitRecommendationUseCase(
                            weatherRepository = get(),
                            userRepository = get(),
                            locationRepository = get(),
                            engine = RuleBasedPresetEngine()
                        )
                    }
                }
            )
        }

        val configuration = Configuration.Builder()
            .setMinimumLoggingLevel(Log.DEBUG)
            .setExecutor(SynchronousExecutor())
            .build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context, configuration)
    }

    @After
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun the_morning_alarm_delivers_the_outfit_and_rearms_tomorrow() {
        WorkManager.getInstance(context).cancelUniqueWork(MorningAlarmReceiver.UNIQUE_WORK_NAME).result.get()

        MorningAlarmReceiver().onReceive(
            context,
            Intent(MorningAlarmReceiver.ACTION_MORNING_ALARM)
        )

        val workInfo = awaitWorkInfo()
        WorkManagerTestInitHelper.getTestDriver(context)
            ?.setAllConstraintsMet(workInfo.id)

        awaitDeliveries(count = 1)
        assertEquals(1, notifier.delivered.size)
        assertEquals(PARIS.cityName, notifier.delivered.single().weather.snapshot.location.cityName)

        assertTrue(
            "the worker must arm the next morning alarm",
            alarmScheduler.scheduledTimes.contains(LocalTime.of(7, 0))
        )

        val finished = WorkManager.getInstance(context)
            .getWorkInfoById(workInfo.id)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertEquals(WorkInfo.State.SUCCEEDED, finished?.state)
    }

    @Test
    fun an_unrelated_action_is_ignored() {
        MorningAlarmReceiver().onReceive(context, Intent("com.example.SOMETHING_ELSE"))

        val infos = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(MorningAlarmReceiver.UNIQUE_WORK_NAME)
            .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        assertTrue(infos.all { it.state == WorkInfo.State.CANCELLED })
        assertTrue(notifier.delivered.isEmpty())
    }

    private fun awaitWorkInfo(): WorkInfo {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (System.currentTimeMillis() < deadline) {
            val infos = WorkManager.getInstance(context)
                .getWorkInfosForUniqueWork(MorningAlarmReceiver.UNIQUE_WORK_NAME)
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .filter { it.state != WorkInfo.State.CANCELLED }
            if (infos.isNotEmpty()) return infos.first()
            Thread.sleep(POLL_MILLIS)
        }
        throw AssertionError("the alarm did not enqueue any work")
    }

    private fun awaitDeliveries(count: Int) {
        val deadline = System.currentTimeMillis() + TIMEOUT_MILLIS
        while (notifier.delivered.size < count && System.currentTimeMillis() < deadline) {
            Thread.sleep(POLL_MILLIS)
        }
    }

    private class RecordingNotifier : OutfitNotifier {
        val delivered = mutableListOf<TodayOutfit>()

        override suspend fun notifyMorningOutfit(outfit: TodayOutfit): Boolean {
            delivered.add(outfit)
            return true
        }
    }

    private class RecordingAlarmScheduler : AlarmScheduler {
        val scheduledTimes = mutableListOf<LocalTime>()
        var cancelCount = 0

        override fun scheduleNext(time: LocalTime) {
            scheduledTimes.add(time)
        }

        override fun cancel() {
            cancelCount++
        }

        override val canScheduleExactAlarms: Boolean = true
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

    private class FakeWeatherRepository : WeatherRepository {
        override suspend fun getTodayWeather(
            location: LocationInfo,
            forceRefresh: Boolean
        ): Result<WeatherData> = Result.success(weatherData())

        override suspend fun getCachedWeather(): WeatherData? = null

        private fun weatherData(): WeatherData {
            val hour = LocalDateTime.of(2026, 11, 1, 12, 0)
            return WeatherData(
                snapshot = WeatherSnapshot(
                    location = PARIS,
                    fetchedAt = Instant.parse("2026-11-01T10:00:00Z"),
                    current = CurrentCondition(
                        hour,
                        14.0,
                        14.0,
                        70,
                        WeatherCode.PARTLY_CLOUDY,
                        12.0
                    ),
                    hourlyForecast = (7..22).map {
                        HourlyCondition(
                            time = LocalDateTime.of(2026, 11, 1, it, 0),
                            temperatureCelsius = 14.0 + (it % 5),
                            apparentTemperatureCelsius = 14.0,
                            precipitationProbability = 10,
                            uvIndex = 1.0,
                            windSpeedKmh = 12.0,
                            weatherCode = WeatherCode.PARTLY_CLOUDY
                        )
                    },
                    dailyForecast = DailySummary(
                        date = LocalDate.of(2026, 11, 1),
                        maxTempCelsius = 18.0,
                        minTempCelsius = 11.0,
                        maxPrecipitationProbability = 10,
                        weatherCode = WeatherCode.PARTLY_CLOUDY
                    )
                ),
                origin = WeatherOrigin.NETWORK
            )
        }
    }

    private class FakeLocationRepository : LocationRepository {
        override suspend fun getCurrentLocation(): Result<LocationInfo> =
            Result.success(PARIS)

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
            Result.success(emptyList())
    }

    private companion object {
        const val TIMEOUT_MILLIS = 10_000L
        const val TIMEOUT_SECONDS = 10L
        const val POLL_MILLIS = 50L

        val PARIS = LocationInfo(48.8566, 2.3522, "Paris", "France")
    }
}
