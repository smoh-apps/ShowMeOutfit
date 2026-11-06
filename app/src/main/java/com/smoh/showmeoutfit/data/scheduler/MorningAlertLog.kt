package com.smoh.showmeoutfit.data.scheduler

import java.time.LocalTime

/**
 * Structured event log for the morning alert chain.
 *
 * The abstraction is deliberately event-shaped instead of message-shaped: it can only carry
 * lifecycle facts (which alarm event happened, which [MorningDelivery] was produced, at what
 * time), so callers cannot accidentally log coordinates, profile fields or notification copy.
 *
 * Production uses [AndroidMorningAlertLog] (`android.util.Log`, tag [TAG]); pure JVM tests use
 * [NoOp] or a recording fake. Every method must be non-throwing: observability must never be able
 * to break delivery or scheduling.
 */
interface MorningAlertLog {

    /** The next occurrence of [time] was handed to the platform scheduler. */
    fun alarmScheduled(time: LocalTime)

    /** The OS broadcast for the morning alarm arrived. */
    fun alarmFired(source: MorningAlertSource)

    /** The worker started one run. */
    fun workerStarted()

    /** The worker completed a run with [delivery]. */
    fun workerFinished(delivery: MorningDelivery)

    /** The recommendation exists but the platform refused to show the notification. */
    fun notificationBlocked()

    /** The profile could not be read, so the alarm state could not be re-synced. */
    fun scheduleSyncFailed()

    companion object {
        /** Stable logcat tag; also used by `adb logcat -s MorningAlert` during QA. */
        const val TAG = "MorningAlert"

        /** Safe default for tests and for any graph that does not bind a real logger. */
        val NoOp: MorningAlertLog = object : MorningAlertLog {
            override fun alarmScheduled(time: LocalTime) = Unit
            override fun alarmFired(source: MorningAlertSource) = Unit
            override fun workerStarted() = Unit
            override fun workerFinished(delivery: MorningDelivery) = Unit
            override fun notificationBlocked() = Unit
            override fun scheduleSyncFailed() = Unit
        }
    }
}

/** Where the delivery run was started from. */
enum class MorningAlertSource {
    /** The exact (or inexact fallback) `AlarmManager` broadcast. */
    EXACT_ALARM,

    /** WorkManager's periodic safety net when exact-alarm access is unavailable. */
    FALLBACK_WORK,
}
