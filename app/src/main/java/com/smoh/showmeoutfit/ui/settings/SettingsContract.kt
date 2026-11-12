package com.smoh.showmeoutfit.ui.settings

import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import java.time.LocalTime

data class SettingsUiState(
    val profile: UserProfile = UserProfile(),
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
    /** Language currently shown by the picker; [AppLanguage.SYSTEM] follows the device. */
    val language: AppLanguage = AppLanguage.SYSTEM
)

sealed interface SettingsIntent {
    data class SetNotificationsEnabled(val enabled: Boolean) : SettingsIntent

    data class SetNotificationTime(val time: LocalTime) : SettingsIntent

    data class SetThermalSensitivity(val sensitivity: ThermalSensitivity) : SettingsIntent

    data class SetGender(val gender: GenderPreference) : SettingsIntent

    /** Null clears the band. Out-of-range values are rejected by the ViewModel. */
    data class SetAge(val years: Int?) : SettingsIntent

    /** Drops the saved city so the next recommendation resolves a fresh device fix. */
    data object ResetLocationToGps : SettingsIntent

    /**
     * Switches the app locale. The ViewModel applies it synchronously so the caller can recreate
     * the activity right after this intent is dispatched on API < 33.
     */
    data class SetLanguage(val language: AppLanguage) : SettingsIntent

    data object DismissSaveError : SettingsIntent
}
