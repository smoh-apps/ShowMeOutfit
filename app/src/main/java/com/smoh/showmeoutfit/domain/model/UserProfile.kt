package com.smoh.showmeoutfit.domain.model

import java.time.LocalTime

/**
 * Catalog fit preference. `ALL` means unisex and is the neutral default.
 *
 * Semantics are deliberately narrow: this selects fit/variant labels only. It must never be
 * used to infer thermal physiology, color, or style.
 */
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

/**
 * Age band derived from the numeric age the user enters on first use.
 *
 * Only the band is persisted, never the date of birth or exact age. [comfortBufferCelsius] is a
 * general-comfort adjustment, not medical advice.
 */
enum class AgeBand(val comfortBufferCelsius: Double) {
    UNSPECIFIED(0.0),
    CHILD_0_5(+1.0),
    CHILD_6_12(+0.5),
    TEEN_13_19(0.0),
    ADULT_20_39(0.0),
    ADULT_40_59(+0.5),
    SENIOR_60_PLUS(+1.0);

    /** Children and seniors get earlier safety escalation for UV, wind, and cold. */
    val hasSafetyPriority: Boolean
        get() = this == CHILD_0_5 ||
            this == CHILD_6_12 ||
            this == SENIOR_60_PLUS

    companion object {
        /** Maps a user-entered age in years to a band. Null or negative input is unspecified. */
        fun fromYears(age: Int?): AgeBand = when {
            age == null || age < 0 -> UNSPECIFIED
            age <= 5 -> CHILD_0_5
            age <= 12 -> CHILD_6_12
            age <= 19 -> TEEN_13_19
            age <= 39 -> ADULT_20_39
            age <= 59 -> ADULT_40_59
            else -> SENIOR_60_PLUS
        }
    }
}

data class UserProfile(
    val genderPreference: GenderPreference = GenderPreference.ALL,
    val ageBand: AgeBand = AgeBand.UNSPECIFIED,
    val thermalSensitivity: ThermalSensitivity = ThermalSensitivity.NEUTRAL,
    val morningNotificationTime: LocalTime = LocalTime.of(7, 0),
    val isNotificationEnabled: Boolean = true,
    val selectedLocation: LocationInfo? = null,
    /** False until the Sprint 3 first-run flow (age, fit, sensitivity) completes. */
    val isConfigured: Boolean = false
)
