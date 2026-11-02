package com.smoh.showmeoutfit.data.scheduler

import com.smoh.showmeoutfit.domain.model.UserProfile
import com.smoh.showmeoutfit.domain.repository.UserRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalTime
import kotlin.coroutines.cancellation.CancellationException

/**
 * Single source of truth for whether a morning alarm should exist and when it fires.
 *
 * Every mutation path — app start, settings edits, boot, timezone change, worker completion —
 * funnels through [apply] so there is exactly one rule: alerts on and profile configured means
 * one pending alarm, anything else means none.
 */
class MorningScheduleCoordinator(
    private val userRepository: UserRepository,
    private val alarmScheduler: AlarmScheduler
) {

    /**
     * Observes the persisted profile for as long as [scope] lives and keeps the alarm in sync.
     *
     * The stream is distinct on the two fields that matter, so an unrelated edit (gender, age)
     * does not cancel and re-arm the alarm. Failures are ignored on purpose: a transient
     * DataStore read error must not crash the process, and the next emission re-syncs.
     */
    fun start(scope: CoroutineScope) {
        scope.launch {
            try {
                userRepository.getUserProfile()
                    .map { profile ->
                        AlarmPlan(
                            enabled = profile.isNotificationEnabled && profile.isConfigured,
                            time = profile.morningNotificationTime
                        )
                    }
                    .distinctUntilChanged()
                    .collectLatest { plan -> apply(plan) }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Throwable) {
                // Leave whatever alarm is already pending; the next successful read fixes it.
            }
        }
    }

    /** Reads the current profile once and applies it. Used by boot, timezone changes and workers. */
    suspend fun sync() {
        val profile = try {
            userRepository.getUserProfile().first()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Throwable) {
            return
        }
        apply(profile)
    }

    fun apply(profile: UserProfile) {
        apply(
            AlarmPlan(
                enabled = profile.isNotificationEnabled && profile.isConfigured,
                time = profile.morningNotificationTime
            )
        )
    }

    private fun apply(plan: AlarmPlan) {
        if (plan.enabled) {
            alarmScheduler.scheduleNext(plan.time)
        } else {
            alarmScheduler.cancel()
        }
    }

    private data class AlarmPlan(
        val enabled: Boolean,
        val time: LocalTime
    )
}
