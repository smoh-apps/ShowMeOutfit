package com.smoh.showmeoutfit.data.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager

/**
 * Receives the exact morning alarm and hands the real work to WorkManager.
 *
 * `onReceive` runs on the main thread with a hard budget, so it does nothing but enqueue an
 * expedited one-time request. The worker is responsible for the recommendation, the notification
 * and arming tomorrow's alarm, so a slow network never lives inside a broadcast receiver.
 */
class MorningAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_MORNING_ALARM) return

        runCatching {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                // REPLACE keeps a stale retry from the previous day from competing with today's run.
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<MorningOutfitWorker>()
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .addTag(MorningOutfitWorker.WORK_TAG)
                    .build()
            )
        }
    }

    companion object {
        const val ACTION_MORNING_ALARM = "com.smoh.showmeoutfit.action.MORNING_ALARM"
        const val UNIQUE_WORK_NAME = "morning_outfit_run"
    }
}
