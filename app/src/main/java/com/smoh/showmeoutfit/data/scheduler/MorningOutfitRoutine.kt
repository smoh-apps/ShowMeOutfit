package com.smoh.showmeoutfit.data.scheduler

import com.smoh.showmeoutfit.domain.model.TodayOutfit
import com.smoh.showmeoutfit.domain.notification.OutfitNotifier
import com.smoh.showmeoutfit.domain.repository.UserRepository
import com.smoh.showmeoutfit.domain.usecase.GetTodayOutfitRecommendationUseCase
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

/** What the morning run achieved. */
sealed interface MorningDelivery {

    /** The alert was posted and the OS accepted it. */
    data object Delivered : MorningDelivery

    /** Alerts are off, or the profile was never configured. Nothing should be shown. */
    data object AlertsDisabled : MorningDelivery

    /** Weather/recommendation could not be produced (offline with no usable cache, no location). */
    data object RecommendationUnavailable : MorningDelivery

    /** The recommendation exists but the OS refused to show it (permission or blocked channel). */
    data object NotificationBlocked : MorningDelivery
}

/**
 * The single "what does 07:00 mean" routine, independent of `AlarmManager`, `WorkManager` and the
 * notification system.
 *
 * Both the alarm-triggered worker and the periodic fallback worker call exactly this, so the two
 * delivery paths cannot drift. It re-reads the profile because the alarm was armed hours earlier
 * and the user may have switched alerts off in the meantime.
 */
class MorningOutfitRoutine(
    private val getTodayOutfit: GetTodayOutfitRecommendationUseCase,
    private val userRepository: UserRepository,
    private val notifier: OutfitNotifier,
    private val log: MorningAlertLog = MorningAlertLog.NoOp
) {

    suspend fun deliver(): MorningDelivery {
        val profile = try {
            userRepository.getUserProfile().first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return MorningDelivery.RecommendationUnavailable
        }

        if (!profile.isNotificationEnabled || !profile.isConfigured) {
            return MorningDelivery.AlertsDisabled
        }

        val outfit: TodayOutfit = try {
            getTodayOutfit(forceRefresh = true).getOrNull()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            null
        } ?: return MorningDelivery.RecommendationUnavailable

        return if (notifier.notifyMorningOutfit(outfit)) {
            MorningDelivery.Delivered
        } else {
            log.notificationBlocked()
            MorningDelivery.NotificationBlocked
        }
    }
}
