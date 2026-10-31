package com.smoh.showmeoutfit.domain.personalization

import com.smoh.showmeoutfit.domain.model.AgeBand
import com.smoh.showmeoutfit.domain.model.GarmentFit
import com.smoh.showmeoutfit.domain.model.GenderPreference
import com.smoh.showmeoutfit.domain.model.PriorityLevel
import com.smoh.showmeoutfit.domain.model.ThermalSensitivity
import com.smoh.showmeoutfit.domain.model.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonalizationPolicyTest {

    // --- Age band mapping ---

    @Test
    fun `age mapping handles every band boundary`() {
        assertEquals(AgeBand.UNSPECIFIED, AgeBand.fromYears(null))
        assertEquals(AgeBand.UNSPECIFIED, AgeBand.fromYears(-1))
        assertEquals(AgeBand.CHILD_0_5, AgeBand.fromYears(0))
        assertEquals(AgeBand.CHILD_0_5, AgeBand.fromYears(5))
        assertEquals(AgeBand.CHILD_6_12, AgeBand.fromYears(6))
        assertEquals(AgeBand.CHILD_6_12, AgeBand.fromYears(12))
        assertEquals(AgeBand.TEEN_13_19, AgeBand.fromYears(13))
        assertEquals(AgeBand.TEEN_13_19, AgeBand.fromYears(19))
        assertEquals(AgeBand.ADULT_20_39, AgeBand.fromYears(20))
        assertEquals(AgeBand.ADULT_20_39, AgeBand.fromYears(39))
        assertEquals(AgeBand.ADULT_40_59, AgeBand.fromYears(40))
        assertEquals(AgeBand.ADULT_40_59, AgeBand.fromYears(59))
        assertEquals(AgeBand.SENIOR_60_PLUS, AgeBand.fromYears(60))
        assertEquals(AgeBand.SENIOR_60_PLUS, AgeBand.fromYears(120))
    }

    // --- Thermal offset ---

    @Test
    fun `neutral profile has no thermal offset`() {
        assertEquals(0.0, PersonalizationPolicy.thermalOffsetCelsius(UserProfile()), 0.001)
    }

    @Test
    fun `sensitivity and age buffer combine`() {
        val profile = UserProfile(
            ageBand = AgeBand.CHILD_0_5,
            thermalSensitivity = ThermalSensitivity.FEELS_COLD_EASILY
        )

        assertEquals(3.5, PersonalizationPolicy.thermalOffsetCelsius(profile), 0.001)
    }

    @Test
    fun `warm sensitivity with a senior buffer stays negative`() {
        val profile = UserProfile(
            ageBand = AgeBand.SENIOR_60_PLUS,
            thermalSensitivity = ThermalSensitivity.FEELS_WARM_EASILY
        )

        assertEquals(-1.5, PersonalizationPolicy.thermalOffsetCelsius(profile), 0.001)
    }

    @Test
    fun `offset never exceeds the clamp`() {
        val extreme = UserProfile(
            ageBand = AgeBand.CHILD_0_5,
            thermalSensitivity = ThermalSensitivity.FEELS_COLD_EASILY
        )

        assertTrue(
            PersonalizationPolicy.thermalOffsetCelsius(extreme) <=
                PersonalizationPolicy.MAX_TOTAL_OFFSET_CELSIUS
        )
        assertTrue(
            PersonalizationPolicy.thermalOffsetCelsius(extreme) >=
                -PersonalizationPolicy.MAX_TOTAL_OFFSET_CELSIUS
        )
    }

    // --- Fit ---

    @Test
    fun `fit preference maps to catalog variant`() {
        assertEquals(GarmentFit.UNISEX, PersonalizationPolicy.fitFor(GenderPreference.ALL))
        assertEquals(GarmentFit.MEN, PersonalizationPolicy.fitFor(GenderPreference.MEN))
        assertEquals(GarmentFit.WOMEN, PersonalizationPolicy.fitFor(GenderPreference.WOMEN))
    }

    // --- Safety escalation ---

    @Test
    fun `children and seniors are safety priority age bands`() {
        assertTrue(AgeBand.CHILD_0_5.hasSafetyPriority)
        assertTrue(AgeBand.CHILD_6_12.hasSafetyPriority)
        assertTrue(AgeBand.SENIOR_60_PLUS.hasSafetyPriority)
        assertFalse(AgeBand.TEEN_13_19.hasSafetyPriority)
        assertFalse(AgeBand.ADULT_20_39.hasSafetyPriority)
        assertFalse(AgeBand.ADULT_40_59.hasSafetyPriority)
        assertFalse(AgeBand.UNSPECIFIED.hasSafetyPriority)
    }

    @Test
    fun `safety escalation only promotes recommended items`() {
        assertEquals(
            PriorityLevel.CRITICAL,
            PersonalizationPolicy.escalateForSafety(PriorityLevel.RECOMMENDED, AgeBand.CHILD_6_12)
        )
        assertEquals(
            PriorityLevel.RECOMMENDED,
            PersonalizationPolicy.escalateForSafety(PriorityLevel.RECOMMENDED, AgeBand.ADULT_20_39)
        )
        assertEquals(
            PriorityLevel.OPTIONAL,
            PersonalizationPolicy.escalateForSafety(PriorityLevel.OPTIONAL, AgeBand.CHILD_0_5)
        )
        assertEquals(
            PriorityLevel.CRITICAL,
            PersonalizationPolicy.escalateForSafety(PriorityLevel.CRITICAL, AgeBand.CHILD_0_5)
        )
    }
}
