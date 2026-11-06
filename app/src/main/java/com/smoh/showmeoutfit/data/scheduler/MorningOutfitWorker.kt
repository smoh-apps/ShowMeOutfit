package com.smoh.showmeoutfit.data.scheduler

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.coroutines.cancellation.CancellationException

/**
 * Executes the morning routine and keeps the daily chain alive.
 *
 * Re-arming happens before and independently of delivery: a device that was asleep, killed the
 * process, or ran the periodic fallback instead of the exact alarm must still end up with exactly
 * one pending alarm for tomorrow.
 */
class MorningOutfitWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params), KoinComponent {

    /**
     * Resolved per run and tolerant of a graph without a binding: observability must never turn a
     * delivery attempt into a failed work request.
     */
    private val log: MorningAlertLog
        get() = runCatching { get<MorningAlertLog>() }.getOrDefault(MorningAlertLog.NoOp)

    override suspend fun doWork(): Result {
        val routine = get<MorningOutfitRoutine>()
        val coordinator = get<MorningScheduleCoordinator>()
        log.workerStarted()

        // Tomorrow first: if delivery is cancelled or crashes, the daily schedule continues.
        try {
            coordinator.sync()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            // The alarm that fired is gone; WorkManager's retry still gets another chance.
            log.scheduleSyncFailed()
        }

        val delivery = try {
            routine.deliver()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            MorningDelivery.RecommendationUnavailable
        }
        log.workerFinished(delivery)

        // Retrying is only useful when the data might appear later. A blocked notification or a
        // disabled alert cannot change within the retry window.
        return when (delivery) {
            MorningDelivery.RecommendationUnavailable -> Result.retry()
            MorningDelivery.Delivered,
            MorningDelivery.AlertsDisabled,
            MorningDelivery.NotificationBlocked -> Result.success()
        }
    }

    companion object {
        const val WORK_TAG = "morning_outfit"
    }
}
