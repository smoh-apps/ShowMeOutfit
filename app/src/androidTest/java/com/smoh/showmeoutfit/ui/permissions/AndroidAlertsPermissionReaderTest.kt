package com.smoh.showmeoutfit.ui.permissions

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.smoh.showmeoutfit.ui.notifications.AndroidOutfitNotifier
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reader is the single source of truth for both the settings notice and the notification
 * guard, so it is checked against the platform services instead of against a mock.
 */
@RunWith(AndroidJUnit4::class)
class AndroidAlertsPermissionReaderTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val reader = AndroidAlertsPermissionReader(context)

    @Before
    fun grantNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        notificationManager.deleteNotificationChannel(AndroidOutfitNotifier.CHANNEL_ID)
    }

    @After
    fun cleanUp() {
        notificationManager.deleteNotificationChannel(AndroidOutfitNotifier.CHANNEL_ID)
    }

    @Test
    fun a_granted_permission_counts_as_allowed() {
        assertTrue(NotificationManagerCompat.from(context).areNotificationsEnabled())

        assertTrue(reader.read().notificationsAllowed)
    }

    @Test
    fun a_blocked_channel_counts_as_not_allowed_even_with_the_permission_granted() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                AndroidOutfitNotifier.CHANNEL_ID,
                "blocked for test",
                NotificationManager.IMPORTANCE_NONE
            )
        )

        assertFalse(reader.read().notificationsAllowed)
    }

    @Test
    fun exact_alarm_state_matches_the_alarm_manager() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return

        val platformAllows = context.getSystemService(AlarmManager::class.java)
            .canScheduleExactAlarms()

        assertEquals(platformAllows, reader.read().exactAlarmsAllowed)
    }

    @Test
    fun the_notification_settings_intent_targets_this_package() {
        val intent = AlertsSettingsIntents.appNotificationSettings(context)

        assertEquals(Settings.ACTION_APP_NOTIFICATION_SETTINGS, intent.action)
        assertEquals(context.packageName, intent.getStringExtra(Settings.EXTRA_APP_PACKAGE))
    }

    @Test
    fun the_exact_alarm_intent_exists_only_where_the_permission_does() {
        val intent = AlertsSettingsIntents.exactAlarmSettings(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            assertNotNull(intent)
            assertEquals(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, intent?.action)
            assertEquals("package", intent?.data?.scheme)
            assertEquals(context.packageName, intent?.data?.schemeSpecificPart)
        } else {
            assertNull(intent)
        }
    }
}
