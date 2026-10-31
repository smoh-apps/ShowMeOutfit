package com.smoh.showmeoutfit.data.location

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The last-known-first policy is only safe while a cached fix still describes the current place.
 * These tests pin the boundary so a fix from a previous trip can never pin the forecast elsewhere.
 */
class LocationFixFreshnessTest {

    private val now = 1_700_000_000_000L

    @Test
    fun `a fix from a moment ago is recent enough`() {
        assertTrue(LocationFixFreshness.isRecentEnough(now - 1_000, now))
    }

    @Test
    fun `a fix exactly at the age limit is still accepted`() {
        assertTrue(
            LocationFixFreshness.isRecentEnough(
                fixTimeMillis = now - LocationFixFreshness.MAX_AGE_MILLIS,
                nowMillis = now
            )
        )
    }

    @Test
    fun `a fix one millisecond past the limit is rejected`() {
        assertFalse(
            LocationFixFreshness.isRecentEnough(
                fixTimeMillis = now - LocationFixFreshness.MAX_AGE_MILLIS - 1,
                nowMillis = now
            )
        )
    }

    @Test
    fun `a fix from yesterday is rejected`() {
        assertFalse(
            LocationFixFreshness.isRecentEnough(now - 24 * 60 * 60 * 1000L, now)
        )
    }

    @Test
    fun `an unknown timestamp is never trusted`() {
        assertFalse(LocationFixFreshness.isRecentEnough(fixTimeMillis = 0L, nowMillis = now))
    }

    @Test
    fun `a fix from the future is rejected as undatable`() {
        // Clock changes and bad platform data must fall back to a fresh request.
        assertFalse(LocationFixFreshness.isRecentEnough(now + 60_000, now))
    }

    @Test
    fun `the default limit is fifteen minutes`() {
        assertTrue(LocationFixFreshness.MAX_AGE_MILLIS == 15 * 60 * 1000L)
    }
}
