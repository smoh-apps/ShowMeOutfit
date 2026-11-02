package com.smoh.showmeoutfit.ui.onboarding

import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity

/** Why the typed age cannot be saved. Copy is resolved by the UI, never here. */
enum class AgeInputError {
    OUT_OF_RANGE
}

data class OnboardingUiState(
    val gender: GenderPreference = GenderPreference.ALL,
    val ageInput: String = "",
    val ageBand: AgeBand = AgeBand.UNSPECIFIED,
    val ageError: AgeInputError? = null,
    val thermalSensitivity: ThermalSensitivity = ThermalSensitivity.NEUTRAL,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false
) {
    /**
     * A blank age is allowed: the app keeps [AgeBand.UNSPECIFIED] and falls back to the neutral
     * comfort buffer. Only a malformed or out-of-range number blocks the primary action.
     */
    val canContinue: Boolean
        get() = !isSaving && ageError == null
}

sealed interface OnboardingIntent {
    data class GenderSelected(val gender: GenderPreference) : OnboardingIntent

    /** Raw text from the age field; the ViewModel filters and validates it. */
    data class AgeChanged(val input: String) : OnboardingIntent

    data class SensitivitySelected(val sensitivity: ThermalSensitivity) : OnboardingIntent

    data object Submit : OnboardingIntent

    /** Accepting the defaults still marks the profile configured, so onboarding is not repeated. */
    data object Skip : OnboardingIntent
}

sealed interface OnboardingEffect {
    data object Completed : OnboardingEffect
}
