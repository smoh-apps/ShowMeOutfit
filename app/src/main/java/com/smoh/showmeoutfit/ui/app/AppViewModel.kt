package com.smoh.showmeoutfit.ui.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.ui.AppRoutes
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Decides where the application graph starts exactly once per process.
 *
 * The decision is deliberately a one-shot read instead of a live observer: profile edits made in
 * Settings must not recreate the `NavHost` graph while the user is in it. Onboarding itself
 * navigates away when it completes, and the start destination stays fixed for the life of the
 * activity.
 */
sealed interface AppStartState {
    data object Loading : AppStartState

    data class Ready(val startDestination: String) : AppStartState
}

class AppViewModel(
    private val userRepository: UserRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow<AppStartState>(AppStartState.Loading)
    val state: StateFlow<AppStartState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            _state.value = AppStartState.Ready(resolveStartDestination())
        }
    }

    private suspend fun resolveStartDestination(): String {
        val profile = try {
            withContext(ioDispatcher) { userRepository.getUserProfile().first() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // An unreadable profile must not trap the user on a blank screen. Onboarding can be
            // completed without reading the old value, and it persists a fresh profile.
            null
        }

        return if (profile?.isConfigured == true) AppRoutes.HOME else AppRoutes.ONBOARDING
    }
}
