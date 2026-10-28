package com.smoh.showmeoutfit.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.usecase.SearchLocationUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the city-search sheet.
 *
 * - Typing waits [DEBOUNCE_MILLIS] before searching, and a newer keystroke cancels the pending
 *   request; a response that arrives after the query changed is discarded (stale-query guard).
 * - Selecting a city persists it immediately, so the choice survives a process death even if the
 *   dashboard never gets a chance to react.
 * - Denied permissions are a state, never an exception: manual search stays usable.
 */
class LocationSearchViewModel(
    private val searchLocation: SearchLocationUseCase,
    private val locationRepository: LocationRepository,
    private val userRepository: UserRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    companion object {
        /** Long enough to avoid a request per keystroke, short enough to feel immediate. */
        const val DEBOUNCE_MILLIS: Long = 350
    }

    private val _state = MutableStateFlow(LocationSearchUiState())
    val state: StateFlow<LocationSearchUiState> = _state.asStateFlow()

    private val _effects = Channel<LocationSearchEffect>(Channel.BUFFERED)
    val effects: Flow<LocationSearchEffect> = _effects.receiveAsFlow()

    private var searchJob: Job? = null
    private var searchSequence = 0L

    fun onIntent(intent: LocationSearchIntent) {
        when (intent) {
            is LocationSearchIntent.QueryChanged -> onQueryChanged(intent.query)

            LocationSearchIntent.SearchNow -> onSearchNow()

            LocationSearchIntent.UseCurrentLocation -> useCurrentLocation()

            LocationSearchIntent.LocationPermissionDenied -> onPermissionDenied()

            is LocationSearchIntent.SelectLocation -> selectLocation(intent.location)

            LocationSearchIntent.DismissError -> _state.value = _state.value.copy(error = null)
        }
    }

    private fun onQueryChanged(query: String) {
        searchJob?.cancel()
        _state.value = _state.value.copy(query = query, error = null)

        if (query.isBlank()) {
            _state.value = _state.value.copy(
                isSearching = false,
                hasSearched = false,
                results = emptyList()
            )
            return
        }

        searchJob = viewModelScope.launch {
            delay(DEBOUNCE_MILLIS)
            runSearch(query)
        }
    }

    private fun onSearchNow() {
        val query = _state.value.query
        searchJob?.cancel()
        if (query.isBlank()) return
        searchJob = viewModelScope.launch { runSearch(query) }
    }

    private suspend fun runSearch(query: String) {
        val sequence = ++searchSequence
        _state.value = _state.value.copy(isSearching = true, error = null)

        val result = withContext(ioDispatcher) { searchLocation(query) }

        // A newer query started while this one was in flight: drop the stale answer.
        if (sequence != searchSequence) return

        _state.value = result.fold(
            onSuccess = { locations ->
                _state.value.copy(
                    isSearching = false,
                    hasSearched = true,
                    results = locations,
                    error = null
                )
            },
            onFailure = { failure ->
                _state.value.copy(
                    isSearching = false,
                    hasSearched = true,
                    results = emptyList(),
                    error = failure.toDomainError()
                )
            }
        )
    }

    private fun useCurrentLocation() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLocating = true, error = null, permissionDenied = false)

            val result = withContext(ioDispatcher) { locationRepository.getCurrentLocation() }

            result.fold(
                onSuccess = { location ->
                    _state.value = _state.value.copy(isLocating = false)
                    persistAndEmit(location)
                },
                onFailure = { failure ->
                    val error = failure.toDomainError()
                    _state.value = _state.value.copy(
                        isLocating = false,
                        error = error,
                        permissionDenied = error is DomainError.LocationPermissionDenied
                    )
                }
            )
        }
    }

    private fun onPermissionDenied() {
        _state.value = _state.value.copy(
            isLocating = false,
            permissionDenied = true,
            error = DomainError.LocationPermissionDenied()
        )
    }

    private fun selectLocation(location: LocationInfo) {
        viewModelScope.launch { persistAndEmit(location) }
    }

    private suspend fun persistAndEmit(location: LocationInfo) {
        val profile = withContext(ioDispatcher) { userRepository.getUserProfile().first() }
        withContext(ioDispatcher) {
            userRepository.updateUserProfile(profile.copy(selectedLocation = location))
        }
        _effects.send(LocationSearchEffect.LocationSelected(location))
    }

    private fun Throwable.toDomainError(): DomainError =
        this as? DomainError ?: DomainError.Unknown(this)
}
