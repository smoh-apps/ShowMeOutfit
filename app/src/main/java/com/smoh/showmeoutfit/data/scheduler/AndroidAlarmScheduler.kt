package com.smoh.showmeoutfit.data.scheduler

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.Clock
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * `AlarmManager` exact scheduling with a WorkManager fallback.
 *
 * Exact (`setExactAndAllowWhileIdle`) is the primary path: it survives Doze and delivers at the
 * requested minute. When Android 12+ exact-alarm access is missing, two things happen:
 * 1. an inexact `setAndAllowWhileIdle` alarm still targets the minute (the OS may batch it), and
 * 2. a unique 24-hour periodic worker guarantees the daily run even if the inexact alarm is
 *    dropped. The worker reschedules itself through [MorningScheduleCoordinator].
 */
class AndroidAlarmScheduler(
    private val context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) : AlarmScheduler {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override val canScheduleExactAlarms: Boolean
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() == true
        } else {
            true
        }

    override fun scheduleNext(time: LocalTime) {
        val manager = alarmManager ?: return
        val triggerAtMillis = MorningAlarmTime.nextTriggerMillis(clock.instant(), clock.zone, time)
        val delayMillis = (triggerAtMillis - clock.millis()).coerceAtLeast(1L)
        val operation = alarmIntent()

        val exact = if (canScheduleExactAlarms) {
            runCatching {
                manager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    operation
                )
            }.isSuccess
        } else {
            false
        }

        if (exact) {
            cancelFallbackWork()
        } else {
            // Permission can be revoked between the capability check and the call, so this path
            // is also the recovery for a SecurityException above.
            runCatching {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
            }
            enqueueFallbackWork(delayMillis)
        }
    }

    override fun cancel() {
        alarmManager?.cancel(alarmIntent())
        cancelFallbackWork()
    }

    private fun alarmIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, MorningAlarmReceiver::class.java)
            .setAction(MorningAlarmReceiver.ACTION_MORNING_ALARM),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun enqueueFallbackWork(delayMillis: Long) {
        val request = PeriodicWorkRequestBuilder<MorningOutfitWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .addTag(FALLBACK_TAG)
            .build()

        runCatching {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                FALLBACK_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }
    }

    private fun cancelFallbackWork() {
        runCatching {
            WorkManager.getInstance(context).cancelUniqueWork(FALLBACK_WORK_NAME)
        }
    }

    companion object {
        const val REQUEST_CODE = 7_001
        const val FALLBACK_WORK_NAME = "morning_outfit_fallback"
        const val FALLBACK_TAG = "morning_outfit"
    }
}
