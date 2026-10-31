package com.smoh.showmeoutfit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoh.showmeoutfit.domain.model.DomainError
import com.smoh.showmeoutfit.domain.model.LocationInfo
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.usecase.GetTodayOutfitRecommendationUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Home screen through a single state stream.
 *
 * Guarantees:
 * - At most one recommendation request is in flight. A trigger that arrives while a request is
 *   running is coalesced into exactly one follow-up request instead of being dropped, so a city
 *   change or a profile edit made mid-refresh is never lost.
 * - Profile changes (fit, age band, sensitivity) recompute the recommendation automatically.
 * - A failed refresh keeps the last good dashboard visible and reports the problem as an effect;
 *   the full-screen [HomeUiState.Error] is reserved for the case with nothing to show.
 * - No `Context`, `Resources`, or formatted copy is produced here; the UI resolves all text.
 */
class HomeViewModel(
    private val getTodayOutfit: GetTodayOutfitRecommendationUseCase,
    private val userRepository: UserRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _effects = Channel<HomeEffect>(Channel.BUFFERED)
    val effects: Flow<HomeEffect> = _effects.receiveAsFlow()

    private var loadJob: Job? = null

    /** A trigger arrived while a request was running and still needs to be served. */
    private var reloadPending = false

    /** Whether the coalesced follow-up request should bypass the cache. */
    private var pendingForceRefresh = false

    init {
        observeProfileChanges()
        onIntent(HomeIntent.Load)
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> load(forceRefresh = false)

            HomeIntent.Refresh -> load(forceRefresh = true)

            is HomeIntent.SelectLocation -> selectLocation(intent.location)

            HomeIntent.DismissError -> {
                if (_state.value is HomeUiState.Error) {
                    load(forceRefresh = false)
                }
            }

            HomeIntent.OpenLocationSearch -> _effects.trySend(HomeEffect.OpenLocationSearch)
        }
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) {
            reloadPending = true
            pendingForceRefresh = pendingForceRefresh || forceRefresh
            return
        }

        loadJob = viewModelScope.launch {
            var force = forceRefresh
            while (isActive) {
                reloadPending = false
                pendingForceRefresh = false

                fetchOnce(forceRefresh = force)

                if (!reloadPending) break
                force = pendingForceRefresh
            }
        }
    }

    private suspend fun fetchOnce(forceRefresh: Boolean) {
        val current = _state.value
        _state.value = when {
            current is HomeUiState.Content && forceRefresh -> current.copy(isRefreshing = true)
            current is HomeUiState.Content -> current
            else -> HomeUiState.Loading
        }

        val result = withContext(ioDispatcher) {
            getTodayOutfit(forceRefresh = forceRefresh)
        }

        val previous = _state.value
        _state.value = result.fold(
            onSuccess = { HomeUiState.Content(outfit = it) },
            onFailure = { failure ->
                val error = failure.toDomainError()
                when {
                    // No usable position and no saved city: picking a city is the only recovery,
                    // whether the user denied the permission, turned location off, or got no fix.
                    error is DomainError.NoLocation ||
                        error is DomainError.LocationPermissionDenied ||
                        error is DomainError.LocationDisabled ||
                        error is DomainError.LocationUnavailable -> HomeUiState.Empty

                    // Keep the last good dashboard on screen; staleness is the banner's job.
                    previous is HomeUiState.Content -> {
                        _effects.trySend(HomeEffect.ShowMessage(error))
                        previous.copy(isRefreshing = false)
                    }

                    else -> {
                        _effects.trySend(HomeEffect.ShowMessage(error))
                        HomeUiState.Error(error)
                    }
                }
            }
        )
    }

    private fun selectLocation(location: LocationInfo) {
        viewModelScope.launch {
            val profile = userRepository.getUserProfile().first()
            if (profile.selectedLocation == location) return@launch
            userRepository.updateUserProfile(profile.copy(selectedLocation = location))
            // The profile observer triggers the reload. If a request is already running the
            // trigger is coalesced, not dropped.
        }
    }

    private fun observeProfileChanges() {
        viewModelScope.launch {
            userRepository.getUserProfile()
                .drop(1) // The initial emission is covered by the first Load.
                .collect { load(forceRefresh = false) }
        }
    }

    private fun Throwable.toDomainError(): DomainError =
        this as? DomainError ?: DomainError.Unknown(this)
}
