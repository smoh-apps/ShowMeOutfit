package com.smoh.showmeoutfit.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoh.showmeoutfit.domain.model.DomainError
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Drives the Home screen through a single state stream.
 *
 * Guarantees:
 * - At most one recommendation request is in flight; extra [HomeIntent.Load]/[HomeIntent.Refresh]
 *   calls are dropped instead of queued (no duplicate network calls on recomposition/rotation).
 * - Profile changes (fit, age band, sensitivity) recompute the recommendation automatically.
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

    init {
        observeProfileChanges()
        onIntent(HomeIntent.Load)
    }

    fun onIntent(intent: HomeIntent) {
        when (intent) {
            HomeIntent.Load -> load(forceRefresh = false)

            HomeIntent.Refresh -> load(forceRefresh = true)

            is HomeIntent.SelectLocation -> selectLocation(intent)

            HomeIntent.DismissError -> {
                if (_state.value is HomeUiState.Error) {
                    load(forceRefresh = false)
                }
            }
        }
    }

    private fun load(forceRefresh: Boolean) {
        if (loadJob?.isActive == true) return

        loadJob = viewModelScope.launch {
            val current = _state.value
            _state.value = when {
                current is HomeUiState.Content && forceRefresh -> current.copy(isRefreshing = true)
                current is HomeUiState.Content -> current
                else -> HomeUiState.Loading
            }

            val result = withContext(ioDispatcher) {
                getTodayOutfit(forceRefresh = forceRefresh)
            }

            _state.value = result.fold(
                onSuccess = { HomeUiState.Content(outfit = it) },
                onFailure = { failure -> failure.toUiState() }
            )
        }
    }

    private fun selectLocation(intent: HomeIntent.SelectLocation) {
        viewModelScope.launch {
            val profile = userRepository.getUserProfile().first()
            if (profile.selectedLocation == intent.location) return@launch
            userRepository.updateUserProfile(profile.copy(selectedLocation = intent.location))
            // The profile observer triggers the reload, so a new city is fetched exactly once.
        }
    }

    private fun observeProfileChanges() {
        viewModelScope.launch {
            userRepository.getUserProfile()
                .drop(1) // The initial emission is covered by the first Load.
                .collect { load(forceRefresh = false) }
        }
    }

    private fun Throwable.toUiState(): HomeUiState = when (val error = toDomainError()) {
        is DomainError.NoLocation -> HomeUiState.Empty
        else -> {
            _effects.trySend(HomeEffect.ShowMessage(error))
            HomeUiState.Error(error)
        }
    }

    private fun Throwable.toDomainError(): DomainError =
        this as? DomainError ?: DomainError.Unknown(this)
}
