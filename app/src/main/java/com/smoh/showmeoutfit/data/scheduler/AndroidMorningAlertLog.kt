package com.smoh.showmeoutfit.data.scheduler

import android.util.Log
import java.time.LocalTime

/**
 * `android.util.Log` implementation of [MorningAlertLog].
 *
 * Only event names, the delivery enum, the schedule time and the run source are written. This is
 * enough to reconstruct the daily chain from logcat (`adb logcat -s MorningAlert`) without ever
 * exposing the user's location, profile or the notification text.
 */
class AndroidMorningAlertLog : MorningAlertLog {

    override fun alarmScheduled(time: LocalTime) {
        Log.i(TAG, "event=alarm_scheduled at=$time")
    }

    override fun alarmFired(source: MorningAlertSource) {
        Log.i(TAG, "event=alarm_fired source=${source.name.lowercase()}")
    }

    override fun workerStarted() {
        Log.i(TAG, "event=worker_started")
    }

    override fun workerFinished(delivery: MorningDelivery) {
        Log.i(TAG, "event=worker_finished delivery=${delivery.label()}")
    }

    override fun notificationBlocked() {
        Log.w(TAG, "event=notification_blocked")
    }

    override fun scheduleSyncFailed() {
        Log.w(TAG, "event=schedule_sync_failed")
    }

    private fun MorningDelivery.label(): String = when (this) {
        MorningDelivery.Delivered -> "delivered"
        MorningDelivery.AlertsDisabled -> "alerts_disabled"
        MorningDelivery.RecommendationUnavailable -> "recommendation_unavailable"
        MorningDelivery.NotificationBlocked -> "notification_blocked"
    }

    private companion object {
        const val TAG = "MorningAlert"
    }
}
