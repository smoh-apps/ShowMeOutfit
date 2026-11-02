package com.smoh.showmeoutfit.ui.permissions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The screen only renders one notice at a time, so the priority between the two missing
 * capabilities is part of the contract, not an implementation detail.
 */
class AlertsPermissionStateTest {

    @Test
    fun `a fully allowed device needs no notice`() {
        val state = AlertsPermissionState(
            notificationsAllowed = true,
            exactAlarmsAllowed = true
        )

        assertTrue(state.isSatisfied)
        assertNull(state.firstMissing)
    }

    @Test
    fun `notifications come before exact alarms`() {
        val state = AlertsPermissionState(
            notificationsAllowed = false,
            exactAlarmsAllowed = false
        )

        assertFalse(state.isSatisfied)
        assertEquals(AlertsRequirement.NOTIFICATIONS, state.firstMissing)
    }

    @Test
    fun `exact alarms surface only once notifications are allowed`() {
        val state = AlertsPermissionState(
            notificationsAllowed = true,
            exactAlarmsAllowed = false
        )

        assertFalse(state.isSatisfied)
        assertEquals(AlertsRequirement.EXACT_ALARMS, state.firstMissing)
    }

    @Test
    fun `defaults assume the platform allows everything`() {
        // Previews and Compose tests omit the parameter; the optimistic default must stay
        // satisfied so they never render a permission card by accident.
        val state = AlertsPermissionState()

        assertTrue(state.isSatisfied)
        assertNull(state.firstMissing)
    }
}
