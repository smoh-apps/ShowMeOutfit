package com.smoh.showmeoutfit.domain.personalization

import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.UserProfile

/**
 * Single place where personalization inputs become recommendation parameters.
 *
 * Keeping the math here (instead of scattered through the engine) makes the thresholds easy to
 * tune after launch and keeps the engine readable.
 */
object PersonalizationPolicy {

    /** Total thermal adjustment never exceeds this, regardless of combined inputs. */
    const val MAX_TOTAL_OFFSET_CELSIUS: Double = 4.0

    /**
     * Combined thermal sensitivity and age comfort buffer, clamped to ±4 °C.
     *
     * A positive value means "dress warmer than the raw temperature suggests".
     */
    fun thermalOffsetCelsius(profile: UserProfile): Double {
        val combined = profile.thermalSensitivity.tempOffsetCelsius +
            profile.ageBand.comfortBufferCelsius
        return combined.coerceIn(-MAX_TOTAL_OFFSET_CELSIUS, MAX_TOTAL_OFFSET_CELSIUS)
    }

    /** Maps the fit preference to a catalog variant. `ALL` resolves to unisex. */
    fun fitFor(gender: GenderPreference): GarmentFit = when (gender) {
        GenderPreference.ALL -> GarmentFit.UNISEX
        GenderPreference.MEN -> GarmentFit.MEN
        GenderPreference.WOMEN -> GarmentFit.WOMEN
    }

    /**
     * Escalates safety-relevant recommendations for children and seniors.
     *
     * Only `RECOMMENDED` items are promoted; `CRITICAL` stays critical and `OPTIONAL` stays
     * optional so the badge hierarchy does not flatten.
     */
    fun escalateForSafety(
        priority: PriorityLevel,
        ageBand: AgeBand
    ): PriorityLevel {
        if (priority == PriorityLevel.RECOMMENDED && ageBand.hasSafetyPriority) {
            return PriorityLevel.CRITICAL
        }
        return priority
    }
}
