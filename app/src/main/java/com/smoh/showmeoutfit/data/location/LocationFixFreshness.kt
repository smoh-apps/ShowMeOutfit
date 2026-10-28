package com.smoh.showmeoutfit.data.location

/**
 * Policy that decides whether a platform location fix is recent enough to describe where the
 * user is *now*.
 *
 * A cached "last known" fix is only a safe substitute for a fresh one while it is recent: a fix
 * from a previous trip would otherwise pin the forecast to a different city indefinitely. The
 * policy lives here, free of Android types, so it is unit-testable on the JVM.
 */
object LocationFixFreshness {

    /**
     * Fifteen minutes covers normal app switching and short trips while staying far below the
     * time it takes to change city.
     */
    const val MAX_AGE_MILLIS: Long = 15 * 60 * 1000L

    /**
     * @param fixTimeMillis the fix timestamp reported by the platform (`Location.time`).
     * @param nowMillis the current wall clock.
     *
     * An unknown (non-positive) or future timestamp is never trusted: on a clock change the safe
     * answer is to request a fresh fix, not to reuse a fix we cannot date.
     */
    fun isRecentEnough(
        fixTimeMillis: Long,
        nowMillis: Long,
        maxAgeMillis: Long = MAX_AGE_MILLIS
    ): Boolean {
        if (fixTimeMillis <= 0L) return false
        val age = nowMillis - fixTimeMillis
        if (age < 0L) return false
        return age <= maxAgeMillis
    }
}
