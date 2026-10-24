package com.smoh.showmeoutfit.domain.model

import java.time.LocalTime

enum class GenderPreference {
    ALL,
    MEN,
    WOMEN
}

enum class ThermalSensitivity(val tempOffsetCelsius: Double) {
    FEELS_COLD_EASILY(+2.5),
    NEUTRAL(0.0),
    FEELS_WARM_EASILY(-2.5)
}

data class UserProfile(
    val genderPreference: GenderPreference = GenderPreference.ALL,
    val thermalSensitivity: ThermalSensitivity = ThermalSensitivity.NEUTRAL,
    val morningNotificationTime: LocalTime = LocalTime.of(7, 0),
    val isNotificationEnabled: Boolean = true,
    val selectedLocation: LocationInfo? = null
)
