package com.smoh.showmeoutfit.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.usecase.ManageUserProfileUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Settings is a projection of the persisted profile, not a second copy of it.
 *
 * Every edit re-reads the latest profile inside a [Mutex] and writes the whole entity back, so two
 * quick changes (toggle then time) cannot overwrite each other with a stale snapshot. The UI also
 * re-renders from the repository flow, which keeps this screen consistent with onboarding and with
 * any background schedule change.
 */
class SettingsViewModel(
    private val manageUserProfile: ManageUserProfileUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val writeMutex = Mutex()

    init {
        viewModelScope.launch {
            manageUserProfile.getUserProfile().collect { profile ->
                _state.update { it.copy(profile = profile, isLoading = false) }
            }
        }
    }

    fun onIntent(intent: SettingsIntent) {
        when (intent) {
            is SettingsIntent.SetNotificationsEnabled ->
                updateProfile { it.copy(isNotificationEnabled = intent.enabled) }

            is SettingsIntent.SetNotificationTime ->
                updateProfile { it.copy(morningNotificationTime = intent.time) }

            is SettingsIntent.SetThermalSensitivity ->
                updateProfile { it.copy(thermalSensitivity = intent.sensitivity) }

            is SettingsIntent.SetGender ->
                updateProfile { it.copy(genderPreference = intent.gender) }

            is SettingsIntent.SetAge -> {
                val years = intent.years
                if (years != null && years !in MIN_AGE..MAX_AGE) return
                updateProfile { it.copy(ageBand = AgeBand.fromYears(years)) }
            }

            SettingsIntent.ResetLocationToGps ->
                updateProfile { it.copy(selectedLocation = null) }

            SettingsIntent.DismissSaveError ->
                _state.update { it.copy(saveFailed = false) }
        }
    }

    private fun updateProfile(transform: (UserProfile) -> UserProfile) {
        viewModelScope.launch {
            writeMutex.withLock {
                _state.update { it.copy(isSaving = true, saveFailed = false) }

                val result = try {
                    withContext(ioDispatcher) {
                        // Read *inside* the lock: the form may have been open for minutes.
                        val latest = manageUserProfile.getUserProfile().first()
                        manageUserProfile.saveUserProfile(transform(latest))
                    }
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (error: Throwable) {
                    Result.failure(error)
                }

                _state.update {
                    it.copy(isSaving = false, saveFailed = result.isFailure)
                }
            }
        }
    }

    private companion object {
        const val MIN_AGE = 0
        const val MAX_AGE = 120
    }
}
