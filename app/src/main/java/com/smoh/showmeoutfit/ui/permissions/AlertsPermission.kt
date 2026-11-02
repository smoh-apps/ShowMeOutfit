package com.smoh.showmeoutfit.ui.permissions

/**
 * OS-level capability the morning alert depends on but cannot grant itself.
 *
 * Kept as data so the decision "what should we ask for?" is testable on a plain JVM and the
 * screen never has to branch on `Build.VERSION` or on `NotificationManagerCompat`.
 */
enum class AlertsRequirement {
    /** Android 13+ `POST_NOTIFICATIONS`, or the app-level notification switch on older versions. */
    NOTIFICATIONS,

    /** Android 12+ `SCHEDULE_EXACT_ALARM`; without it the alarm is inexact and may drift. */
    EXACT_ALARMS
}

/**
 * Snapshot of what the user has allowed for alerts.
 *
 * Defaults are optimistic: the values only mean anything once a real reader produced them, and
 * letting the previews and Compose tests omit the parameter keeps the screen signature small.
 */
data class AlertsPermissionState(
    val notificationsAllowed: Boolean = true,
    val exactAlarmsAllowed: Boolean = true
) {

    val isSatisfied: Boolean
        get() = notificationsAllowed && exactAlarmsAllowed

    /**
     * The first thing worth asking for, notifications before exact alarms.
     *
     * A missing notification permission hides the alert completely, while an inexact alarm only
     * moves it by minutes, so fixing notifications first has the bigger effect.
     */
    val firstMissing: AlertsRequirement?
        get() = when {
            !notificationsAllowed -> AlertsRequirement.NOTIFICATIONS
            !exactAlarmsAllowed -> AlertsRequirement.EXACT_ALARMS
            else -> null
        }
}

/** Reads [AlertsPermissionState] from the platform. Implementations may hit system services. */
fun interface AlertsPermissionReader {
    fun read(): AlertsPermissionState
}
