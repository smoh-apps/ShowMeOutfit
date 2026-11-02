package com.smoh.showmeoutfit.domain.notification

import com.smoh.showmeoutfit.domain.model.TodayOutfit

/**
 * Output port for the morning outfit alert.
 *
 * The domain decides *what* to announce; the platform implementation owns channels, permissions
 * and system UI. Keeping this an interface means the delivery routine and the worker stay
 * JVM-testable and the domain never imports `android.*`.
 */
interface OutfitNotifier {

    /**
     * Posts the morning alert for [outfit].
     *
     * @return `false` when the platform refused to show it (notifications disabled by the user,
     * a blocked channel, or a missing runtime permission). Callers must treat that as "not
     * delivered" rather than as success, so a future retry still has a chance to run.
     */
    suspend fun notifyMorningOutfit(outfit: TodayOutfit): Boolean
}
