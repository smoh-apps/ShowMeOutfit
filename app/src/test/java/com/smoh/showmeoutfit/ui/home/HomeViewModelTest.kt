package com.smoh.showmeoutfit.ui.home

import app.cash.turbine.test
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
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import com.smoh.showmeoutfit.domain.usecase.GetTodayOutfitRecommendationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var weatherRepository: FakeWeatherRepository
    private lateinit var userRepository: FakeUserRepository
    private lateinit var locationRepository: FakeLocationRepository

    private val paris = LocationInfo(48.8566, 2.3522, "Paris", "France")
    private val berlin = LocationInfo(52.52, 13.405, "Berlin", "Germany")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        weatherRepository = FakeWeatherRepository()
        userRepository = FakeUserRepository(UserProfile(selectedLocation = paris))
        locationRepository = FakeLocationRepository()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(): HomeViewModel = HomeViewModel(
        getTodayOutfit = GetTodayOutfitRecommendationUseCase(
            weatherRepository = weatherRepository,
            userRepository = userRepository,
            locationRepository = locationRepository,
            engine = RuleBasedPresetEngine()
        ),
        userRepository = userRepository,
        ioDispatcher = dispatcher
    )

    private fun weatherData(
        temp: Double = 18.0,
        origin: WeatherOrigin = WeatherOrigin.NETWORK
    ): WeatherData {
        val hour = LocalDateTime.of(2026, 10, 24, 12, 0)
        return WeatherData(
            snapshot = WeatherSnapshot(
                location = paris,
                fetchedAt = Instant.parse("2026-10-24T10:00:00Z"),
                current = CurrentCondition(hour, temp, temp, 60, WeatherCode.CLEAR_SKY, 10.0),
                hourlyForecast = (7..22).map {
                    HourlyCondition(
                        time = LocalDateTime.of(2026, 10, 24, it, 0),
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
            ),
            origin = origin,
            cacheAge = null
        )
    }

    @Test
    fun `load publishes content on success`() = runTest {
        val vm = viewModel()

        val state = vm.state.value
        assertTrue(state is HomeUiState.Content)
        assertEquals(WeatherOrigin.NETWORK, (state as HomeUiState.Content).outfit.weather.origin)
        assertEquals(1, weatherRepository.calls)
    }

    @Test
    fun `cache provenance is exposed to the presentation layer`() = runTest {
        weatherRepository.result = Result.success(
            WeatherData(
                snapshot = weatherData().snapshot,
                origin = WeatherOrigin.CACHE_STALE,
                cacheAge = java.time.Duration.ofHours(5)
            )
        )

        val vm = viewModel()

        val content = vm.state.value as HomeUiState.Content
        assertEquals(WeatherOrigin.CACHE_STALE, content.outfit.weather.origin)
    }

    @Test
    fun `failure publishes error and emits a message effect`() = runTest {
        weatherRepository.result = Result.failure(DomainError.Offline(IOException("offline")))

        val vm = viewModel()

        vm.effects.test {
            val effect = awaitItem()
            assertTrue(effect is HomeEffect.ShowMessage)
            assertEquals(
                DomainError.Offline::class,
                (effect as HomeEffect.ShowMessage).error::class
            )
            cancelAndIgnoreRemainingEvents()
        }
        assertTrue(vm.state.value is HomeUiState.Error)
    }

    @Test
    fun `missing location publishes empty instead of an error`() = runTest {
        userRepository.setProfile(UserProfile(selectedLocation = null))
        locationRepository.lastKnown = Result.failure(IOException("no fix"))

        val vm = viewModel()

        assertEquals(HomeUiState.Empty, vm.state.value)
    }

    @Test
    fun `refresh marks content as refreshing and clears the flag when done`() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        weatherRepository.gate = gate

        vm.onIntent(HomeIntent.Refresh)

        val refreshing = vm.state.value as HomeUiState.Content
        assertTrue(refreshing.isRefreshing)

        gate.complete(Unit)

        val settled = vm.state.value as HomeUiState.Content
        assertTrue(!settled.isRefreshing)
        assertEquals(2, weatherRepository.calls)
    }

    @Test
    fun `duplicate load while a request is in flight is dropped`() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        weatherRepository.gate = gate

        vm.onIntent(HomeIntent.Load)
        vm.onIntent(HomeIntent.Load)
        vm.onIntent(HomeIntent.Refresh)

        gate.complete(Unit)

        assertEquals(2, weatherRepository.calls) // initial load + one refresh
    }

    @Test
    fun `selecting a location persists it and recomputes the recommendation`() = runTest {
        val vm = viewModel()
        assertEquals(1, weatherRepository.calls)

        vm.onIntent(HomeIntent.SelectLocation(berlin))

        assertEquals(berlin, userRepository.profile.value.selectedLocation)
        assertEquals(2, weatherRepository.calls)
        assertEquals(berlin, weatherRepository.lastRequestedLocation)
    }

    @Test
    fun `profile changes recompute the recommendation`() = runTest {
        val vm = viewModel()
        assertEquals(1, weatherRepository.calls)

        userRepository.setProfile(
            userRepository.profile.value.copy(ageBand = com.smoh.showmeoutfit.domain.model.AgeBand.SENIOR_60_PLUS)
        )

        assertEquals(2, weatherRepository.calls)
        val content = vm.state.value as HomeUiState.Content
        assertEquals(
            com.smoh.showmeoutfit.domain.model.AgeBand.SENIOR_60_PLUS,
            content.outfit.profile.ageBand
        )
    }

    @Test
    fun `dismissing an error retries the load`() = runTest {
        weatherRepository.result = Result.failure(DomainError.Offline(IOException("offline")))
        val vm = viewModel()
        assertTrue(vm.state.value is HomeUiState.Error)

        weatherRepository.result = Result.success(weatherData())
        vm.onIntent(HomeIntent.DismissError)

        assertTrue(vm.state.value is HomeUiState.Content)
        assertEquals(2, weatherRepository.calls)
    }

    @Test
    fun `state never exposes Android context or resources`() = runTest {
        val vm = viewModel()

        val content = vm.state.value as HomeUiState.Content
        assertTrue(content.outfit is TodayOutfit)
    }

    // --- Fakes ---

    private class FakeWeatherRepository : WeatherRepository {
        var result: Result<WeatherData> = Result.success(WeatherData(
            snapshot = WeatherSnapshot(
                location = LocationInfo(48.8566, 2.3522, "Paris", "France"),
                fetchedAt = Instant.parse("2026-10-24T10:00:00Z"),
                current = CurrentCondition(
                    LocalDateTime.of(2026, 10, 24, 12, 0), 18.0, 18.0, 60,
                    WeatherCode.CLEAR_SKY, 10.0
                ),
                hourlyForecast = emptyList(),
                dailyForecast = DailySummary(
                    LocalDate.of(2026, 10, 24), 20.0, 12.0, 0, WeatherCode.CLEAR_SKY
                )
            ),
            origin = WeatherOrigin.NETWORK
        ))
        var calls = 0
        var lastRequestedLocation: LocationInfo? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getTodayWeather(
            location: LocationInfo,
            forceRefresh: Boolean
        ): Result<WeatherData> {
            calls++
            lastRequestedLocation = location
            gate?.await()
            return result
        }

        override suspend fun getCachedWeather(): WeatherData? = result.getOrNull()
    }

    private class FakeUserRepository(initial: UserProfile) : UserRepository {
        val profile = MutableStateFlow(initial)

        fun setProfile(profile: UserProfile) {
            this.profile.value = profile
        }

        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            this.profile.value = profile
            return Result.success(Unit)
        }
    }

    private class FakeLocationRepository : LocationRepository {
        var lastKnown: Result<LocationInfo> =
            Result.success(LocationInfo(48.8566, 2.3522, "Paris", "France"))

        override suspend fun getLastKnownLocation(): Result<LocationInfo> = lastKnown

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
            Result.success(emptyList())
    }
}
