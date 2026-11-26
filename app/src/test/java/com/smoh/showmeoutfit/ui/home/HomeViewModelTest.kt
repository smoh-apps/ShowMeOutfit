package com.smoh.showmeoutfit.ui.home

import app.cash.turbine.test
import com.smoh.showmeoutfit.domain.engine.RuleBasedPresetEngine
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.CurrentCondition
import com.smoh.showmeoutfit.domain.model.DailySummary
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GarmentId
import com.smoh.showmeoutfit.domain.model.HourlyCondition
import com.smoh.showmeoutfit.domain.model.LayerCategory
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.model.WardrobeItem
import com.smoh.showmeoutfit.domain.model.WeatherCode
import com.smoh.showmeoutfit.domain.model.WeatherData
import com.smoh.showmeoutfit.domain.model.WeatherOrigin
import com.smoh.showmeoutfit.domain.model.WeatherSnapshot
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.repository.WardrobeRepository
import com.smoh.showmeoutfit.domain.repository.WeatherRepository
import com.smoh.showmeoutfit.domain.usecase.GetTodayOutfitRecommendationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
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
    private lateinit var wardrobeRepository: FakeWardrobeRepository
    private lateinit var locationRepository: FakeLocationRepository

    private val paris = LocationInfo(48.8566, 2.3522, "Paris", "France")
    private val berlin = LocationInfo(52.52, 13.405, "Berlin", "Germany")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        weatherRepository = FakeWeatherRepository()
        userRepository = FakeUserRepository(UserProfile(selectedLocation = paris))
        wardrobeRepository = FakeWardrobeRepository()
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
        wardrobeRepository = wardrobeRepository,
        ioDispatcher = dispatcher
    )

    private fun HomeViewModel.closetKeys(): Set<Pair<LayerCategory, GarmentId>> =
        (state.value as HomeUiState.Content).closetItemKeys

    private fun wardrobeItem(
        id: String,
        category: LayerCategory,
        garmentId: GarmentId,
        fit: GarmentFit = GarmentFit.UNISEX,
        name: String = "Closet item"
    ): WardrobeItem = WardrobeItem(
        id = id,
        name = name,
        category = category,
        garmentId = garmentId,
        fit = fit,
        createdAtEpochMillis = 1L
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
    fun `initial closet items become exact category and garment keys`() = runTest {
        wardrobeRepository.emit(
            listOf(
                wardrobeItem("base", LayerCategory.BASE, GarmentId.COTTON_TEE),
                wardrobeItem("mid", LayerCategory.MID, GarmentId.COTTON_TEE)
            )
        )

        val content = viewModel().state.value as HomeUiState.Content

        assertEquals(
            setOf(
                LayerCategory.BASE to GarmentId.COTTON_TEE,
                LayerCategory.MID to GarmentId.COTTON_TEE
            ),
            content.closetItemKeys
        )
    }

    @Test
    fun `closet keys deduplicate fits names and item ids`() = runTest {
        wardrobeRepository.emit(
            listOf(
                wardrobeItem(
                    id = "first",
                    category = LayerCategory.BOTTOM,
                    garmentId = GarmentId.JEANS,
                    fit = GarmentFit.MEN,
                    name = "Everyday jeans"
                ),
                wardrobeItem(
                    id = "second",
                    category = LayerCategory.BOTTOM,
                    garmentId = GarmentId.JEANS,
                    fit = GarmentFit.WOMEN,
                    name = "Custom label"
                )
            )
        )

        val content = viewModel().state.value as HomeUiState.Content

        assertEquals(setOf(LayerCategory.BOTTOM to GarmentId.JEANS), content.closetItemKeys)
    }

    @Test
    fun `insert update and delete emissions change closet state without weather IO`() = runTest {
        val vm = viewModel()
        val callsAfterLoad = weatherRepository.calls
        val inserted = wardrobeItem("jeans", LayerCategory.BOTTOM, GarmentId.JEANS)

        wardrobeRepository.emit(listOf(inserted))
        assertEquals(setOf(LayerCategory.BOTTOM to GarmentId.JEANS), vm.closetKeys())

        wardrobeRepository.emit(
            listOf(inserted.copy(name = "Renamed", fit = GarmentFit.WOMEN))
        )
        assertEquals(setOf(LayerCategory.BOTTOM to GarmentId.JEANS), vm.closetKeys())

        wardrobeRepository.emit(emptyList())
        assertEquals(emptySet<Pair<LayerCategory, GarmentId>>(), vm.closetKeys())
        assertEquals(callsAfterLoad, weatherRepository.calls)
        assertEquals(1, wardrobeRepository.observeCalls)
    }

    @Test
    fun `closet changes during a successful refresh survive the new content`() = runTest {
        val vm = viewModel()
        wardrobeRepository.emit(
            listOf(wardrobeItem("tee", LayerCategory.BASE, GarmentId.COTTON_TEE))
        )
        val gate = CompletableDeferred<Unit>()
        weatherRepository.gate = gate

        vm.onIntent(HomeIntent.Refresh)
        wardrobeRepository.emit(
            listOf(wardrobeItem("jacket", LayerCategory.OUTER, GarmentId.LIGHT_JACKET))
        )
        gate.complete(Unit)
        runCurrent()

        assertEquals(
            setOf(LayerCategory.OUTER to GarmentId.LIGHT_JACKET),
            vm.closetKeys()
        )
    }

    @Test
    fun `a failed refresh retains the latest closet keys`() = runTest {
        val vm = viewModel()
        wardrobeRepository.emit(
            listOf(wardrobeItem("boots", LayerCategory.FOOTWEAR, GarmentId.WINTER_BOOTS))
        )
        weatherRepository.result = Result.failure(DomainError.Offline(IOException("offline")))

        vm.onIntent(HomeIntent.Refresh)
        runCurrent()

        val content = vm.state.value as HomeUiState.Content
        assertTrue(content.refreshFailed)
        assertEquals(setOf(LayerCategory.FOOTWEAR to GarmentId.WINTER_BOOTS), content.closetItemKeys)
    }

    @Test
    fun `closet emissions while initial weather is loading survive until content`() = runTest {
        val gate = CompletableDeferred<Unit>()
        weatherRepository.gate = gate
        val vm = viewModel()
        assertTrue(vm.state.value is HomeUiState.Loading)

        wardrobeRepository.emit(
            listOf(wardrobeItem("coat", LayerCategory.OUTER, GarmentId.WOOL_COAT))
        )
        gate.complete(Unit)
        runCurrent()

        assertEquals(setOf(LayerCategory.OUTER to GarmentId.WOOL_COAT), vm.closetKeys())
    }

    @Test
    fun `closet emissions while the dashboard is empty survive the next successful load`() = runTest {
        userRepository.setProfile(UserProfile(selectedLocation = null))
        locationRepository.lastKnown = Result.failure(IOException("no fix"))
        val vm = viewModel()
        assertEquals(HomeUiState.Empty, vm.state.value)

        wardrobeRepository.emit(
            listOf(wardrobeItem("shorts", LayerCategory.BOTTOM, GarmentId.SHORTS))
        )
        userRepository.setProfile(UserProfile(selectedLocation = paris))
        runCurrent()

        assertEquals(setOf(LayerCategory.BOTTOM to GarmentId.SHORTS), vm.closetKeys())
    }

    @Test
    fun `closet emissions while the dashboard is an error survive recovery`() = runTest {
        weatherRepository.result = Result.failure(DomainError.Offline(IOException("offline")))
        val vm = viewModel()
        assertTrue(vm.state.value is HomeUiState.Error)

        wardrobeRepository.emit(
            listOf(wardrobeItem("sandals", LayerCategory.FOOTWEAR, GarmentId.SANDALS))
        )
        weatherRepository.result = Result.success(weatherData())
        vm.onIntent(HomeIntent.Refresh)
        runCurrent()

        assertEquals(setOf(LayerCategory.FOOTWEAR to GarmentId.SANDALS), vm.closetKeys())
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
    fun `an initial failure publishes the error state without a duplicate snackbar`() = runTest {
        weatherRepository.result = Result.failure(DomainError.Offline(IOException("offline")))

        val vm = viewModel()

        // The screen shows the message inline; a snackbar with the same copy read as a duplicate.
        vm.effects.test { expectNoEvents() }
        assertTrue(vm.state.value is HomeUiState.Error)
        assertEquals(
            DomainError.Offline::class,
            ((vm.state.value as HomeUiState.Error).error)::class
        )
    }

    @Test
    fun `missing location publishes empty instead of an error`() = runTest {
        userRepository.setProfile(UserProfile(selectedLocation = null))
        locationRepository.lastKnown = Result.failure(IOException("no fix"))

        val vm = viewModel()

        assertEquals(HomeUiState.Empty, vm.state.value)
    }

    @Test
    fun `denied location permission still leaves a manual city path`() = runTest {
        userRepository.setProfile(UserProfile(selectedLocation = null))
        locationRepository.lastKnown = Result.failure(DomainError.LocationPermissionDenied())

        val vm = viewModel()

        // Not a dead-end error: the empty state offers "choose a city".
        assertEquals(HomeUiState.Empty, vm.state.value)

        vm.onIntent(HomeIntent.OpenLocationSearch)

        vm.effects.test {
            assertEquals(HomeEffect.OpenLocationSearch, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `location services disabled still leaves a manual city path`() = runTest {
        userRepository.setProfile(UserProfile(selectedLocation = null))
        locationRepository.lastKnown = Result.failure(DomainError.LocationDisabled())

        val vm = viewModel()

        assertEquals(HomeUiState.Empty, vm.state.value)
    }

    @Test
    fun `refreshing after an expired cache asks for the network, not the cache`() = runTest {
        // CacheExpired means the stored row is from another day, another city, or older than the
        // 12 h fallback. A non-forced reload would read that same row and fail identically.
        weatherRepository.result = Result.failure(DomainError.CacheExpired())
        val vm = viewModel()

        assertTrue(vm.state.value is HomeUiState.Error)

        vm.onIntent(HomeIntent.Refresh)
        runCurrent()

        assertEquals(true, weatherRepository.lastForceRefresh)
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
    fun `concurrent triggers are coalesced into exactly one follow-up request`() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        weatherRepository.gate = gate

        // Three triggers while the first request is blocked must not queue three requests.
        vm.onIntent(HomeIntent.Load)
        vm.onIntent(HomeIntent.Load)
        vm.onIntent(HomeIntent.Refresh)

        gate.complete(Unit)

        assertEquals(3, weatherRepository.calls) // initial + blocked + one coalesced follow-up
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
    fun `selecting a location during an in-flight fetch still fetches the new city`() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        weatherRepository.gate = gate

        vm.onIntent(HomeIntent.SelectLocation(berlin))
        assertEquals(berlin, userRepository.profile.value.selectedLocation)

        gate.complete(Unit)

        assertEquals(2, weatherRepository.calls)
        assertEquals(berlin, weatherRepository.lastRequestedLocation)
    }

    @Test
    fun `profile change during an in-flight fetch is not lost`() = runTest {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        weatherRepository.gate = gate

        userRepository.setProfile(
            userRepository.profile.value.copy(ageBand = AgeBand.SENIOR_60_PLUS)
        )
        gate.complete(Unit)

        assertEquals(2, weatherRepository.calls)
        val content = vm.state.value as HomeUiState.Content
        assertEquals(AgeBand.SENIOR_60_PLUS, content.outfit.profile.ageBand)
    }

    @Test
    fun `a failed refresh keeps the last dashboard and marks it as unrefreshed`() = runTest {
        val vm = viewModel()
        val before = vm.state.value as HomeUiState.Content

        weatherRepository.result = Result.failure(DomainError.Offline(IOException("offline")))
        vm.onIntent(HomeIntent.Refresh)

        vm.effects.test {
            assertTrue(awaitItem() is HomeEffect.ShowMessage)
            cancelAndIgnoreRemainingEvents()
        }

        val after = vm.state.value
        assertTrue("a failed refresh must not wipe usable content", after is HomeUiState.Content)
        assertEquals(before.outfit, (after as HomeUiState.Content).outfit)
        assertTrue(!after.isRefreshing)
        // Without this the previous (network) snapshot would look live again once the snackbar
        // disappears, because a non-cached snapshot carries no provenance banner.
        assertTrue("a failed refresh must be visible in the state", after.refreshFailed)
    }

    @Test
    fun `a successful refresh clears the unrefreshed marker`() = runTest {
        val vm = viewModel()
        val good = weatherRepository.result

        weatherRepository.result = Result.failure(DomainError.Offline(IOException("offline")))
        vm.onIntent(HomeIntent.Refresh)
        runCurrent()
        assertTrue((vm.state.value as HomeUiState.Content).refreshFailed)

        weatherRepository.result = good
        vm.onIntent(HomeIntent.Refresh)
        runCurrent()
        assertTrue(!(vm.state.value as HomeUiState.Content).refreshFailed)
    }

    @Test
    fun `an initial failure still surfaces the full-screen error state`() = runTest {
        weatherRepository.result = Result.failure(DomainError.ApiFailure(IllegalStateException("500")))

        val vm = viewModel()

        // The screen renders this error inline, so it must not also fire a snackbar with the same
        // copy: that showed the same sentence twice.
        vm.effects.test { expectNoEvents() }
        assertTrue(vm.state.value is HomeUiState.Error)
    }

    @Test
    fun `open location search is exposed as an effect and is recoverable from empty`() = runTest {
        userRepository.setProfile(UserProfile(selectedLocation = null))
        locationRepository.lastKnown = Result.failure(IOException("no fix"))
        val vm = viewModel()
        assertEquals(HomeUiState.Empty, vm.state.value)

        vm.onIntent(HomeIntent.OpenLocationSearch)

        vm.effects.test {
            assertEquals(HomeEffect.OpenLocationSearch, awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `profile changes recompute the recommendation`() = runTest {
        val vm = viewModel()
        assertEquals(1, weatherRepository.calls)

        userRepository.setProfile(
            userRepository.profile.value.copy(ageBand = AgeBand.SENIOR_60_PLUS)
        )

        assertEquals(2, weatherRepository.calls)
        val content = vm.state.value as HomeUiState.Content
        assertEquals(AgeBand.SENIOR_60_PLUS, content.outfit.profile.ageBand)
    }

    @Test
    fun `retrying after an error recovers the dashboard`() = runTest {
        weatherRepository.result = Result.failure(DomainError.Offline(IOException("offline")))
        val vm = viewModel()
        assertTrue(vm.state.value is HomeUiState.Error)

        weatherRepository.result = Result.success(weatherData())
        vm.onIntent(HomeIntent.Refresh)

        assertTrue(vm.state.value is HomeUiState.Content)
        assertEquals(2, weatherRepository.calls)
    }

    @Test
    fun `state is a single hot stream that does not re-trigger IO per collector`() = runTest {
        val vm = viewModel()
        val callsAfterLoad = weatherRepository.calls

        // A second collector (e.g. rotation, a nested composable) must not cause work.
        vm.state.test {
            assertTrue(awaitItem() is HomeUiState.Content)
            cancelAndIgnoreRemainingEvents()
        }

        assertEquals(callsAfterLoad, weatherRepository.calls)
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
        var lastForceRefresh: Boolean? = null
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun getTodayWeather(
            location: LocationInfo,
            forceRefresh: Boolean
        ): Result<WeatherData> {
            calls++
            lastRequestedLocation = location
            lastForceRefresh = forceRefresh
            gate?.await()
            return result
        }

        override suspend fun getCachedWeather(): WeatherData? = result.getOrNull()
    }

    private class FakeWardrobeRepository : WardrobeRepository {
        private val items = MutableStateFlow<List<WardrobeItem>>(emptyList())
        var observeCalls = 0
            private set

        fun emit(items: List<WardrobeItem>) {
            this.items.value = items
        }

        override fun observeAll(): Flow<List<WardrobeItem>> {
            observeCalls++
            return items
        }

        override fun observeByCategory(category: LayerCategory): Flow<List<WardrobeItem>> =
            items.map { all -> all.filter { it.category == category } }

        override suspend fun upsert(item: WardrobeItem) {
            items.value = items.value.filterNot { it.id == item.id } + item
        }

        override suspend fun delete(id: String) {
            items.value = items.value.filterNot { it.id == id }
        }
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

        override suspend fun getCurrentLocation(): Result<LocationInfo> = lastKnown

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> =
            Result.success(emptyList())
    }
}
