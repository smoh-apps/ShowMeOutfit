package com.smoh.showmeoutfit.ui.permissions

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import com.smoh.showmeoutfit.ui.notifications.AndroidOutfitNotifier

/**
 * Reads the two OS gates that can silently swallow the morning alert.
 *
 * Notifications are checked at app level *and* at channel level: a user can leave the app's
 * notification toggle on and still block the "Morning outfit" channel, which looks identical from
 * the outside (nothing is posted) but needs a different fix. Exact alarms are only a concept from
 * API 31; older releases always schedule exactly, so they report `true`.
 */
class AndroidAlertsPermissionReader(
    private val context: Context
) : AlertsPermissionReader {

    override fun read(): AlertsPermissionState = AlertsPermissionState(
        notificationsAllowed = notificationsAllowed(),
        exactAlarmsAllowed = exactAlarmsAllowed()
    )

    private fun notificationsAllowed(): Boolean {
        val appLevel = NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!appLevel) return false

        // A channel that exists and was blocked by the user outlives the app-level toggle.
        val channel = context.getSystemService(NotificationManager::class.java)
            ?.getNotificationChannel(AndroidOutfitNotifier.CHANNEL_ID)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun exactAlarmsAllowed(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
    }
}

/** System screens the settings card can open, kept together so the intents are testable. */
object AlertsSettingsIntents {

    /** App notification settings (used when the runtime prompt is no longer available). */
    fun appNotificationSettings(context: Context): Intent =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /**
     * Android 12+ exact-alarm grant screen.
     *
     * Returns `null` below API 31, where the permission does not exist and the scheduler is exact
     * by default. Some OEM builds do not expose the screen at all, so callers must also handle an
     * `ActivityNotFoundException`.
     */
    fun exactAlarmSettings(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        return Intent(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            Uri.fromParts("package", context.packageName, null)
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
