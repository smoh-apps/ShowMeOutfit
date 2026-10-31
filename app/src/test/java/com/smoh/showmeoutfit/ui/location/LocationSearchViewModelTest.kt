package com.smoh.showmeoutfit.ui.location

import app.cash.turbine.test
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.usecase.SearchLocationUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class LocationSearchViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private lateinit var locationRepository: FakeLocationRepository
    private lateinit var userRepository: FakeUserRepository

    private val berlin = LocationInfo(52.52, 13.405, "Berlin", "Germany", "Berlin", "Europe/Berlin")
    private val paris = LocationInfo(48.8566, 2.3522, "Paris", "France", "Île-de-France", "Europe/Paris")

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        locationRepository = FakeLocationRepository(listOf(berlin))
        userRepository = FakeUserRepository(UserProfile())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = LocationSearchViewModel(
        searchLocation = SearchLocationUseCase(locationRepository),
        locationRepository = locationRepository,
        userRepository = userRepository,
        ioDispatcher = dispatcher
    )

    // --- Debounce ---

    @Test
    fun `typing does not search before the debounce window elapses`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(LocationSearchIntent.QueryChanged("Ber"))
        advanceTimeBy(LocationSearchViewModel.DEBOUNCE_MILLIS - 1)
        runCurrent()

        assertEquals(0, locationRepository.searchCalls)
        assertTrue(vm.state.value.isSearching || !vm.state.value.hasSearched)

        advanceTimeBy(2)
        runCurrent()

        assertEquals(1, locationRepository.searchCalls)
        assertEquals(listOf(berlin), vm.state.value.results)
    }

    @Test
    fun `a newer keystroke cancels the pending search`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(LocationSearchIntent.QueryChanged("Ber"))
        advanceTimeBy(100)
        vm.onIntent(LocationSearchIntent.QueryChanged("Berli"))
        advanceTimeBy(100)
        vm.onIntent(LocationSearchIntent.QueryChanged("Berlin"))

        advanceTimeBy(LocationSearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()

        assertEquals(1, locationRepository.searchCalls)
        assertEquals("Berlin", locationRepository.lastQuery)
    }

    @Test
    fun `the search action skips the debounce`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(LocationSearchIntent.QueryChanged("Berlin"))
        vm.onIntent(LocationSearchIntent.SearchNow)
        runCurrent()

        assertEquals(1, locationRepository.searchCalls)
    }

    @Test
    fun `an in-flight search never overwrites the results of a newer query`() = runTest(dispatcher) {
        val vm = viewModel()
        val gate = CompletableDeferred<Unit>()
        locationRepository.gateForQuery = "Ber" to gate
        locationRepository.resultFor = { query ->
            if (query == "Ber") listOf(berlin) else listOf(paris)
        }

        vm.onIntent(LocationSearchIntent.QueryChanged("Ber"))
        advanceTimeBy(LocationSearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent() // "Ber" is now blocked inside the repository

        vm.onIntent(LocationSearchIntent.QueryChanged("Paris"))
        advanceTimeBy(LocationSearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()

        assertEquals("Paris", vm.state.value.query)
        assertEquals(listOf(paris), vm.state.value.results)

        // Release the abandoned request; it must not land.
        gate.complete(Unit)
        runCurrent()

        assertEquals("Paris", vm.state.value.query)
        assertEquals(listOf(paris), vm.state.value.results)
        assertTrue(!vm.state.value.isSearching)
    }

    // --- Query lifecycle ---

    @Test
    fun `clearing the query resets results without searching`() = runTest(dispatcher) {
        val vm = viewModel()
        vm.onIntent(LocationSearchIntent.QueryChanged("Berlin"))
        advanceTimeBy(LocationSearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()

        vm.onIntent(LocationSearchIntent.QueryChanged(""))
        runCurrent()

        assertTrue(vm.state.value.results.isEmpty())
        assertTrue(!vm.state.value.hasSearched)
        assertEquals(1, locationRepository.searchCalls)
    }

    @Test
    fun `empty results are reported as an empty search, not an error`() = runTest(dispatcher) {
        val vm = viewModel()
        locationRepository.searchHandler = { Result.success(emptyList()) }

        vm.onIntent(LocationSearchIntent.QueryChanged("Atlantis"))
        advanceTimeBy(LocationSearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()

        val state = vm.state.value
        assertTrue(state.hasSearched)
        assertTrue(state.showsEmptyResults)
        assertNull(state.error)
    }

    @Test
    fun `a search failure is surfaced as a typed error`() = runTest(dispatcher) {
        val vm = viewModel()
        // The repository owns the mapping; the ViewModel must pass the typed error through.
        locationRepository.searchHandler = { Result.failure(DomainError.Offline(IOException("offline"))) }

        vm.onIntent(LocationSearchIntent.QueryChanged("Berlin"))
        advanceTimeBy(LocationSearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()

        assertTrue(vm.state.value.error is DomainError.Offline)
        assertTrue(!vm.state.value.showsEmptyResults)
        assertTrue(!vm.state.value.isSearching)
    }

    // --- Current location ---

    @Test
    fun `using the current location persists it and emits a selection`() = runTest(dispatcher) {
        val vm = viewModel()
        locationRepository.currentResult = Result.success(berlin)

        vm.onIntent(LocationSearchIntent.UseCurrentLocation)
        runCurrent()

        vm.effects.test {
            assertEquals(LocationSearchEffect.LocationSelected(berlin), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
        assertEquals(berlin, userRepository.profile.value.selectedLocation)
        assertTrue(!vm.state.value.isLocating)
    }

    @Test
    fun `a denied permission is a recoverable state, not a crash`() = runTest(dispatcher) {
        val vm = viewModel()
        locationRepository.currentResult = Result.failure(DomainError.LocationPermissionDenied())

        vm.onIntent(LocationSearchIntent.UseCurrentLocation)
        runCurrent()

        assertTrue(vm.state.value.permissionDenied)
        assertTrue(vm.state.value.error is DomainError.LocationPermissionDenied)
        assertTrue(!vm.state.value.isLocating)
        assertNull(userRepository.profile.value.selectedLocation)
    }

    @Test
    fun `a location timeout is surfaced without touching the persisted profile`() = runTest(dispatcher) {
        val vm = viewModel()
        locationRepository.currentResult = Result.failure(DomainError.LocationUnavailable())

        vm.onIntent(LocationSearchIntent.UseCurrentLocation)
        runCurrent()

        assertTrue(vm.state.value.error is DomainError.LocationUnavailable)
        assertTrue(!vm.state.value.permissionDenied)
        assertNull(userRepository.profile.value.selectedLocation)
    }

    @Test
    fun `the permission denied intent sets the manual search fallback state`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(LocationSearchIntent.LocationPermissionDenied)

        assertTrue(vm.state.value.permissionDenied)
        assertTrue(vm.state.value.error is DomainError.LocationPermissionDenied)
    }

    // --- Selection ---

    @Test
    fun `selecting a search result persists it immediately`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.onIntent(LocationSearchIntent.SelectLocation(paris))
        runCurrent()

        assertEquals(paris, userRepository.profile.value.selectedLocation)
        vm.effects.test {
            assertEquals(LocationSearchEffect.LocationSelected(paris), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed persistence is reported and never emits a selection`() = runTest(dispatcher) {
        val vm = viewModel()
        userRepository.writeResult = Result.failure(DomainError.Unknown(IOException("disk full")))

        vm.onIntent(LocationSearchIntent.SelectLocation(paris))
        runCurrent()

        // The sheet must stay open, so the dashboard cannot end up showing the old city.
        vm.effects.test {
            expectNoEvents()
        }
        assertTrue(vm.state.value.error != null)
        assertNull(userRepository.profile.value.selectedLocation)
    }

    @Test
    fun `a throwing profile store does not crash the view model`() = runTest(dispatcher) {
        val vm = viewModel()
        userRepository.throwOnWrite = IOException("datastore closed")

        vm.onIntent(LocationSearchIntent.SelectLocation(paris))
        runCurrent()

        vm.effects.test { expectNoEvents() }
        assertTrue(vm.state.value.error != null)
    }

    @Test
    fun `a throwing location repository is surfaced as an error, not a crash`() = runTest(dispatcher) {
        val vm = viewModel()
        locationRepository.throwOnCurrent = IllegalStateException("binder died")

        vm.onIntent(LocationSearchIntent.UseCurrentLocation)
        runCurrent()

        assertTrue(vm.state.value.error != null)
        assertTrue(!vm.state.value.isLocating)
    }

    @Test
    fun `dismissing an error leaves the query and results intact`() = runTest(dispatcher) {
        val vm = viewModel()
        locationRepository.searchHandler = { Result.failure(IOException("offline")) }
        vm.onIntent(LocationSearchIntent.QueryChanged("Berlin"))
        advanceTimeBy(LocationSearchViewModel.DEBOUNCE_MILLIS + 1)
        runCurrent()
        assertTrue(vm.state.value.error != null)

        vm.onIntent(LocationSearchIntent.DismissError)

        assertNull(vm.state.value.error)
        assertEquals("Berlin", vm.state.value.query)
    }

    // --- Fakes ---

    private class FakeLocationRepository(
        private val defaultResults: List<LocationInfo>
    ) : LocationRepository {
        /** Overrides the default success path when set; otherwise [resultFor] decides. */
        var searchHandler: (suspend (String) -> Result<List<LocationInfo>>)? = null
        var gateForQuery: Pair<String, CompletableDeferred<Unit>>? = null
        var resultFor: (String) -> List<LocationInfo> = { defaultResults }
        var searchCalls = 0
        var lastQuery: String? = null
        var currentResult: Result<LocationInfo> =
            Result.failure(DomainError.LocationUnavailable())
        var throwOnCurrent: Throwable? = null

        override suspend fun getCurrentLocation(): Result<LocationInfo> {
            throwOnCurrent?.let { throw it }
            return currentResult
        }

        override suspend fun searchLocation(query: String): Result<List<LocationInfo>> {
            searchCalls++
            lastQuery = query
            gateForQuery?.let { (gatedQuery, gate) ->
                if (query == gatedQuery) gate.await()
            }
            searchHandler?.let { return it(query) }
            return Result.success(resultFor(query))
        }
    }

    private class FakeUserRepository(initial: UserProfile) : UserRepository {
        val profile = MutableStateFlow(initial)
        var writeResult: Result<Unit> = Result.success(Unit)
        var throwOnWrite: Throwable? = null

        override fun getUserProfile(): Flow<UserProfile> = profile

        override suspend fun updateUserProfile(profile: UserProfile): Result<Unit> {
            throwOnWrite?.let { throw it }
            return writeResult.also {
                if (it.isSuccess) this.profile.value = profile
            }
        }
    }
}
