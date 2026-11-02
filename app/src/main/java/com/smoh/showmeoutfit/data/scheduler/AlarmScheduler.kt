package com.smoh.showmeoutfit.data.scheduler

import java.time.LocalTime

/**
 * Platform boundary for the daily morning wake-up.
 *
 * Implementations must be idempotent: scheduling twice for the same profile replaces the pending
 * alarm instead of creating a second one.
 */
interface AlarmScheduler {

    /** Schedules the next occurrence of [time] (today if still ahead, otherwise tomorrow). */
    fun scheduleNext(time: LocalTime)

    /** Removes the pending alarm and any fallback work. */
    fun cancel()

    /**
     * False when Android 12+ exact-alarm access has not been granted, or was revoked at runtime.
     * Callers can still schedule: the implementation degrades to an inexact alarm plus periodic
     * fallback work.
     */
    val canScheduleExactAlarms: Boolean
}
