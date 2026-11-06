package com.smoh.showmeoutfit.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smoh.showmeoutfit.data.scheduler.AndroidAlarmScheduler
import com.smoh.showmeoutfit.data.scheduler.MorningAlarmReceiver
import java.time.LocalTime

/**
 * Debug-only ADB harness for the Sprint 4 Doze soak (SMO-S4-02).
 *
 * The production [MorningAlarmReceiver] is deliberately not exported, so an overnight soak cannot
 * arm or fire it from `adb shell`. This receiver exists only in the `debug` source set (it is not
 * part of a release build) and is gated by `android.permission.DUMP`, which the shell UID holds but
 * ordinary apps do not.
 *
 * Usage:
 * ```
 * adb shell am broadcast -a com.smoh.showmeoutfit.soak.SCHEDULE --ei seconds 120 \
 *   -n com.smoh.showmeoutfit/.debug.SoakAlarmReceiver
 * ```
 */
class SoakAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val scheduler = AndroidAlarmScheduler(context)
        when (intent.action) {
            ACTION_SCHEDULE -> {
                val seconds = intent.getIntExtra(EXTRA_SECONDS, DEFAULT_SECONDS)
                    .coerceIn(MIN_SECONDS, MAX_SECONDS)
                scheduler.scheduleNext(LocalTime.now().plusSeconds(seconds.toLong()))
            }

            ACTION_CANCEL -> scheduler.cancel()

            ACTION_FIRE -> {
                context.sendBroadcast(
                    Intent(context, MorningAlarmReceiver::class.java)
                        .setAction(MorningAlarmReceiver.ACTION_MORNING_ALARM)
                )
            }
        }
    }

    companion object {
        const val ACTION_SCHEDULE = "com.smoh.showmeoutfit.soak.SCHEDULE"
        const val ACTION_CANCEL = "com.smoh.showmeoutfit.soak.CANCEL"
        const val ACTION_FIRE = "com.smoh.showmeoutfit.soak.FIRE"
        const val EXTRA_SECONDS = "seconds"

        private const val DEFAULT_SECONDS = 120
        private const val MIN_SECONDS = 60
        private const val MAX_SECONDS = 3_600
    }
}
