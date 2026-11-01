package com.smoh.showmeoutfit.ui.onboarding

import androidx.annotation.StringRes
import com.smoh.showmeoutfit.R
import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity

/** Resource mapping only: no formatted copy is produced outside a composable. */
object OnboardingMessageMapper {

    @StringRes
    fun genderLabelRes(gender: GenderPreference): Int = when (gender) {
        GenderPreference.ALL -> R.string.onboarding_gender_all
        GenderPreference.MEN -> R.string.onboarding_gender_men
        GenderPreference.WOMEN -> R.string.onboarding_gender_women
    }

    @StringRes
    fun sensitivityLabelRes(sensitivity: ThermalSensitivity): Int = when (sensitivity) {
        ThermalSensitivity.FEELS_COLD_EASILY -> R.string.onboarding_sensitivity_cold
        ThermalSensitivity.NEUTRAL -> R.string.onboarding_sensitivity_neutral
        ThermalSensitivity.FEELS_WARM_EASILY -> R.string.onboarding_sensitivity_warm
    }

    @StringRes
    fun sensitivityDescriptionRes(sensitivity: ThermalSensitivity): Int = when (sensitivity) {
        ThermalSensitivity.FEELS_COLD_EASILY -> R.string.onboarding_sensitivity_cold_description
        ThermalSensitivity.NEUTRAL -> R.string.onboarding_sensitivity_neutral_description
        ThermalSensitivity.FEELS_WARM_EASILY -> R.string.onboarding_sensitivity_warm_description
    }

    @StringRes
    fun ageBandRes(band: AgeBand): Int = when (band) {
        AgeBand.UNSPECIFIED -> R.string.onboarding_age_band_unspecified
        AgeBand.CHILD_0_5 -> R.string.onboarding_age_band_child_0_5
        AgeBand.CHILD_6_12 -> R.string.onboarding_age_band_child_6_12
        AgeBand.TEEN_13_19 -> R.string.onboarding_age_band_teen_13_19
        AgeBand.ADULT_20_39 -> R.string.onboarding_age_band_adult_20_39
        AgeBand.ADULT_40_59 -> R.string.onboarding_age_band_adult_40_59
        AgeBand.SENIOR_60_PLUS -> R.string.onboarding_age_band_senior_60_plus
    }

    @StringRes
    fun ageErrorRes(error: AgeInputError): Int = when (error) {
        AgeInputError.OUT_OF_RANGE -> R.string.onboarding_age_error_range
    }
}
