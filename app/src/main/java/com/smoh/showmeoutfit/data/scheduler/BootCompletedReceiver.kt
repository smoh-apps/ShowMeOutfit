package com.smoh.showmeoutfit.data.scheduler

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.coroutines.cancellation.CancellationException

/**
 * Re-arms the daily alarm after the process has no memory of it.
 *
 * Alarms are dropped on reboot, on app update, and become wrong when the wall clock or the
 * timezone changes. Android 12+ also revokes exact-alarm access at runtime, which fires the
 * `SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` broadcast when it is granted again. All of them
 * land here and re-read the profile through [MorningScheduleCoordinator], which is idempotent.
 */
class BootCompletedReceiver : BroadcastReceiver(), KoinComponent {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in HANDLED_ACTIONS) return

        // A DataStore read is a disk round trip; goAsync keeps the process alive for it without
        // blocking the main thread for the duration of the broadcast.
        val pendingResult = goAsync()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope.launch {
            try {
                get<MorningScheduleCoordinator>().sync()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Nothing to recover: the next app start re-syncs from the same profile.
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        // The literal is inlined at compile time, so it is safe on older API levels even though the
        // constant itself is only declared from API 31.
        @SuppressLint("InlinedApi")
        private val HANDLED_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        )
    }
}
