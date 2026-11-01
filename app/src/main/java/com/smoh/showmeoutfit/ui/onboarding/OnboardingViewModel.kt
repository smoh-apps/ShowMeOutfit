package com.smoh.showmeoutfit.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.usecase.ManageUserProfileUseCase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * First-run profile form.
 *
 * Only the age *band* is ever persisted; the typed number stays in this ViewModel and is never
 * written to disk. A save is serialized (one at a time) so double-tapping the primary action
 * cannot interleave two whole-profile writes.
 */
class OnboardingViewModel(
    private val manageUserProfile: ManageUserProfileUseCase,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingUiState())
    val state: StateFlow<OnboardingUiState> = _state.asStateFlow()

    private val _effects = Channel<OnboardingEffect>(Channel.BUFFERED)
    val effects: Flow<OnboardingEffect> = _effects.receiveAsFlow()

    private var saveJob: Job? = null

    fun onIntent(intent: OnboardingIntent) {
        when (intent) {
            is OnboardingIntent.GenderSelected -> _state.update {
                it.copy(gender = intent.gender, saveFailed = false)
            }

            is OnboardingIntent.AgeChanged -> onAgeChanged(intent.input)

            is OnboardingIntent.SensitivitySelected -> _state.update {
                it.copy(thermalSensitivity = intent.sensitivity, saveFailed = false)
            }

            OnboardingIntent.Submit -> submit()

            OnboardingIntent.Skip -> skip()
        }
    }

    private fun onAgeChanged(raw: String) {
        // Keep digits only so pasted text ("34 years") still works on a numeric keyboard.
        val digits = raw.filter { it.isDigit() }
        val age = digits.toIntOrNull()
        val error = when {
            digits.isEmpty() -> null
            digits.length > MAX_AGE_DIGITS || age == null || age > MAX_AGE -> AgeInputError.OUT_OF_RANGE
            else -> null
        }

        _state.update {
            it.copy(
                ageInput = digits,
                ageBand = if (error == null) AgeBand.fromYears(age) else AgeBand.UNSPECIFIED,
                ageError = error,
                saveFailed = false
            )
        }
    }

    private fun submit() {
        val form = _state.value
        if (!form.canContinue || saveJob?.isActive == true) return

        persist { existing ->
            existing.copy(
                genderPreference = form.gender,
                ageBand = form.ageBand,
                thermalSensitivity = form.thermalSensitivity,
                isConfigured = true
            )
        }
    }

    private fun skip() {
        if (_state.value.isSaving || saveJob?.isActive == true) return
        // Skipping accepts the stored defaults; the form's unconfirmed edits are not applied.
        persist { existing -> existing.copy(isConfigured = true) }
    }

    /**
     * Reads the latest profile, applies [transform], and writes it back as one unit. Saving last
     * prevents a stale form from wiping location or notification choices made elsewhere.
     */
    private fun persist(transform: (UserProfile) -> UserProfile) {
        _state.update { it.copy(isSaving = true, saveFailed = false) }

        saveJob = viewModelScope.launch {
            val result = try {
                withContext(ioDispatcher) {
                    val existing = manageUserProfile.getUserProfile().first()
                    manageUserProfile.saveUserProfile(transform(existing))
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Throwable) {
                Result.failure(error)
            }

            _state.update {
                it.copy(isSaving = false, saveFailed = result.isFailure)
            }

            if (result.isSuccess) {
                _effects.send(OnboardingEffect.Completed)
            }
        }
    }

    private companion object {
        const val MAX_AGE = 120

        /** Three digits covers 0–120; anything longer is rejected rather than truncated. */
        const val MAX_AGE_DIGITS = 3
    }
}
