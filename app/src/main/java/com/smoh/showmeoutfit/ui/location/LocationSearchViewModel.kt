package com.smoh.showmeoutfit.ui.location

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.repository.LocationRepository
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.usecase.SearchLocationUseCase
import kotlinx.coroutines.CancellationException
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
        _state.value = _state.value.copy(isSearching = true, error = null)

        // Cancellation is what makes a stale answer impossible: every new keystroke cancels the
        // job that owns the previous request, and the repository rethrows CancellationException,
        // so an answer for an old query can never reach the state below.
        val result = try {
            withContext(ioDispatcher) { searchLocation(query) }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error.toDomainError())
        }

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

            val result = try {
                withContext(ioDispatcher) { locationRepository.getCurrentLocation() }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error.toDomainError())
            }

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

    /**
     * Persists [location] and only then tells the caller it is safe to dismiss.
     *
     * A failed write must never emit a selection: the sheet would close and the dashboard would
     * show weather for the old city, which looks like the pick was ignored.
     */
    private suspend fun persistAndEmit(location: LocationInfo) {
        val saved = try {
            val profile = withContext(ioDispatcher) { userRepository.getUserProfile().first() }
            withContext(ioDispatcher) {
                userRepository.updateUserProfile(profile.copy(selectedLocation = location))
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            Result.failure(error.toDomainError())
        }

        saved.fold(
            onSuccess = { _effects.send(LocationSearchEffect.LocationSelected(location)) },
            onFailure = { failure ->
                _state.value = _state.value.copy(
                    isLocating = false,
                    error = failure.toDomainError()
                )
            }
        )
    }

    private fun Throwable.toDomainError(): DomainError =
        this as? DomainError ?: DomainError.Unknown(this)
}
